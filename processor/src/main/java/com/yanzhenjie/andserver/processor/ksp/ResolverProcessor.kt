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
import com.yanzhenjie.andserver.annotation.Resolver as ResolverAnnotation
import com.yanzhenjie.andserver.processor.util.Constants
import kotlin.reflect.KClass

/**
 * KSP version of [com.yanzhenjie.andserver.processor.ResolverProcessor].
 */
class ResolverProcessor(
    environment: SymbolProcessorEnvironment
) : BaseProcessor(environment) {

    override val logger = environment.logger
    override val codeGenerator = environment.codeGenerator

    override fun addAnnotation(set: MutableSet<KClass<out Annotation>>) {
        set.add(ResolverAnnotation::class)
        set.add(AppInfo::class)
    }

    override fun processAnnotations(
        annotation: KClass<out Annotation>,
        classes: List<KSClassDeclaration>,
        resolver: Resolver
    ) {
        if (annotation != ResolverAnnotation::class) return

        val appClasses = resolver
            .getSymbolsWithAnnotation(AppInfo::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        if (appClasses.isEmpty()) return

        val registerPackageName = getRegisterPackageName(appClasses)
        val resolverClasses = classes.filter { it.annotations.any { it.shortName.asString() == "Resolver" } }
        val resolverMap = findAnnotation(resolverClasses)
        if (resolverMap.isNotEmpty()) {
            createRegister(registerPackageName, resolverMap)
        }
    }

    private fun findAnnotation(classes: List<KSClassDeclaration>): Map<String, KSClassDeclaration> {
        val resolverMap = mutableMapOf<String, KSClassDeclaration>()
        for (declaration in classes) {
            if (!declaration.modifiers.contains(Modifier.PUBLIC)) {
                logger.error("The modifier public is missing on ${declaration.qualifiedName?.asString()}")
                continue
            }
            val interfaces = declaration.superTypes.toList()
            if (interfaces.isEmpty()) {
                logger.warn("The annotation Resolver must be used in a subclass of [ExceptionResolver] on ${declaration.qualifiedName?.asString()}")
                continue
            }
            for (typeRef in interfaces) {
                val resolved = typeRef.resolve()
                val name = resolved.declaration.qualifiedName?.asString()
                if (name == Constants.RESOLVER_TYPE) {
                    resolverMap[getGroup(declaration)] = declaration
                    break
                } else {
                    logger.warn("The annotation Resolver must be used in a subclass of [ExceptionResolver] on ${declaration.qualifiedName?.asString()}")
                }
            }
        }
        return resolverMap
    }

    private fun createRegister(
        registerPackageName: String,
        resolverMap: Map<String, KSClassDeclaration>
    ) {
        val resolverType = ClassName.bestGuess(Constants.RESOLVER_TYPE)
        val mapType = MAP.parameterizedBy(STRING, resolverType)

        val mapField = PropertySpec.builder("mMap", mapType, KModifier.PRIVATE).build()

        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addStatement("this.mMap = hashMapOf()")

        for ((group, resolverClass) in resolverMap) {
            val className = resolverClass.toClassName()
            logger.info("------ Processing ${resolverClass.simpleName.asString()} ------")
            constructorBuilder.addStatement("this.mMap.put(%S, %T())", group, className)
        }

        val registerFun = FunSpec.builder("onRegister")
            .addModifiers(KModifier.PUBLIC)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", ClassName.bestGuess(Constants.CONTEXT_TYPE))
            .addParameter("group", STRING)
            .addParameter("register", ClassName.bestGuess(Constants.REGISTER_TYPE))
            .addStatement("var resolver: %T? = mMap.get(group)", resolverType)
            .beginControlFlow("if(resolver == null)")
            .addStatement("resolver = mMap.get(%S)", "default")
            .endControlFlow()
            .beginControlFlow("if(resolver != null)")
            .addStatement("register.setResolver(resolver)")
            .endControlFlow()
            .build()

        val registerClass = TypeSpec.classBuilder("ResolverRegister")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(ClassName.bestGuess(Constants.ON_REGISTER_TYPE))
            .addProperty(mapField)
            .primaryConstructor(constructorBuilder.build())
            .addFunction(registerFun)
            .build()

        FileSpec.builder(registerPackageName, "ResolverRegister")
            .addType(registerClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun getGroup(type: KSClassDeclaration): String {
        val annotation = type.annotations.firstOrNull { it.shortName.asString() == "Resolver" }
        val value = annotation?.arguments?.firstOrNull()?.value as? String
        if (value != null) return value
        throw IllegalStateException("The type is not a Resolver: ${type.qualifiedName?.asString()}")
    }
}

class ResolverProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ResolverProcessor(environment)
    }
}
