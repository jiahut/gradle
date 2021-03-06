/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal;

import java.lang.reflect.InvocationTargetException;

/**
 * Wraps a checked exception. Carries no other context.
 */
public final class UncheckedException extends RuntimeException {
    public UncheckedException(Throwable cause) {
        super(cause);
    }

    public static RuntimeException asUncheckedException(Throwable t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new UncheckedException(t);
    }

    /**
     * Uwraps passed InvocationTargetException hence making the stack of exceptions cleaner without losing information.
     * @param e to be unwrapped
     * @return an instance of RuntimeException based on the target exception of the parameter.
     */
    public static RuntimeException unwrap(InvocationTargetException e) {
        if (e.getTargetException() instanceof RuntimeException) {
            return (RuntimeException) e.getTargetException();
        } else {
            return UncheckedException.asUncheckedException(e.getTargetException());
        }
    }
}