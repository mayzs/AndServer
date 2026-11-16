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
import kotlin.reflect.KClass

/**
 * Base KSP processor mirroring the kapt [BaseProcessor.java].
 */
abstract class BaseProcessor(
    protected val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    protected open val logger: KSPLogger = environment.logger
    protected open val codeGenerator: CodeGenerator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotations = mutableSetOf<KClass<out Annotation>>()
        addAnnotation(annotations)

        for (annotation in annotations) {
            val symbols = resolver
                .getSymbolsWithAnnotation(annotation.qualifiedName!!)
                .filterIsInstance<KSClassDeclaration>()
                .toList()
            processAnnotations(annotation, symbols, resolver)
        }

        return emptyList()
    }

    protected open fun processAnnotations(
        annotation: KClass<out Annotation>,
        classes: List<KSClassDeclaration>,
        resolver: Resolver
    ) {
        for (clazz in classes) {
            processClass(clazz, resolver)
        }
    }

    protected abstract fun addAnnotation(set: MutableSet<KClass<out Annotation>>)

    protected open fun processClass(
        classDeclaration: KSClassDeclaration,
        resolver: Resolver
    ) {
    }

    protected fun isAcceptType(
        declaration: KSClassDeclaration,
        target: KClass<*>
    ): Boolean {
        val qualifiedName = target.qualifiedName ?: return false
        for (typeRef in declaration.superTypes) {
            val resolved = typeRef.resolve()
            val name = resolved.declaration.qualifiedName?.asString()
            if (name == qualifiedName) return true
            val parent = resolved.declaration as? KSClassDeclaration
            if (parent != null && isAcceptType(parent, target)) return true
        }
        return false
    }

    protected fun isAcceptInterface(
        declaration: KSClassDeclaration,
        target: KClass<*>
    ): Boolean {
        val qualifiedName = target.qualifiedName ?: return false
        for (typeRef in declaration.superTypes) {
            val resolved = typeRef.resolve()
            val parent = resolved.declaration as? KSClassDeclaration
            val name = resolved.declaration.qualifiedName?.asString()
            if (name == qualifiedName) return true
            if (parent != null && isAcceptInterface(parent, target)) return true
        }
        return false
    }

    protected fun getRegisterPackageName(classes: List<KSClassDeclaration>): String {
        val list = classes.mapNotNull { cls ->
            cls.annotations
                .firstOrNull { a -> a.shortName.asString() == "AppInfo" }
                ?.arguments
                ?.firstOrNull()
                ?.value as? String
        }
        if (list.isEmpty()) {
            throw RuntimeException(PLUGIN_MESSAGE)
        }
        val rootPackage = list[0]
        return "$rootPackage.andserver.processor.generator"
    }

    companion object {
        private const val PLUGIN_MESSAGE = """
Add the plugin to your project build script :
buildscript {
    repositories {
       mavenCentral()
       google()
    }
    dependencies {
        classpath 'com.yanzhenjie.andserver:plugin:{version}'
        ...
    }
}

And then apply it in your module:
apply plugin: 'com.yanzhenjie.andserver'
"""
    }
}
