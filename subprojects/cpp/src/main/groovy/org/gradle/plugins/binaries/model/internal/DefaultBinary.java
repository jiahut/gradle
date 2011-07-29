/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.binaries.model.internal;

import org.gradle.plugins.binaries.model.Binary;
import org.gradle.plugins.binaries.model.CompileSpec;

import org.gradle.util.ConfigureUtil;

import groovy.lang.Closure;

public class DefaultBinary implements Binary {

    private final String name;
    private final CompileSpec spec;

    public DefaultBinary(String name, CompileSpec spec) {
        this.name = name;
        this.spec = spec;
    }

    public String getName() {
        return name;
    }

    public CompileSpec getSpec() {
        return spec;
    }

    public CompileSpec spec(Closure closure) {
        return ConfigureUtil.configure(closure, spec);
    }
}