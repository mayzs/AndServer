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

/**
 * Created by Zhenjie Yan on 2018/9/17.
 */
public class Null implements Mapping {

    private boolean isRest;
    /**
     * Null mapping.
     */
    public Null() {
        this(false);
    }

    /**
     * Null mapping.
     * @param isRest is rest.
     */
    public Null(boolean isRest) {
        this.isRest = isRest;
    }

    /**
     * Get mapping value.
     * @return value.
     */
    @Override
    public String[] value() {
        return new String[0];
    }

    /**
     * Get mapping path.
     * @return path.
     */
    @Override
    public String[] path() {
        return new String[0];
    }

    /**
     * Get mapping method.
     * @return method.
     */
    @Override
    public String[] method() {
        return new String[0];
    }

    /**
     * Get mapping params.
     * @return params.
     */
    @Override
    public String[] params() {
        return new String[0];
    }

    /**
     * Get mapping headers.
     * @return headers.
     */
    @Override
    public String[] headers() {
        return new String[0];
    }

    /**
     * Get mapping consumes.
     * @return consumes.
     */
    @Override
    public String[] consumes() {
        return new String[0];
    }

    /**
     * Get mapping produces.
     * @return produces.
     */
    @Override
    public String[] produces() {
        return new String[0];
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