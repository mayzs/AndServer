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

import com.yanzhenjie.andserver.annotation.GetMapping;
import com.yanzhenjie.andserver.annotation.RequestMethod;

/**
 * Created by Zhenjie Yan on 2018/6/16.
 */
public class Get implements Mapping {

    private GetMapping mMapping;
    private boolean isRest;

    /**
     * Constructor.
     *
     * @param mapping mapping.
     * @param rest rest.
     */
    public Get(GetMapping mapping, boolean rest) {
        this.mMapping = mapping;
        this.isRest = rest;
    }

    /**
     * Get url mapping.
     *
     * @return url mapping.
     */
    @Override
    public String[] value() {
        return mMapping.value();
    }

    /**
     * Get url path.
     *
     * @return url path.
     */
    @Override
    public String[] path() {
        return mMapping.path();
    }

    /**
     * Get request method.
     *
     * @return request method.
     */
    @Override
    public String[] method() {
        return new String[]{RequestMethod.GET.value()};
    }

    /**
     * Get request params.
     *
     * @return request params.
     */
    @Override
    public String[] params() {
        return mMapping.params();
    }

    /**
     * Get request headers.
     *
     * @return request headers.
     */
    @Override
    public String[] headers() {
        return mMapping.headers();
    }

    /**
     * Get consumes.
     *
     * @return consumes.
     */
    @Override
    public String[] consumes() {
        return mMapping.consumes();
    }

    /**
     * Get produces.
     *
     * @return produces.
     */
    @Override
    public String[] produces() {
        return mMapping.produces();
    }

    /**
     * Is rest controller.
     *
     * @return {@code true} if rest controller.
     */
    @Override
    public boolean isRest() {
        return isRest;
    }
}