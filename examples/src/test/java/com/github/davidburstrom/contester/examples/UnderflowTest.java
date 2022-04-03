package com.github.davidburstrom.contester.examples;

import static com.github.davidburstrom.contester.ConTesterDriver.getUncaughtThrowable;
import static com.github.davidburstrom.contester.ConTesterDriver.join;
import static com.github.davidburstrom.contester.ConTesterDriver.resume;
import static com.github.davidburstrom.contester.ConTesterDriver.runToBreakpoint;
import static com.github.davidburstrom.contester.ConTesterDriver.runUntilBlockedOrTerminated;
import static com.github.davidburstrom.contester.ConTesterDriver.thread;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UnderflowTest {

  private void runTest(Underflow obj) throws InterruptedException {
    final Thread producer = thread(obj::produce);

    runToBreakpoint(producer, "produce");

    final Thread consumer = thread(obj::consume);
    runUntilBlockedOrTerminated(consumer);
    resume(producer);

    join(consumer);
    assertNull(getUncaughtThrowable(consumer));
  }

  @Test
  void broken() throws InterruptedException {
    try {
      runTest(new Underflow.Broken());
    } catch (AssertionError ignored) {
      // This will fail as long as the implementation is incorrect
    }
  }

  @Test
  void fixed() throws InterruptedException {
    runTest(new Underflow.Fixed());
  }
}
