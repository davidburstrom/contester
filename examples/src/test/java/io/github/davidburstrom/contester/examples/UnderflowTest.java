/*
 * Copyright 2022-2024 David Burström
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.davidburstrom.contester.examples;

import static io.github.davidburstrom.contester.ConTesterDriver.join;
import static io.github.davidburstrom.contester.ConTesterDriver.resume;
import static io.github.davidburstrom.contester.ConTesterDriver.runToBreakpoint;
import static io.github.davidburstrom.contester.ConTesterDriver.runUntilBlockedOrTerminated;
import static io.github.davidburstrom.contester.ConTesterDriver.thread;

import org.junit.jupiter.api.Test;

class UnderflowTest {

  private void runTest(Underflow obj) {
    final Thread producer = thread(obj::produce);

    runToBreakpoint(producer, "produce");

    final Thread consumer = thread(obj::consume);
    runUntilBlockedOrTerminated(consumer);
    resume(producer);

    join(consumer);
  }

  @Test
  void broken() {
    try {
      runTest(new Underflow.Broken());
    } catch (AssertionError ignored) {
      // This will fail as long as the implementation is incorrect
    }
  }

  @Test
  void fixed() {
    runTest(new Underflow.Fixed());
  }
}
