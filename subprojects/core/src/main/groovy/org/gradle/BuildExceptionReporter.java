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
package org.gradle;

import org.codehaus.groovy.runtime.StackTraceUtils;
import org.gradle.api.GradleException;
import org.gradle.api.internal.LocationAwareException;
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.ImplicitTasksConfigurer;
import org.gradle.execution.TaskSelectionException;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.ShowStacktrace;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LinePrefixingStyledTextOutput;
import org.gradle.logging.internal.LoggingCommandLineConverter;
import org.gradle.logging.internal.BufferingStyledTextOutput;
import org.gradle.util.GUtil;
import org.gradle.util.TreeVisitor;

import static org.gradle.logging.StyledTextOutput.Style.*;

/**
 * A {@link BuildListener} which reports the build exception, if any.
 */
public class BuildExceptionReporter extends BuildAdapter {
    private enum ExceptionStyle {
        NONE, SANITIZED, FULL
    }

    private final StyledTextOutputFactory textOutputFactory;
    private final LoggingConfiguration loggingConfiguration;
    private final BuildClientMetaData clientMetaData;

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, LoggingConfiguration loggingConfiguration, BuildClientMetaData clientMetaData) {
        this.textOutputFactory = textOutputFactory;
        this.loggingConfiguration = loggingConfiguration;
        this.clientMetaData = clientMetaData;
    }

    public void buildFinished(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }

        reportException(failure);
    }

    public void reportException(Throwable failure) {
        FailureDetails details = new FailureDetails(failure);
        if (failure instanceof GradleException) {
            reportBuildFailure((GradleException) failure, details);
        } else {
            reportInternalError(details);
        }

        write(details);
    }

    protected void write(FailureDetails details) {
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);

        output.println();
        output.withStyle(Failure).text("FAILURE: ");
        details.summary.writeTo(output.withStyle(Failure));

        if (details.location.getHasContent()) {
            output.println().println();
            output.println("* Where:");
            details.location.writeTo(output);
        }

        if (details.details.getHasContent()) {
            output.println().println();
            output.println("* What went wrong:");
            details.details.writeTo(output);
        }

        if (details.resolution.getHasContent()) {
            output.println().println();
            output.println("* Try:");
            details.resolution.writeTo(output);
        }

        Throwable exception = null;
        switch (details.exceptionStyle) {
            case NONE:
                break;
            case SANITIZED:
                exception = StackTraceUtils.deepSanitize(details.failure);
                break;
            case FULL:
                exception = details.failure;
                break;
        }

        if (exception != null) {
            output.println().println();
            output.println("* Exception is:");
            output.exception(exception);
        }

        output.println();
    }

    public void reportInternalError(FailureDetails details) {
        details.summary.text("Build aborted because of an internal error.");
        details.details.text("Build aborted because of an unexpected internal error. Please file an issue at: http://www.gradle.org.");

        if (loggingConfiguration.getLogLevel() != LogLevel.DEBUG) {
            details.resolution.text("Run with ");
            details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.DEBUG_LONG);
            details.resolution.text(" option to get additional debug info.");
            details.exceptionStyle = ExceptionStyle.FULL;
        }
    }

    private void reportBuildFailure(GradleException failure, FailureDetails details) {
        if (loggingConfiguration.getShowStacktrace() == ShowStacktrace.ALWAYS || loggingConfiguration.getLogLevel() == LogLevel.DEBUG) {
            details.exceptionStyle = ExceptionStyle.SANITIZED;
        }
        if (loggingConfiguration.getShowStacktrace() == ShowStacktrace.ALWAYS_FULL) {
            details.exceptionStyle = ExceptionStyle.FULL;
        }

        if (failure instanceof TaskSelectionException) {
            formatTaskSelectionFailure((TaskSelectionException) failure, details);
        } else {
            formatGenericFailure(failure, details);
        }
    }

    private void formatTaskSelectionFailure(TaskSelectionException failure, FailureDetails details) {
        assert failure.getCause() == null;
        details.summary.text("Could not determine which tasks to execute.");
        details.details.text(getMessage(failure));
        details.resolution.text("Run ");
        clientMetaData.describeCommand(details.resolution.withStyle(UserInput), ImplicitTasksConfigurer.TASKS_TASK);
        details.resolution.text(" to get a list of available tasks.");
    }

    private void formatGenericFailure(GradleException failure, final FailureDetails details) {
        details.summary.text("Build failed with an exception.");

        fillInFailureResolution(details);

        if (failure instanceof LocationAwareException) {
            final LocationAwareException scriptException = (LocationAwareException) failure;
            details.failure = scriptException.getCause();
            if (scriptException.getLocation() != null) {
                details.location.text(scriptException.getLocation());
            }
            scriptException.visitReportableCauses(new TreeVisitor<Throwable>() {
                int depth;

                @Override
                public void node(final Throwable node) {
                    if (node == scriptException) {
                        details.details.text(scriptException.getOriginalMessage());
                    } else {
                        details.details.format("%n");
                        StringBuilder prefix = new StringBuilder();
                        for (int i = 1; i < depth; i++) {
                            prefix.append("   ");
                        }
                        details.details.text(prefix);
                        prefix.append("  ");
                        details.details.style(Info).text("> ").style(Normal);
                        
                        final LinePrefixingStyledTextOutput output = new LinePrefixingStyledTextOutput(details.details, prefix);
                        output.text(getMessage(node));
                    }
                }

                @Override
                public void startChildren() {
                    depth++;
                }

                @Override
                public void endChildren() {
                    depth--;
                }
            });
        } else {
            details.details.text(getMessage(failure));
        }
    }

    private void fillInFailureResolution(FailureDetails details) {
        if (details.exceptionStyle == ExceptionStyle.NONE) {
            details.resolution.text("Run with ");
            details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.STACKTRACE_LONG);
            details.resolution.text(" option to get the stack trace. ");
        }

        if (loggingConfiguration.getLogLevel() != LogLevel.DEBUG) {
            details.resolution.text("Run with ");
            if (loggingConfiguration.getLogLevel() != LogLevel.INFO) {
                details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.INFO_LONG);
                details.resolution.text(" or ");
            }
            details.resolution.withStyle(UserInput).format("--%s", LoggingCommandLineConverter.DEBUG_LONG);
            details.resolution.text(" option to get more log output.");
        }
    }

    private String getMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (GUtil.isTrue(message)) {
            return message;
        }
        return String.format("%s (no error message)", throwable.getClass().getName());
    }

    private static class FailureDetails {
        Throwable failure;
        final BufferingStyledTextOutput summary = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput details = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput location = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput resolution = new BufferingStyledTextOutput();

        ExceptionStyle exceptionStyle = ExceptionStyle.NONE;

        public FailureDetails(Throwable failure) {
            this.failure = failure;
        }
    }
}
