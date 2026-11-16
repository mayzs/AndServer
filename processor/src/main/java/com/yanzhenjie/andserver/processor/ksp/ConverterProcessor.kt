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
import com.yanzhenjie.andserver.annotation.Converter
import com.yanzhenjie.andserver.processor.util.Constants
import kotlin.reflect.KClass

/**
 * KSP version of [com.yanzhenjie.andserver.processor.ConverterProcessor].
 */
class ConverterProcessor(
    environment: SymbolProcessorEnvironment
) : BaseProcessor(environment) {

    override val logger = environment.logger
    override val codeGenerator = environment.codeGenerator

    override fun addAnnotation(set: MutableSet<KClass<out Annotation>>) {
        set.add(Converter::class)
        set.add(AppInfo::class)
    }

    override fun processAnnotations(
        annotation: KClass<out Annotation>,
        classes: List<KSClassDeclaration>,
        resolver: Resolver
    ) {
        if (annotation != Converter::class) return

        val appClasses = resolver
            .getSymbolsWithAnnotation(AppInfo::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        if (appClasses.isEmpty()) return

        val registerPackageName = getRegisterPackageName(appClasses)
        val converterClasses = classes.filter { it.annotations.any { it.shortName.asString() == "Converter" } }
        val converterMap = findAnnotation(converterClasses)
        if (converterMap.isNotEmpty()) {
            createRegister(registerPackageName, converterMap)
        }
    }

    private fun findAnnotation(classes: List<KSClassDeclaration>): Map<String, KSClassDeclaration> {
        val result = mutableMapOf<String, KSClassDeclaration>()
        for (clazz in classes) {
            if (!clazz.modifiers.contains(Modifier.PUBLIC)) {
                logger.error("The modifier public is missing on ${clazz.qualifiedName?.asString()}")
                continue
            }
            val interfaces = clazz.superTypes.toList()
            if (interfaces.isEmpty()) {
                logger.warn("The annotation Converter must be used in a subclass of [MessageConverter] on ${clazz.qualifiedName?.asString()}")
                continue
            }
            for (typeRef in interfaces) {
                val resolved = typeRef.resolve()
                val name = resolved.declaration.qualifiedName?.asString()
                if (name == Constants.CONVERTER_TYPE) {
                    result[getGroup(clazz)] = clazz
                    break
                } else {
                    logger.warn("The annotation Converter must be used in a subclass of [MessageConverter] on ${clazz.qualifiedName?.asString()}")
                }
            }
        }
        return result
    }

    private fun createRegister(
        registerPackageName: String,
        converterMap: Map<String, KSClassDeclaration>
    ) {
        val converterType = ClassName.bestGuess(Constants.CONVERTER_TYPE)
        val mapType = MAP.parameterizedBy(STRING, converterType)

        val mapField = PropertySpec.builder("mMap", mapType, KModifier.PRIVATE).build()

        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addStatement("this.mMap = hashMapOf()")

        for ((group, converterClass) in converterMap) {
            val className = converterClass.toClassName()
            logger.info("------ Processing ${converterClass.simpleName.asString()} ------")
            constructorBuilder.addStatement("this.mMap.put(%S, %T())", group, className)
        }

        val registerFun = FunSpec.builder("onRegister")
            .addModifiers(KModifier.PUBLIC)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", ClassName.bestGuess(Constants.CONTEXT_TYPE))
            .addParameter("group", STRING)
            .addParameter("register", ClassName.bestGuess(Constants.REGISTER_TYPE))
            .addStatement("var converter: %T? = mMap.get(group)", converterType)
            .beginControlFlow("if(converter == null)")
            .addStatement("converter = mMap.get(%S)", "default")
            .endControlFlow()
            .beginControlFlow("if(converter != null)")
            .addStatement("register.setConverter(converter)")
            .endControlFlow()
            .build()

        val registerClass = TypeSpec.classBuilder("ConverterRegister")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(ClassName.bestGuess(Constants.ON_REGISTER_TYPE))
            .addProperty(mapField)
            .primaryConstructor(constructorBuilder.build())
            .addFunction(registerFun)
            .build()

        FileSpec.builder(registerPackageName, "ConverterRegister")
            .addType(registerClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun getGroup(type: KSClassDeclaration): String {
        val annotation = type.annotations.firstOrNull { it.shortName.asString() == "Converter" }
        val value = annotation?.arguments?.firstOrNull()?.value as? String
        if (value != null) return value
        throw IllegalStateException("The type is not a Converter: ${type.qualifiedName?.asString()}")
    }
}

class ConverterProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ConverterProcessor(environment)
    }
}
