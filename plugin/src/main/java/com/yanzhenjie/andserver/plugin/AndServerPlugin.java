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

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.api.BaseVariant;
import com.yanzhenjie.andserver.plugin.util.Log;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nonnull;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.GeneratesApk;
import com.android.build.api.variant.TestComponent;
import com.android.build.api.variant.Variant;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;


import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Created by Zhenjie Yan on 4/10/20.
 */
public class AndServerPlugin implements Plugin<Project> {
    /**
     * Default constructor.
     */
    public AndServerPlugin(){}

    @Override
    public void apply(@Nonnull Project project) {
        Log.inject(project);

        // 用插件 ID 感知，替代 instanceof AppPlugin / LibraryPlugin（AGP 9 已隐藏这些内部类），
        // 同时兼容 "先 apply 本插件 / 后 apply AGP" 两种顺序
        project.getPluginManager().withPlugin("com.android.application",
                applied -> configGenerator(project, true));
        project.getPluginManager().withPlugin("com.android.library",
                applied -> configGenerator(project, false));
    }

    @SuppressWarnings("unchecked")
    private void configGenerator(Project project, boolean isApp) {
        AndroidComponentsExtension<?, ?, Variant> androidComponents =
                (AndroidComponentsExtension<?, ?, Variant>)
                        project.getExtensions().getByType(AndroidComponentsExtension.class);

        Function1<Variant, Unit> callback = variant -> {
            // 原 applicationVariants/libraryVariants 只含主变体，跳过测试组件
            if (variant instanceof TestComponent) {
                return Unit.INSTANCE;
            }

            // 应用模块用 applicationId（含 flavor 后缀），库模块用 namespace
            Provider<String> appIdProvider;
            if (isApp && variant instanceof GeneratesApk) {
                appIdProvider = ((GeneratesApk) variant).getApplicationId();
            } else {
                appIdProvider = variant.getNamespace();
            }

            String variantName = variant.getName();
            String taskName = "generate" + capitalize(variantName) + "AppInfo";

            TaskProvider<AppInfoGenerator> generate =
                    project.getTasks().register(taskName, AppInfoGenerator.class, generator -> {
                        generator.getAppId().set(appIdProvider);
                        generator.getPackageName().set(appIdProvider.map(
                                appId -> appId + ".andserver.plugin.generator"));
                        // 输出目录由下方 Sources API 自动接线，这里不要手动 set
                    });

            // 替代 registerJavaGeneratingTask(generate, outputDir)
            if (variant.getSources().getJava() != null) {
                variant.getSources().getJava()
                        .addGeneratedSourceDirectory(generate, AppInfoGenerator::getOutputDir);
            }

            // 替代往 mergeAssets 输出目录 doLast 写文件的 hack：
            // 生成的 assets 目录会被正式合并进 APK assets
            if (variant.getSources().getAssets() != null) {
                variant.getSources().getAssets()
                        .addGeneratedSourceDirectory(generate, AppInfoGenerator::getAssetsOutputDir);
            }

            return Unit.INSTANCE;
        };

        androidComponents.onVariants(androidComponents.selector().all(), callback);
    }

    /**
     * 首字母大写。
     * @param text 转换的文字
     * @return 转换后的文字
     */
    public static String capitalize(String text) {
        if (text != null && text.length() > 0) {
            char[] chars = text.toCharArray();
            chars[0] = Character.toUpperCase(chars[0]);
            return new String(chars);
        }
        return text;
    }
}