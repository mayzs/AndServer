/*
 * Copyright © 2019 Zhenjie Yan.
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
import com.yanzhenjie.andserver.annotation.Config
import com.yanzhenjie.andserver.processor.util.Constants
import kotlin.reflect.KClass

/**
 * KSP version of [com.yanzhenjie.andserver.processor.ConfigProcessor].
 */
class ConfigProcessor(
    environment: SymbolProcessorEnvironment
) : BaseProcessor(environment) {

    override val logger = environment.logger
    override val codeGenerator = environment.codeGenerator

    override fun addAnnotation(set: MutableSet<KClass<out Annotation>>) {
        set.add(Config::class)
        set.add(AppInfo::class)
    }

    override fun processAnnotations(
        annotation: KClass<out Annotation>,
        classes: List<KSClassDeclaration>,
        resolver: Resolver
    ) {
        if (annotation != Config::class) return

        val appClasses = resolver
            .getSymbolsWithAnnotation(AppInfo::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        if (appClasses.isEmpty()) return

        val registerPackageName = getRegisterPackageName(appClasses)
        val configClasses = classes.filter { it.annotations.any { it.shortName.asString() == "Config" } }
        val configMap = findAnnotation(configClasses)
        if (configMap.isNotEmpty()) {
            createRegister(registerPackageName, configMap)
        }
    }

    private fun findAnnotation(classes: List<KSClassDeclaration>): Map<String, KSClassDeclaration> {
        val configMap = mutableMapOf<String, KSClassDeclaration>()
        for (declaration in classes) {
            if (!declaration.modifiers.contains(Modifier.PUBLIC)) {
                logger.error("The modifier public is missing on ${declaration.qualifiedName?.asString()}")
                continue
            }
            val interfaces = declaration.superTypes.toList()
            if (interfaces.isEmpty()) {
                logger.warn("The annotation Config must be used in a subclass of [WebConfig] on ${declaration.qualifiedName?.asString()}")
                continue
            }
            for (typeRef in interfaces) {
                val resolved = typeRef.resolve()
                val name = resolved.declaration.qualifiedName?.asString()
                if (name == Constants.CONFIG_TYPE) {
                    configMap[getGroup(declaration)] = declaration
                    break
                } else {
                    logger.warn("The annotation Config must be used in a subclass of [WebConfig] on ${declaration.qualifiedName?.asString()}")
                }
            }
        }
        return configMap
    }

    private fun createRegister(
        registerPackageName: String,
        configMap: Map<String, KSClassDeclaration>
    ) {
        val configClassType = ClassName.bestGuess(Constants.CONFIG_TYPE)
        val mapType = Map::class.asClassName()
            .parameterizedBy(String::class.asClassName(), configClassType)

        val mapField = PropertySpec.builder("mMap", mapType)
            .addModifiers(KModifier.PRIVATE)
            .build()

        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PUBLIC)
            .addStatement("this.mMap = hashMapOf()")

        for ((group, element) in configMap) {
            val className = element.toClassName()
            logger.info("------ Processing ${element.simpleName.asString()} ------")
            constructorBuilder.addStatement("this.mMap.put(%S, %T())", group, className)
        }

        val registerMethod = FunSpec.builder("onRegister")
            .addModifiers(KModifier.PUBLIC)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", ClassName.bestGuess(Constants.CONTEXT_TYPE))
            .addParameter("group", STRING)
            .addParameter("register", ClassName.bestGuess(Constants.REGISTER_TYPE))
            .addStatement("var config: %T? = mMap.get(group)", configClassType)
            .beginControlFlow("if(config == null)")
            .addStatement("config = mMap.get(%S)", "default")
            .endControlFlow()
            .beginControlFlow("if(config != null)")
            .addStatement("val delegate = %T.newInstance()", ClassName.bestGuess(Constants.CONFIG_DELEGATE_TYPE))
            .addStatement("config.onConfig(context, delegate)")
            .addStatement("val list: List<%T>? = delegate.getWebsites()", ClassName.bestGuess(Constants.WEBSITE_TYPE))
            .beginControlFlow("if(list != null && !list.isEmpty())")
            .beginControlFlow("for (website in list)")
            .addStatement("register.addAdapter(website)")
            .endControlFlow()
            .endControlFlow()
            .addStatement("val multipart: %T? = delegate.getMultipart()", ClassName.bestGuess(Constants.CONFIG_MULTIPART_TYPE))
            .addStatement("register.setMultipart(multipart)")
            .endControlFlow()
            .build()

        val type = TypeSpec.classBuilder("ConfigRegister")
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addSuperinterface(ClassName.bestGuess(Constants.ON_REGISTER_TYPE))
            .addProperty(mapField)
            .primaryConstructor(constructorBuilder.build())
            .addFunction(registerMethod)
            .build()

        FileSpec.builder(registerPackageName, "ConfigRegister")
            .addType(type)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }

    private fun getGroup(type: KSClassDeclaration): String {
        val annotation = type.annotations.firstOrNull { it.shortName.asString() == "Config" }
        val value = annotation?.arguments?.firstOrNull()?.value as? String
        if (value != null) return value
        throw IllegalStateException("The type is not a Config: ${type.qualifiedName?.asString()}")
    }
}

class ConfigProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ConfigProcessor(environment)
    }
}
