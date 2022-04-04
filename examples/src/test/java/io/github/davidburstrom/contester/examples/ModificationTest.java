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
