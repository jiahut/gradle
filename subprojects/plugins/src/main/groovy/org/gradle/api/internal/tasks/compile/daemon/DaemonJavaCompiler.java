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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.UncheckedException;

public class DaemonJavaCompiler implements Compiler<JavaCompileSpec> {
    private final ProjectInternal project;
    private final Compiler<JavaCompileSpec> delegate;

    public DaemonJavaCompiler(ProjectInternal project, Compiler<JavaCompileSpec> delegate) {
        this.project = project;
        this.delegate = delegate;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        CompileOptions compileOptions = spec.getCompileOptions();
        DaemonForkOptions forkOptions = new DaemonForkOptions(compileOptions.getForkOptions());
        CompilerDaemon daemon = CompilerDaemonManager.getInstance().getDaemon(project, forkOptions);
        CompileResult result = daemon.execute(delegate, spec);
        if (result.isSuccess()) {
            return result;
        }
        throw UncheckedException.asUncheckedException(result.getException());
    }
}
