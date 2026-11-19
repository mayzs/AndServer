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

import com.yanzhenjie.andserver.annotation.PatchMapping;
import com.yanzhenjie.andserver.annotation.RequestMethod;

/**
 * Created by Zhenjie Yan on 2018/6/16.
 */
public class Patch implements Mapping {

    private PatchMapping mMapping;
    private boolean isRest;

    /**
     * Patch mapping.
     * @param mapping annotation.
     * @param rest is rest mapping.
     */
    public Patch(PatchMapping mapping, boolean rest) {
        this.mMapping = mapping;
        this.isRest = rest;
    }

    /**
     * Get mapping value.
     * @return value.
     */
    @Override
    public String[] value() {
        return mMapping.value();
    }

    /**
     * Get mapping path.
     * @return path.
     */
    @Override
    public String[] path() {
        return mMapping.path();
    }

    /**
     * Get mapping method.
     * @return method.
     */
    @Override
    public String[] method() {
        return new String[]{RequestMethod.PATCH.value()};
    }

    /**
     * Get mapping params.
     * @return params.
     */
    @Override
    public String[] params() {
        return mMapping.params();
    }

    /**
     * Get mapping headers.
     * @return headers.
     */
    @Override
    public String[] headers() {
        return mMapping.headers();
    }

    /**
     * Get mapping consumes.
     * @return consumes.
     */
    @Override
    public String[] consumes() {
        return mMapping.consumes();
    }

    /**
     * Get mapping produces.
     * @return produces.
     */
    @Override
    public String[] produces() {
        return mMapping.produces();
    }

    /**
     * Is rest mapping.
     * @return true if is rest mapping.
     */
    @Override
    public boolean isRest() {
        return isRest;
    }
}