package io.github.davidburstrom.contester.examples;

import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.davidburstrom.contester.ConTesterDriver;
import org.junit.jupiter.api.Test;

class ModificationTest {

  private void runTest(Modification obj) throws InterruptedException {
    Thread resetter = ConTesterDriver.thread(obj::reset);
    Thread printer = ConTesterDriver.thread(obj::print);

    ConTesterDriver.runToBreakpoint(resetter, "reset");
    ConTesterDriver.runToBreakpoint(printer, "print");
    ConTesterDriver.runUntilBlockedOrTerminated(
        resetter); // without the fix, resetter will run reset() in full
    ConTesterDriver.runUntilBlockedOrTerminated(
        printer); // without the fix, printer will dereference a null member
    assertNull(ConTesterDriver.getUncaughtThrowable(printer));
  }

  @Test
  void broken() throws InterruptedException {
    try {
      runTest(new Modification.Broken());
    } catch (AssertionError ignored) {
      // This will fail as long as the implementation is broken
    }
  }

  @Test
  void fixed() throws InterruptedException {
    runTest(new Modification.Fixed());
  }
}
