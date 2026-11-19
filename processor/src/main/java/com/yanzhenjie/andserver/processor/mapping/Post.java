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

import com.yanzhenjie.andserver.annotation.PostMapping;
import com.yanzhenjie.andserver.annotation.RequestMethod;

/**
 * Created by Zhenjie Yan on 2018/6/16.
 */
public class Post implements Mapping {

    private PostMapping mMapping;
    private boolean isRest;

    /**
     * Constructor.
     * @param mapping annotation.
     * @param rest whether it is a restful request.
     */
    public Post(PostMapping mapping, boolean rest) {
        this.mMapping = mapping;
        this.isRest = rest;
    }

    /**
     * Get request path.
     * @return value
     */
    @Override
    public String[] value() {
        return mMapping.value();
    }

    /**
     * Get request path.
     * @return path
     */
    @Override
    public String[] path() {
        return mMapping.path();
    }

    /**
     * Get request method.
     * @return method
     */
    @Override
    public String[] method() {
        return new String[]{RequestMethod.POST.value()};
    }

    /**
     * Get request params.
     * @return params
     */
    @Override
    public String[] params() {
        return mMapping.params();
    }

    /**
     * Get request headers.
     * @return headers
     */
    @Override
    public String[] headers() {
        return mMapping.headers();
    }

    /**
     * Get request consumes.
     * @return consumes
     */
    @Override
    public String[] consumes() {
        return mMapping.consumes();
    }

    /**
     * Get request produces.
     * @return produces
     */
    @Override
    public String[] produces() {
        return mMapping.produces();
    }

    /**
     * Whether it is a restful request.
     * @return true if it is a restful request.
     */
    @Override
    public boolean isRest() {
        return isRest;
    }
}