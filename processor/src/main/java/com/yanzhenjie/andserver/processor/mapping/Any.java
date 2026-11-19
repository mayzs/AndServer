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
package com.yanzhenjie.andserver.processor.mapping;

import com.yanzhenjie.andserver.annotation.RequestMapping;
import com.yanzhenjie.andserver.annotation.RequestMethod;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Created by Zhenjie Yan on 2018/6/16.
 */
public class Any implements Mapping {

    private RequestMapping mMapping;
    private boolean isRest;

    /**
     * any
     * @param mapping mapping
     * @param rest rest
     */
    public Any(RequestMapping mapping, boolean rest) {
        this.mMapping = mapping;
        this.isRest = rest;
    }

    /**
     * {@link RequestMapping#value()}
     * @return value
     */
    @Override
    public String[] value() {
        return mMapping.value();
    }

    /**
     * {@link RequestMapping#path()}
     * @return path
     */
    @Override
    public String[] path() {
        return mMapping.path();
    }

    /**
     * {@link RequestMapping#method()}
     * @return method
     */
    @Override
    public String[] method() {
        RequestMethod[] methods = mMapping.method();
        String[] textMethods = new String[methods.length];
        if (!ArrayUtils.isEmpty(methods)) {
            for (int i = 0; i < textMethods.length; i++) {
                textMethods[i] = methods[i].value();
            }
        }
        return textMethods;
    }

    /**
     * {@link RequestMapping#params()}
     * @return params
     */
    @Override
    public String[] params() {
        return mMapping.params();
    }

    /**
     * {@link RequestMapping#headers()}
     * @return headers
     */
    @Override
    public String[] headers() {
        return mMapping.headers();
    }

    /**
     * {@link RequestMapping#consumes()}
     * @return consumes
     */
    @Override
    public String[] consumes() {
        return mMapping.consumes();
    }

    /**
     * {@link RequestMapping#produces()}
     * @return produces
     */
    @Override
    public String[] produces() {
        return mMapping.produces();
    }

    /**
     * @return isRest
     */
    @Override
    public boolean isRest() {
        return isRest;
    }
}