/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.integtests.tooling.m9

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.Jvm
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Timeout

@MinToolingApiVersion('1.0-milestone-9')
@MinTargetGradleVersion('1.0-milestone-8')
class M9JavaConfigurabilityIntegrationTest extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process
        toolingApi.isEmbedded = false
    }

    def "uses sensible java defaults if nulls configured"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                    .setJvmArguments(null)
                    .get()
        }

        then:
        env.java.javaHome
    }

    @Issue("GRADLE-1799")
    @Timeout(25)
    def "promptly discovers when java is not a valid installation"() {
        def dummyJdk = dist.file("wrong jdk location").createDir()

        when:
        def ex = maybeFailWithConnection {
            it.newBuild().setJavaHome(dummyJdk).run()
        }

        then:
        ex instanceof GradleConnectionException
        ex.cause.message.contains "wrong jdk location"
    }

    def "uses defaults when a variant of empty jvm args requested"() {
        when:
        def env = withConnection {
            it.model(BuildEnvironment.class).setJvmArguments(new String[0]).get()
        }

        def env2 = withConnection {
            it.model(BuildEnvironment.class).setJvmArguments(null).get()
        }

        def env3 = withConnection {
            it.model(BuildEnvironment.class).get()
        }

        then:
        env.java.jvmArguments
        env.java.jvmArguments == env2.java.jvmArguments
        env.java.jvmArguments == env3.java.jvmArguments
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "customized java home is reflected in the java.home and the build model"() {
        given:
        dist.file('build.gradle') << "project.description = new File(System.getProperty('java.home')).canonicalPath"

        when:
        File javaHome = AvailableJavaHomes.bestAlternative
        BuildEnvironment env
        Project project
        withConnection {
            env = it.model(BuildEnvironment.class).setJavaHome(javaHome).get()
            project = it.model(Project.class).setJavaHome(javaHome).get()
        }

        then:
        project.description.startsWith(env.java.javaHome.canonicalPath)
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "tooling api provided java home takes precedence over gradle.properties"() {
        File javaHome = AvailableJavaHomes.bestAlternative
        String javaHomePath = TextUtil.escapeString(javaHome.canonicalPath)
        File otherJava = Jvm.current().getJavaHome()
        String otherJavaPath = TextUtil.escapeString(otherJava.canonicalPath)

        dist.file('build.gradle') << "assert new File(System.getProperty('java.home')).canonicalPath.startsWith('$javaHomePath')"
        dist.file('gradle.properties') << "org.gradle.java.home=$otherJavaPath"

        when:
        def env = withConnection {
            it.newBuild().setJavaHome(javaHome).run() //the assert
            it.model(BuildEnvironment.class)
                    .setJavaHome(javaHome)
                    .get()
        }

        then:
        env != null
        env.java.javaHome == javaHome
        env.java.javaHome != otherJava
    }
}
