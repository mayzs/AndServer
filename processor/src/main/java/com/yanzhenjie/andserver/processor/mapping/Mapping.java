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
import com.yanzhenjie.andserver.annotation.ResponseBody;

/**
 * Created by Zhenjie Yan on 2018/6/16.
 */
public interface Mapping {

    /**
     * {@link RequestMapping#value()}
     * @return value
     */
    String[] value();

    /**
     * {@link RequestMapping#path()}
     * @return path
     */
    String[] path();

    /**
     * {@link RequestMapping#method()}
     * @return method
     */
    String[] method();

    /**
     * {@link RequestMapping#params()}
     * @return params
     */
    String[] params();

    /**
     * {@link RequestMapping#headers()}
     * @return headers
     */
    String[] headers();

    /**
     * {@link RequestMapping#consumes()}
     * @return consumes
     */
    String[] consumes();

    /**
     * {@link RequestMapping#produces()}
     * @return produces
     */
    String[] produces();

    /**
     * {@link ResponseBody}
     * @return isRest
     */
    boolean isRest();
}