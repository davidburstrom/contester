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
