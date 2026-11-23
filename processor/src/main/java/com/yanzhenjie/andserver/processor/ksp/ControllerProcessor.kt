/*
 * Copyright 2018 Zhenjie Yan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yanzhenjie.andserver.processor.ksp

import com.google.devtools.ksp.processing.Resolver as KSPResolver
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.yanzhenjie.andserver.annotation.*
import com.yanzhenjie.andserver.processor.util.Constants
import com.yanzhenjie.andserver.processor.util.Patterns
import kotlin.collections.emptyList
import kotlin.reflect.KClass

/**
 * KSP version of [com.yanzhenjie.andserver.processor.ControllerProcessor].
 */
class ControllerProcessor(
    environment: SymbolProcessorEnvironment
) : BaseProcessor(environment), Patterns {

    override val logger = environment.logger
    override val codeGenerator = environment.codeGenerator

    // Resolves a base handler class name to a unique name.
    // Uses a counter suffix only when the base name is already taken.
    private fun resolveHandlerName(base: String): String {
        var name = base
        var i = 0
        while (name in generatedHandlerClassNames) {
            i++
            name = "${base}$i"
        }
        generatedHandlerClassNames.add(name)
        return name
    }

    // All handler class names already written in this run.
    private val generatedHandlerClassNames = mutableSetOf<String>()
    private val processedControllers = mutableSetOf<String>()
    private val adapterGroups = mutableMapOf<String, MutableList<String>>()

    // Type names used throughout generation
    private val mappingType = ClassName.bestGuess(Constants.MAPPING_TYPE)
    private val handlerType = ClassName.bestGuess(Constants.HANDLER_TYPE)
    private val mappingAdapterType = ClassName.bestGuess(Constants.MAPPING_ADAPTER_TYPE)
    private val mappingHandlerType = ClassName.bestGuess(Constants.MAPPING_HANDLER_TYPE)
    private val additionType = ClassName.bestGuess(Constants.ADDITION_TYPE)
    private val crossOriginType = ClassName.bestGuess(Constants.CROSS_ORIGIN_TYPE)
    private val viewObjectType = ClassName.bestGuess(Constants.VIEW_TYPE_OBJECT)
    private val requestType = ClassName.bestGuess(Constants.REQUEST_TYPE)
    private val responseType = ClassName.bestGuess(Constants.RESPONSE_TYPE)
    private val contextType = ClassName.bestGuess(Constants.CONTEXT_TYPE)
    private val registerType = ClassName.bestGuess(Constants.REGISTER_TYPE)
    private val onRegisterType = ClassName.bestGuess(Constants.ON_REGISTER_TYPE)
    private val adapterType = ClassName.bestGuess(Constants.ADAPTER_TYPE)
    
    // Mapping helper types
    private val pathType = ClassName.bestGuess("${Constants.PACKAGE_NAME}.framework.mapping.Path")
    private val methodType = ClassName.bestGuess("${Constants.PACKAGE_NAME}.framework.mapping.Method")
    private val pairType = ClassName.bestGuess("${Constants.PACKAGE_NAME}.framework.mapping.Pair")
    private val mimeTypes = ClassName.bestGuess("${Constants.PACKAGE_NAME}.framework.mapping.Mime")

    private val mappingToHandlerMap = MAP.parameterizedBy(mappingType, handlerType)

    override fun addAnnotation(set: MutableSet<KClass<out Annotation>>) {
        set.add(RequestMapping::class)
        set.add(GetMapping::class)
        set.add(PostMapping::class)
        set.add(PutMapping::class)
        set.add(PatchMapping::class)
        set.add(DeleteMapping::class)
        set.add(AppInfo::class)
    }

    // No-op: all collection and processing happens in process() to match JAP behavior.
    // The per-annotation invocation here would add controllers to processedControllers
    // prematurely, skipping later annotation types that contribute additional HTTP methods.
    override fun processAnnotations(
        annotation: KClass<out Annotation>,
        classes: List<KSClassDeclaration>,
        resolver: KSPResolver
    ) {}

    override fun process(resolver: KSPResolver): List<KSAnnotated> {
        val mappingAnnoNames = listOf(
            RequestMapping::class.qualifiedName!!,
            GetMapping::class.qualifiedName!!,
            PostMapping::class.qualifiedName!!,
            PutMapping::class.qualifiedName!!,
            PatchMapping::class.qualifiedName!!,
            DeleteMapping::class.qualifiedName!!
        )

        // Phase 1: collect every mapping method per controller class (matches JAP's two-pass design).
        // Store parent class directly to avoid redundant getClassDeclarationByName() re-resolution in KSP2.
        val controllerMethods = mutableMapOf<String, Pair<KSClassDeclaration, MutableList<KSFunctionDeclaration>>>()
        for (annoName in mappingAnnoNames) {
            for (symbol in resolver.getSymbolsWithAnnotation(annoName)) {
                if (symbol !is KSFunctionDeclaration) continue
                val parent = symbol.parentDeclaration as? KSClassDeclaration ?: continue
                val typeKey = parent.qualifiedName?.asString() ?: continue
                controllerMethods.getOrPut(typeKey) { parent to mutableListOf() }.second.add(symbol)
            }
        }

        // Phase 2: process each controller exactly once with its complete method list.
        for ((typeKey, pair) in controllerMethods) {
            val (parent, executes) = pair
            if (typeKey in processedControllers) continue
            processedControllers.add(typeKey)

            val hasRestController = parent.annotations.any { it.shortName.asString() == "RestController" }
            val hasController = parent.annotations.any { it.shortName.asString() == "Controller" }
            if (!hasRestController && !hasController) {
                logger.warn("Controller/RestController annotations may be missing on ${parent.qualifiedName?.asString()}")
                continue
            }

            logger.info("------ Processing ${parent.simpleName.asString()} ------")
            val packageName = parent.packageName.asString()

            // Pre-resolve all handler class names deterministically so createHandlerClass
            // and createAdapterClass always agree on the same name for each method.
            val handlerClassNames = executes.map { execute ->
                val baseName = "${parent.simpleName.asString()}" +
                    execute.simpleName.asString().replaceFirstChar { it.uppercaseChar() } + "Handler"
                resolveHandlerName(baseName)
            }

            for ((idx, execute) in executes.withIndex()) {
                createHandlerClass(parent, execute, packageName, handlerClassNames[idx])
            }
            createAdapterClass(parent, executes, packageName, handlerClassNames)

            val group = getGroup(parent)
            adapterGroups.getOrPut(group) { mutableListOf() }
                .add("$packageName.${parent.simpleName.asString()}Adapter")
        }

        // Register adapters.
        if (adapterGroups.isNotEmpty()) {
            val appClasses = resolver
                .getSymbolsWithAnnotation(AppInfo::class.qualifiedName!!)
                .filterIsInstance<KSClassDeclaration>()
                .toList()
            if (appClasses.isNotEmpty()) {
                val registerPackageName = getRegisterPackageName(appClasses)
                createRegister(registerPackageName)
            }
        }

        return emptyList()
    }

    private fun createHandlerClass(
        type: KSClassDeclaration,
        function: KSFunctionDeclaration,
        packageName: String,
        className: String
    ) {

        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addParameter("host", Any::class)
            .addParameter("mapping", mappingType)
            .addParameter("addition", additionType)
            .addParameter("crossOrigin", crossOriginType.copy(nullable = true))

        val handleMethod = FunSpec.builder("onHandle")
            .addModifiers(KModifier.OVERRIDE)
            .returns(viewObjectType)
            .addParameter("request", requestType)
            .addParameter("response", responseType)
            .addStatement("// TODO: implement parameter binding and type conversion")
            .addStatement("return %T(true, null)", viewObjectType)
            .build()

        val handlerClass = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .superclass(mappingHandlerType)
            .primaryConstructor(constructorBuilder.build())
            .addSuperclassConstructorParameter("%L, %L, %L, %L", "host", "mapping", "addition", "crossOrigin")
            .addFunction(handleMethod)
            .build()

        FileSpec.builder(packageName, className)
            .addType(handlerClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun createAdapterClass(
        type: KSClassDeclaration,
        executes: List<KSFunctionDeclaration>,
        packageName: String,
        handlerClassNames: List<String>
    ) {
        val controllerClassName = type.toClassName()

        // mHost field - private, set in constructor
        val hostField = PropertySpec.builder("mHost", controllerClassName, KModifier.PRIVATE)
            .build()

        // mMappingMap field - Map<Mapping, RequestHandler>
        val mappingField = PropertySpec.builder("mMappingMap", mappingToHandlerMap, KModifier.PRIVATE)
            .build()

        // Constructor: initialize host and mapping map, then populate with handlers
        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addStatement("this.mHost = %T()", controllerClassName)
            .addStatement("this.mMappingMap = linkedMapOf()")

        for ((idx, execute) in executes.withIndex()) {
            // Use a stable variable per iteration so KotlinPoet's lazy evaluation
            // doesn't pick up a later value from the loop closure.
            val currentHandlerClassName = handlerClassNames[idx]
            val handlerVarName = "handler${idx}"
            val mappingVarName = "mapping${idx}"

            // Create Mapping instance
            constructorBuilder.addStatement("\nval $mappingVarName = %T()", mappingType)
            
            // Extract and set path — join all path parts into a single rule.
            // Multiple addRule() calls create independent rules that fail runtime matching
            // because each rule is matched against the full request path independently.
            val paths = extractPaths(type, execute)
            if (paths.isNotEmpty()) {
                constructorBuilder.addStatement("val path${idx} = %T()", pathType)
                constructorBuilder.addStatement("path${idx}.addRule(%S)", paths.joinToString(""))
                constructorBuilder.addStatement("$mappingVarName.setPath(path${idx})")
            }
            
            // Extract and set method
            val methods = extractMethods(type, execute)
            if (methods.isNotEmpty()) {
                constructorBuilder.addStatement("val method${idx} = %T()", methodType)
                for (method in methods) {
                    constructorBuilder.addStatement("method${idx}.addRule(%S)", method)
                }
                constructorBuilder.addStatement("$mappingVarName.setMethod(method${idx})")
            }
            
            // Extract and set params
            val params = extractParams(type, execute)
            if (params.isNotEmpty()) {
                constructorBuilder.addStatement("val param${idx} = %T()", pairType)
                for (param in params) {
                    constructorBuilder.addStatement("param${idx}.addRule(%S)", param)
                }
                constructorBuilder.addStatement("$mappingVarName.setParam(param${idx})")
            }
            
            // Extract and set headers
            val headers = extractHeaders(type, execute)
            if (headers.isNotEmpty()) {
                constructorBuilder.addStatement("val header${idx} = %T()", pairType)
                for (header in headers) {
                    constructorBuilder.addStatement("header${idx}.addRule(%S)", header)
                }
                constructorBuilder.addStatement("$mappingVarName.setHeader(header${idx})")
            }
            
            // Extract and set consumes
            val consumes = extractConsumes(type, execute)
            if (consumes.isNotEmpty()) {
                constructorBuilder.addStatement("val consume${idx} = %T()", mimeTypes)
                for (consume in consumes) {
                    constructorBuilder.addStatement("consume${idx}.addRule(%S)", consume)
                }
                constructorBuilder.addStatement("$mappingVarName.setConsume(consume${idx})")
            }
            
            // Extract and set produces
            val produces = extractProduces(type, execute)
            if (produces.isNotEmpty()) {
                constructorBuilder.addStatement("val produce${idx} = %T()", mimeTypes)
                for (produce in produces) {
                    constructorBuilder.addStatement("produce${idx}.addRule(%S)", produce)
                }
                constructorBuilder.addStatement("$mappingVarName.setProduce(produce${idx})")
            }

            // Create Addition instance
            val additionVarName = "addition${idx}"
            constructorBuilder.addStatement("val $additionVarName = %T()", additionType)
            
            // Create CrossOrigin instance
            val crossOriginVarName = "crossOrigin${idx}"
            constructorBuilder.addStatement("val $crossOriginVarName = %T()", crossOriginType)

            // Create the handler instance with unique variable name
            constructorBuilder.addStatement(
                "val $handlerVarName = %L(this.mHost, $mappingVarName, $additionVarName, $crossOriginVarName)",
                currentHandlerClassName
            )

            // Put mapping -> handler into the map
            constructorBuilder.addStatement("this.mMappingMap[$mappingVarName] = $handlerVarName")
        }

        // getMappingMap() override - returns the map
        val getMappingMapMethod = FunSpec.builder("getMappingMap")
            .addModifiers(KModifier.OVERRIDE)
            .returns(mappingToHandlerMap)
            .addStatement("return mMappingMap")
            .build()

        // getHost() override - returns the controller instance
        val getHostMethod = FunSpec.builder("getHost")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Any::class)
            .addStatement("return mHost")
            .build()

        val adapterClass = TypeSpec.classBuilder("${type.simpleName.asString()}Adapter")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .superclass(mappingAdapterType)
            .addProperty(hostField)
            .addProperty(mappingField)
            .primaryConstructor(constructorBuilder.build())
            .addFunction(getMappingMapMethod)
            .addFunction(getHostMethod)
            .build()

        FileSpec.builder(packageName, "${type.simpleName.asString()}Adapter")
            .addType(adapterClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun extractPaths(type: KSClassDeclaration, function: KSFunctionDeclaration): List<String> {
        val paths = mutableListOf<String>()
        val annotationTargets = listOf("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping")
        
        // Helper function to extract paths from a single annotation
        fun extractFromAnnotation(annotation: KSAnnotation): List<String> {
            val result = mutableListOf<String>()
            for (arg in annotation.arguments) {
                val argName = arg.name?.asString()
                if (argName in listOf("value", "path")) {
                    val value = arg.value
                    @Suppress("UNCHECKED_CAST")
                    val values = value as? List<*>
                    if (values != null && values.isNotEmpty()) {
                        result.addAll(values.filterIsInstance<String>())
                    } else if (value is String) {
                        // Single string value
                        result.add(value)
                    }
                }
            }
            return result
        }
        
        // Check type-level annotations
        for (annotation in type.annotations) {
            val annName = annotation.shortName.asString()
            if (annName in annotationTargets) {
                paths.addAll(extractFromAnnotation(annotation))
            }
        }
        
        // Check method-level annotations
        for (annotation in function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName in annotationTargets) {
                paths.addAll(extractFromAnnotation(annotation))
            }
        }
        
        return paths
    }

    private fun extractMethods(type: KSClassDeclaration, function: KSFunctionDeclaration): List<String> {
        val methods = mutableSetOf<String>()
        val methodMap = mapOf(
            "GetMapping" to listOf("GET"),
            "PostMapping" to listOf("POST"),
            "PutMapping" to listOf("PUT"),
            "PatchMapping" to listOf("PATCH"),
            "DeleteMapping" to listOf("DELETE")
        )
        
        // First check for specific mapping annotations (@GetMapping, @PostMapping, etc.)
        for (annotation in type.annotations + function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName in methodMap) {
                methods.addAll(methodMap[annName]!!)
                // If we found a specific mapping annotation, don't check RequestMapping
                return methods.toList()
            }
        }
        
        // Only check RequestMapping if no specific mapping annotation was found
        for (annotation in type.annotations + function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName == "RequestMapping") {
                val methodArg = annotation.arguments.firstOrNull { it.name?.asString() == "method" }
                if (methodArg != null) {
                    @Suppress("UNCHECKED_CAST")
                    val methodValues = methodArg.value as? List<Any?>
                    for (m in methodValues ?: emptyList()) {
                        // The value might be RequestMethod.GET, GET, "GET", etc.
                        val methodName = when (m) {
                            is String -> m.uppercase()
                            else -> {
                                // Try to extract from qualified name like RequestMethod.GET
                                val str = m.toString()
                                if (str.contains(".")) str.substringAfterLast(".") else str.uppercase()
                            }
                        }
                        if (methodName in listOf("GET", "POST", "PUT", "PATCH", "DELETE")) {
                            methods.add(methodName)
                        }
                    }
                }
            }
        }
        
        return methods.toList()
    }

    private fun extractParams(type: KSClassDeclaration, function: KSFunctionDeclaration): List<String> {
        val params = mutableListOf<String>()
        for (annotation in type.annotations + function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName == "RequestMapping") {
                for (arg in annotation.arguments) {
                    if (arg.name?.asString() == "params") {
                        @Suppress("UNCHECKED_CAST")
                        val value = arg.value as? List<*>
                        if (value != null && value.isNotEmpty()) {
                            params.addAll(value.filterIsInstance<String>())
                        } else if (arg.value is String) {
                            params.add(arg.value as String)
                        }
                    }
                }
            }
        }
        return params
    }

    private fun extractHeaders(type: KSClassDeclaration, function: KSFunctionDeclaration): List<String> {
        val headers = mutableListOf<String>()
        for (annotation in type.annotations + function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName == "RequestMapping") {
                for (arg in annotation.arguments) {
                    if (arg.name?.asString() == "headers") {
                        @Suppress("UNCHECKED_CAST")
                        val value = arg.value as? List<*>
                        if (value != null && value.isNotEmpty()) {
                            headers.addAll(value.filterIsInstance<String>())
                        } else if (arg.value is String) {
                            headers.add(arg.value as String)
                        }
                    }
                }
            }
        }
        return headers
    }

    private fun extractConsumes(type: KSClassDeclaration, function: KSFunctionDeclaration): List<String> {
        val consumes = mutableListOf<String>()
        for (annotation in type.annotations + function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName in listOf("RequestMapping", "PostMapping", "PutMapping", "PatchMapping")) {
                for (arg in annotation.arguments) {
                    if (arg.name?.asString() == "consumes") {
                        @Suppress("UNCHECKED_CAST")
                        val value = arg.value as? List<*>
                        if (value != null && value.isNotEmpty()) {
                            consumes.addAll(value.filterIsInstance<String>())
                        } else if (arg.value is String) {
                            consumes.add(arg.value as String)
                        }
                    }
                }
            }
        }
        return consumes
    }

    private fun extractProduces(type: KSClassDeclaration, function: KSFunctionDeclaration): List<String> {
        val produces = mutableListOf<String>()
        for (annotation in type.annotations + function.annotations) {
            val annName = annotation.shortName.asString()
            if (annName in listOf("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping")) {
                for (arg in annotation.arguments) {
                    if (arg.name?.asString() == "produces") {
                        @Suppress("UNCHECKED_CAST")
                        val value = arg.value as? List<*>
                        if (value != null && value.isNotEmpty()) {
                            produces.addAll(value.filterIsInstance<String>())
                        } else if (arg.value is String) {
                            produces.add(arg.value as String)
                        }
                    }
                }
            }
        }
        return produces
    }

    private fun createRegister(
        registerPackageName: String
    ) {
        val mapType = MAP.parameterizedBy(STRING, MUTABLE_LIST.parameterizedBy(adapterType))

        val mapField = PropertySpec.builder("mMap", mapType, KModifier.PRIVATE).build()

        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addStatement("this.mMap = hashMapOf()")

        for ((group, adapterList) in adapterGroups) {
            val listName = "${group}List"
            constructorBuilder.addStatement(
                "val %L = mutableListOf<%T>()", listName, adapterType
            )
            for (adapterName in adapterList) {
                constructorBuilder.addStatement("%L.add(%T())", listName, ClassName.bestGuess(adapterName))
            }
            constructorBuilder.addStatement("this.mMap.put(%S, %L)", group, listName)
        }

        val registerFun = FunSpec.builder("onRegister")
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter("context", contextType)
            .addParameter("group", STRING)
            .addParameter("register", registerType)
            .addStatement("var list: MutableList<%T>? = mMap.get(group)", adapterType)
            .beginControlFlow("if(list == null)")
            .addStatement("list = mutableListOf()")
            .endControlFlow()
            .addStatement("val defaultList: MutableList<%T>? = mMap.get(%S)", adapterType, "default")
            .beginControlFlow("if(defaultList != null && !defaultList.isEmpty())")
            .addStatement("list.addAll(defaultList)")
            .endControlFlow()
            .beginControlFlow("if(list != null && !list.isEmpty())")
            .beginControlFlow("for (adapter in list)")
            .addStatement("register.addAdapter(adapter)")
            .endControlFlow()
            .endControlFlow()
            .build()

        val registerClass = TypeSpec.classBuilder("AdapterRegister")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(onRegisterType)
            .addProperty(mapField)
            .primaryConstructor(constructorBuilder.build())
            .addFunction(registerFun)
            .build()

        FileSpec.builder(registerPackageName, "AdapterRegister")
            .addType(registerClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun getGroup(type: KSClassDeclaration): String {
        for (annotation in type.annotations) {
            val name = annotation.shortName.asString()
            if (name == "Controller" || name == "RestController") {
                val value = annotation.arguments.firstOrNull()?.value as? String
                if (!value.isNullOrEmpty()) return value
            }
        }
        return "default"
    }
}

class ControllerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ControllerProcessor(environment)
    }
}
