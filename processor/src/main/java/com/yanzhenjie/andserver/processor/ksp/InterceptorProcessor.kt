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

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.yanzhenjie.andserver.annotation.AppInfo
import com.yanzhenjie.andserver.annotation.Interceptor
import com.yanzhenjie.andserver.processor.util.Constants
import kotlin.reflect.KClass

/**
 * KSP version of [com.yanzhenjie.andserver.processor.InterceptorProcessor].
 */
class InterceptorProcessor(
    environment: SymbolProcessorEnvironment
) : BaseProcessor(environment) {

    override val logger = environment.logger
    override val codeGenerator = environment.codeGenerator

    override fun addAnnotation(set: MutableSet<KClass<out Annotation>>) {
        set.add(Interceptor::class)
        set.add(AppInfo::class)
    }

    override fun processAnnotations(
        annotation: KClass<out Annotation>,
        classes: List<KSClassDeclaration>,
        resolver: Resolver
    ) {
        if (annotation != Interceptor::class) return

        val appClasses = resolver
            .getSymbolsWithAnnotation(AppInfo::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        if (appClasses.isEmpty()) return

        val registerPackageName = getRegisterPackageName(appClasses)
        val interceptorClasses = classes.filter { it.annotations.any { it.shortName.asString() == "Interceptor" } }
        val interceptorMap = findAnnotation(interceptorClasses)
        if (interceptorMap.isNotEmpty()) {
            createRegister(registerPackageName, interceptorMap)
        }
    }

    private fun findAnnotation(classes: List<KSClassDeclaration>): Map<String, List<KSClassDeclaration>> {
        val interceptorMap = mutableMapOf<String, MutableList<KSClassDeclaration>>()
        for (declaration in classes) {
            if (!declaration.modifiers.contains(Modifier.PUBLIC)) {
                logger.error("The modifier public is missing on ${declaration.qualifiedName?.asString()}")
                continue
            }
            val interfaces = declaration.superTypes.toList()
            if (interfaces.isEmpty()) {
                logger.warn("The annotation Interceptor must be used in a subclass of [HandlerInterceptor] on ${declaration.qualifiedName?.asString()}")
                continue
            }
            for (typeRef in interfaces) {
                val resolved = typeRef.resolve()
                val name = resolved.declaration.qualifiedName?.asString()
                if (name == Constants.INTERCEPTOR_TYPE) {
                    val group = getGroup(declaration)
                    interceptorMap.getOrPut(group) { mutableListOf() }.add(declaration)
                    break
                } else {
                    logger.warn("The annotation Interceptor must be used in a subclass of [HandlerInterceptor] on ${declaration.qualifiedName?.asString()}")
                }
            }
        }
        return interceptorMap
    }

    private fun createRegister(
        registerPackageName: String,
        interceptorMap: Map<String, List<KSClassDeclaration>>
    ) {
        val interceptorType = ClassName.bestGuess(Constants.INTERCEPTOR_TYPE)
        val listType = MUTABLE_LIST.parameterizedBy(interceptorType)
        val mapType = MAP.parameterizedBy(STRING, listType)

        val mapField = PropertySpec.builder("mMap", mapType, KModifier.PRIVATE).build()

        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addStatement("this.mMap = hashMapOf()")

        for ((group, interceptorList) in interceptorMap) {
            val listName = "${group}List"
            constructorBuilder.addStatement(
                "val %L = mutableListOf<%T>()", listName, interceptorType
            )
            for (type in interceptorList) {
                val className = type.toClassName()
                logger.info("------ Processing ${type.simpleName.asString()} ------")
                constructorBuilder.addStatement("%L.add(%T())", listName, className)
            }
            constructorBuilder.addStatement("this.mMap.put(%S, %L)", group, listName)
        }

        val registerFun = FunSpec.builder("onRegister")
            .addModifiers(KModifier.PUBLIC)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", ClassName.bestGuess(Constants.CONTEXT_TYPE))
            .addParameter("group", STRING)
            .addParameter("register", ClassName.bestGuess(Constants.REGISTER_TYPE))
            .addStatement("var list: MutableList<%T>? = mMap.get(group)", interceptorType)
            .beginControlFlow("if(list == null)")
            .addStatement("list = mutableListOf()")
            .endControlFlow()
            .addStatement("val defaultList: MutableList<%T>? = mMap.get(%S)", interceptorType, "default")
            .beginControlFlow("if(defaultList != null && defaultList.isNotEmpty())")
            .addStatement("list.addAll(defaultList)")
            .endControlFlow()
            .beginControlFlow("if(list.isNotEmpty())")
            .beginControlFlow("for (interceptor in list)")
            .addStatement("register.addInterceptor(interceptor)")
            .endControlFlow()
            .endControlFlow()
            .build()

        val typeSpec = TypeSpec.classBuilder("InterceptorRegister")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(ClassName.bestGuess(Constants.ON_REGISTER_TYPE))
            .addProperty(mapField)
            .primaryConstructor(constructorBuilder.build())
            .addFunction(registerFun)
            .build()

        FileSpec.builder(registerPackageName, "InterceptorRegister")
            .addType(typeSpec)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun getGroup(type: KSClassDeclaration): String {
        val annotation = type.annotations.firstOrNull { it.shortName.asString() == "Interceptor" }
        val value = annotation?.arguments?.firstOrNull()?.value as? String
        if (value != null) return value
        throw IllegalStateException("The type is not a Interceptor: ${type.qualifiedName?.asString()}")
    }
}

class InterceptorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return InterceptorProcessor(environment)
    }
}
