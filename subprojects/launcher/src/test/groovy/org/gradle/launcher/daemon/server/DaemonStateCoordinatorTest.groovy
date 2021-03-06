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
package org.gradle.launcher.daemon.server

import java.util.concurrent.locks.Condition
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 2/6/12
 */
class DaemonStateCoordinatorTest extends Specification {

    def coordinator = new DaemonStateCoordinator(Mock(Runnable), Mock(Runnable), Mock(Runnable), Mock(Runnable), Mock(Runnable))

    def "requesting stop lifecycle"() {
        coordinator.asyncStop = Mock(Runnable)

        given:
        assert !coordinator.stopped

        when: "requested first time"
        def passOne = coordinator.requestStop()

        then: "retruns true and schedules stopping"
        passOne == true
        1 * coordinator.asyncStop.run()
        1 * coordinator.onStopRequested.run()
        coordinator.stopped

        when: "requested again"
        def passTwo = coordinator.requestStop()

        then: "only returns false"
        passTwo == false
        0 * coordinator.asyncStop.run()
        0 * coordinator.onStopRequested.run()
        coordinator.stopped
    }

    def "stopping lifecycle"() {
        coordinator.condition = Mock(Condition)

        given:
        assert !coordinator.stopped

        when: "stopped first time"
        coordinator.stop()

        then: "stops"
        1 * coordinator.onStop.run()
        1 * coordinator.onStopRequested.run()
        1 * coordinator.condition.signalAll()
        coordinator.stopped

        when: "requested again"
        coordinator.stop()

        then:
        0 * coordinator.onStopRequested.run()
        1 * coordinator.onStop.run()
        1 * coordinator.condition.signalAll()
        coordinator.stopped
    }
}
