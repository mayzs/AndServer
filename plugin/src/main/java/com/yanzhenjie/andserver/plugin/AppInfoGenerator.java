/*
 * Copyright 2020 Zhenjie Yan.
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
package com.yanzhenjie.andserver.plugin;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.yanzhenjie.andserver.annotation.AppInfo;
import com.yanzhenjie.andserver.plugin.util.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
//import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.Random;

import javax.lang.model.element.Modifier;
import org.gradle.api.provider.Property;
import java.nio.charset.StandardCharsets;

/**
 * Created by Zhenjie Yan on 4/11/20.
 */

@DisableCachingByDefault(because = "Generated class name is random, output is not deterministic")
public abstract class AppInfoGenerator extends DefaultTask {
    /**
     * Default constructor.
     */
    public AppInfoGenerator(){}
    private static final char[] CHARS = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
            'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
    /**
     * output 目录输出目录。
     * @return outputDir
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();
/**
 * assets 目录输出目录。
 * @return assetsOutputDir
 */
    @OutputDirectory
    public abstract DirectoryProperty getAssetsOutputDir();

    /**
     * App id.
     * @return appId
     */
    @Input
    public abstract Property<String> getAppId();

    /**
     * Package name.
     * @return packageName
     */
    @Input
    public abstract Property<String> getPackageName();

    /**
     * Generate app info.
     * @throws Exception 抛异常
     */
    @TaskAction
    public void generate() throws Exception {
        String appId = getAppId().get();
        String packageName = getPackageName().get();

        // 1. 生成带 @AppInfo 注解的 Java 类
        File outputDir = getOutputDir().get().getAsFile();
        FileUtils.deleteDirectory(outputDir);

        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            builder.append(CHARS[random.nextInt(CHARS.length)]);
        }

        AnnotationSpec annotation = AnnotationSpec.builder(AppInfo.class)
                .addMember("value", "$S", appId)
                .build();

        TypeSpec adapterClass = TypeSpec.classBuilder(AndServerPlugin.capitalize(builder.toString()))
                .addJavadoc(Constants.DOC_EDIT_WARN)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(annotation)
                .build();

        JavaFile.builder(packageName, adapterClass).build().writeTo(outputDir);

        // 2. 生成 assets 标记文件（<appId>.andserver），由 Sources API 正式合并进 APK
        File assetsDir = getAssetsOutputDir().get().getAsFile();
        FileUtils.deleteDirectory(assetsDir);
        FileUtils.forceMkdir(assetsDir);

        String filename = appId + ".andserver";
        FileUtils.writeStringToFile(new File(assetsDir, filename), filename, StandardCharsets.UTF_8);
    }
}