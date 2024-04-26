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

import io.github.davidburstrom.contester.ConTesterDriver;
import org.junit.jupiter.api.Test;

class ModificationTest {

  private void runTest(Modification obj) {
    Thread resetter = ConTesterDriver.thread(obj::reset);
    Thread printer = ConTesterDriver.thread(obj::print);

    ConTesterDriver.runToBreakpoint(resetter, "reset");
    ConTesterDriver.runToBreakpoint(printer, "print");
    ConTesterDriver.runUntilBlockedOrTerminated(
        resetter); // without the fix, resetter will run reset() in full
    ConTesterDriver.runUntilBlockedOrTerminated(
        printer); // without the fix, printer will dereference a null member
  }

  @Test
  void broken() {
    try {
      runTest(new Modification.Broken());
    } catch (AssertionError ignored) {
      // This will fail as long as the implementation is broken
    }
  }

  @Test
  void fixed() {
    runTest(new Modification.Fixed());
  }
}
