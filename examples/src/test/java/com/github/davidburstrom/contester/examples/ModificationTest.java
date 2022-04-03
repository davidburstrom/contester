package com.github.davidburstrom.contester.examples;

import static com.github.davidburstrom.contester.ConTesterDriver.getUncaughtThrowable;
import static com.github.davidburstrom.contester.ConTesterDriver.runToBreakpoint;
import static com.github.davidburstrom.contester.ConTesterDriver.runUntilBlockedOrTerminated;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.davidburstrom.contester.ConTesterDriver;
import org.junit.jupiter.api.Test;

class ModificationTest {

  private void runTest(Modification obj) throws InterruptedException {
    Thread resetter = ConTesterDriver.thread(obj::reset);
    Thread printer = ConTesterDriver.thread(obj::print);

    runToBreakpoint(resetter, "reset");
    runToBreakpoint(printer, "print");
    runUntilBlockedOrTerminated(resetter); // without the fix, resetter will run reset() in full
    runUntilBlockedOrTerminated(printer); // without the fix, printer will dereference a null member
    assertNull(getUncaughtThrowable(printer));
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
