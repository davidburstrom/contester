package io.github.davidburstrom.contester;

import static io.github.davidburstrom.contester.ConTesterDriver.disableBreakpoint;
import static io.github.davidburstrom.contester.ConTesterDriver.enableBreakpoint;
import static io.github.davidburstrom.contester.ConTesterDriver.getUncaughtThrowable;
import static io.github.davidburstrom.contester.ConTesterDriver.join;
import static io.github.davidburstrom.contester.ConTesterDriver.register;
import static io.github.davidburstrom.contester.ConTesterDriver.resume;
import static io.github.davidburstrom.contester.ConTesterDriver.runToBreakpoint;
import static io.github.davidburstrom.contester.ConTesterDriver.runUntilBlockedOrTerminated;
import static io.github.davidburstrom.contester.ConTesterDriver.start;
import static io.github.davidburstrom.contester.ConTesterDriver.thread;
import static io.github.davidburstrom.contester.ConTesterDriver.visitBreakpoint;
import static io.github.davidburstrom.contester.ConTesterDriver.waitForBlockedOrTerminated;
import static io.github.davidburstrom.contester.ConTesterDriver.waitForBreakpoint;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConTesterDriverTest {

  @AfterEach
  void tearDown() {
    ConTesterDriver.cleanUp();
  }

  @Test
  void unrelatedThreadDoesNotBlockOnBreakpoint() {
    final Thread thread1 = thread(() -> visitBreakpoint("id"));
    final Thread thread2 = thread(() -> visitBreakpoint("id"));
    enableBreakpoint(thread1, "id");
    start(thread2);
    join(thread2);
  }

  @Test
  void runToBreakpointThrowsAnyUncaughtException() {
    final Thread thread =
        thread(
            () -> {
              throw new RuntimeException();
            });
    final AssertionError assertionError =
        assertThrows(AssertionError.class, () -> runToBreakpoint(thread, "dummy"));
    assertTrue(assertionError.getMessage().contains("threw an uncaught exception"));
  }

  @Test
  void runToBreakpointThrowsIfTimeout() {
    final Thread thread =
        thread(
            () -> {
              while (true) {
                if (Thread.interrupted()) {
                  break;
                }
              }
            });
    final AssertionError assertionError =
        assertThrows(
            AssertionError.class, () -> runToBreakpoint(thread, "dummy", 1, TimeUnit.MILLISECONDS));
    assertTrue(assertionError.getMessage().contains("Breakpoint never hit"));
    thread.interrupt();
    join(thread);
  }

  private void runInnerTest() {
    AtomicBoolean innerBoolean = new AtomicBoolean();
    final Thread inner =
        thread(
            () -> {
              visitBreakpoint("id");
              innerBoolean.set(true);
            });
    runToBreakpoint(inner, "id");
    visitBreakpoint("inner");
    assertFalse(innerBoolean.get());
    join(inner);
    assertTrue(innerBoolean.get());
  }

  @Test
  void parallelTestsAreIndependent() {
    Thread parallelTestWorker = thread(this::runInnerTest);
    runToBreakpoint(parallelTestWorker, "inner");
    runInnerTest();
    join(parallelTestWorker);
  }

  @Test
  void breakpointCannotBeEnabledIfAlreadyEnabled() {
    final Thread thread = thread(() -> {});

    enableBreakpoint(thread, "id");
    assertThrows(IllegalArgumentException.class, () -> enableBreakpoint(thread, "id"));
  }

  @Test
  void breakpointCannotBeDisabledIfAlreadyDisabled() {
    final Thread thread = thread(() -> {});

    assertThrows(IllegalArgumentException.class, () -> disableBreakpoint(thread, "id"));
  }

  @Test
  void suspendedThreadsAreResumedDuringTeardown() throws InterruptedException {
    final Thread thread = thread(() -> visitBreakpoint("id"));

    enableBreakpoint(thread, "id");
    start(thread);
    waitForBreakpoint(thread, "id");
    tearDown();
    thread.join(1000);
    assertFalse(thread.isAlive());
  }

  @Test
  void runsSimpleBreakpoint() {
    AtomicBoolean first = new AtomicBoolean();
    AtomicBoolean second = new AtomicBoolean();
    final Thread thread =
        thread(
            () -> {
              first.set(true);
              visitBreakpoint("id");
              second.set(true);
            });
    assertFalse(first.get());
    runToBreakpoint(thread, "id");
    assertTrue(first.get());
    assertFalse(second.get());
    join(thread);
    assertTrue(second.get());
  }

  @Test
  void joinResumesSuspendedThread() {
    final Thread thread = thread(() -> visitBreakpoint("id"));
    runToBreakpoint(thread, "id");
    join(thread);
    assertFalse(thread.isAlive());
  }

  @Test
  void joinUnregistersBreakpoints() {
    final Thread thread =
        thread(
            () -> {
              visitBreakpoint("id 1");
              visitBreakpoint("id 2");
            });
    enableBreakpoint(thread, "id 1");
    enableBreakpoint(thread, "id 2");
    start(thread);
    waitForBreakpoint(thread, "id 1");
    join(thread);
    assertFalse(thread.isAlive());
  }

  @Test
  void registeringThreadTwiceThrows() {
    final Thread thread = new Thread(() -> {});
    register(thread);
    assertThrows(IllegalArgumentException.class, () -> register(thread));
  }

  @Test
  void runToBreakpointDisablesBreakpoints() {
    AtomicBoolean check = new AtomicBoolean();
    final Thread thread =
        thread(
            () -> {
              visitBreakpoint("id");
              check.set(true);
              visitBreakpoint("id2");
            });
    enableBreakpoint(thread, "id");
    runToBreakpoint(thread, "id2");
    assertTrue(check.get());
    join(thread);
  }

  @Test
  void runToBreakpointReenablesBreakpoints() {
    AtomicBoolean check = new AtomicBoolean(true);
    final Thread thread =
        thread(
            () -> {
              visitBreakpoint("id2");
              check.set(false);
              visitBreakpoint("id");
              check.set(true);
            });
    enableBreakpoint(thread, "id");
    runToBreakpoint(thread, "id2");
    assertTrue(check.get());
    resume(thread);
    waitForBreakpoint(thread, "id");
    assertFalse(check.get());
    join(thread);
    assertTrue(check.get());
  }

  @Test
  void runToBreakpointResumes() {
    final Thread thread =
        thread(
            () -> {
              visitBreakpoint("id1");
              visitBreakpoint("id2");
            });
    runToBreakpoint(thread, "id1");
    runToBreakpoint(thread, "id2");
  }

  @Test
  void canGetThrowableFromRegisteredThread() throws InterruptedException {
    // set no-op handler, so that the test is not spamming stderr.
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {});
    final Thread thread =
        thread(
            () -> {
              throw new RuntimeException();
            });
    start(thread);
    thread.join();
    Thread.setDefaultUncaughtExceptionHandler(null);
    assertTrue(getUncaughtThrowable(thread).isPresent());
  }

  @Test
  void canGetEmptyOptionalThrowableFromRegisteredThread() {
    final Thread thread = thread(() -> {});
    start(thread);
    join(thread);
    assertFalse(getUncaughtThrowable(thread).isPresent());
  }

  @Test
  void cannotGetThrowableFromUnregisteredThread() {
    final Thread thread = new Thread(() -> {});
    assertThrows(IllegalArgumentException.class, () -> getUncaughtThrowable(thread));
  }

  @Test
  void cannotGetThrowableFromLiveThread() {
    final Thread thread = thread(() -> visitBreakpoint("id"));
    runToBreakpoint(thread, "id");
    assertThrows(IllegalStateException.class, () -> getUncaughtThrowable(thread));
  }

  @Test
  void registeredThreadCallsThroughToCustomUncaughtExceptionHandler() throws InterruptedException {
    final Thread thread =
        new Thread(
            () -> {
              throw new RuntimeException();
            });
    final AtomicBoolean visited = new AtomicBoolean();
    thread.setUncaughtExceptionHandler((t, e) -> visited.set(true));
    start(thread);
    thread.join();
    assertTrue(visited.get());
  }

  @Test
  void liveThreadCanBeSuspended() throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    AtomicBoolean run = new AtomicBoolean(true);
    final Thread thread =
        new Thread(
            () -> {
              while (run.get()) {
                visitBreakpoint("id");
                countDownLatch.countDown();
              }
            });
    thread.start();

    // Proves that the breakpoint has already been passed at least once
    countDownLatch.await();

    register(thread);
    enableBreakpoint(thread, "id");
    waitForBreakpoint(thread, "id");

    // cleanup
    disableBreakpoint(thread, "id");
    run.set(false);
    join(thread);
  }

  @Test
  void threadIdIncreases() {
    final String infix = " / ConTester Thread ";
    final Thread thread1 = thread(() -> {});
    final Thread thread2 = thread(() -> {});
    final String thread1Name = thread1.getName();
    final String thread2Name = thread2.getName();
    assertTrue(thread1Name.contains(infix));
    assertTrue(thread2Name.contains(infix));
    final int id1 =
        Integer.parseInt(thread1Name.substring(thread1Name.indexOf(infix) + infix.length()));
    final int id2 =
        Integer.parseInt(thread2Name.substring(thread2Name.indexOf(infix) + infix.length()));
    assertEquals(id1 + 1, id2);
  }

  @Test
  void unsettingBreakpointAllowsThreadToRun() {
    final Thread thread = thread(() -> visitBreakpoint("id"));
    enableBreakpoint(thread, "id");
    disableBreakpoint(thread, "id");
    start(thread);
    join(thread);
  }

  @Test
  void threadIsAutomaticallyRegistered() {
    final Thread thread = thread(() -> {});
    assertDoesNotThrow(() -> join(thread));
  }

  @Test
  void runUntilBlocked() {
    Object lock = new Object();
    final Thread thread1 =
        thread(
            () -> {
              synchronized (lock) {
                visitBreakpoint("id");
              }
            });
    final Thread thread2 =
        thread(
            () -> {
              synchronized (lock) {
                visitBreakpoint("dummy");
              }
            });
    runToBreakpoint(thread1, "id");
    runUntilBlockedOrTerminated(thread2);
    assertEquals(Thread.State.BLOCKED, thread2.getState());
    join(thread1);
    join(thread2);
  }

  @Test
  void runUntilBlockedOrTerminatedResumesAutomatically() {
    final Thread thread = thread(() -> visitBreakpoint("id"));
    runToBreakpoint(thread, "id");
    runUntilBlockedOrTerminated(thread);
    join(thread);
  }

  @Test
  void waitForBlockedOrTerminatedMustUseStartedThread() {
    final Thread thread = thread(() -> {});
    final IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> waitForBlockedOrTerminated(thread));
    assertEquals("Cannot wait for unstarted thread", exception.getMessage());
  }

  @Test
  void throwsIfWaitingWithoutEnabledBreakpoints() {
    final Thread thread1 = thread(() -> visitBreakpoint("id"));
    final Thread thread2 = thread(() -> visitBreakpoint("id"));
    enableBreakpoint(thread2, "id");
    assertThrows(IllegalArgumentException.class, () -> waitForBreakpoint(thread1, "id"));
  }

  @Test
  void cannotSelfRegister() throws InterruptedException {
    thread(() -> {}); // dummy to pass tearDown()
    AtomicReference<Throwable> throwable = new AtomicReference<>();
    final Thread thread = new Thread(() -> register(Thread.currentThread()));
    thread.setUncaughtExceptionHandler((t, e) -> throwable.set(e));
    thread.start();
    thread.join();
    assertTrue(throwable.get().getMessage().contains("self-register"));
  }

  @Test
  void cannotJoinUnregisteredThread() {
    Thread thread = new Thread(() -> {});
    assertThrows(IllegalArgumentException.class, () -> join(thread));
  }

  @Test
  void enableBreakpointWithoutRegisteredThreads() {
    Thread thread = new Thread(() -> {});
    assertThrows(IllegalArgumentException.class, () -> enableBreakpoint(thread, "dummy"));
  }

  @Test
  void waitForBreakpointThrowsIfThreadTerminated() {
    Thread thread = thread(() -> {});
    enableBreakpoint(thread, "id");
    start(thread);
    final AssertionError assertionError =
        assertThrows(AssertionError.class, () -> waitForBreakpoint(thread, "id"));
    assertTrue(assertionError.getMessage().contains(" has terminated"));
  }

  @Test
  void runUntilBlockedOrTerminatedThrowsIfThreadThrew() {
    Thread thread =
        thread(
            () -> {
              throw new RuntimeException();
            });
    final AssertionError assertionError =
        assertThrows(AssertionError.class, () -> runUntilBlockedOrTerminated(thread));
    assertTrue(assertionError.getCause() instanceof RuntimeException);
  }

  @Test
  void runToBreakpointResumesSuspendedThread() {
    AtomicBoolean check = new AtomicBoolean();
    final Thread thread =
        thread(
            () -> {
              visitBreakpoint("id1");
              check.set(true);
              visitBreakpoint("id2");
            });

    runToBreakpoint(thread, "id1");
    runToBreakpoint(thread, "id2");
    assertTrue(check.get(), "thread didn't get released from breakpoint 'id1'");
  }

  @Test
  void waitForBreakpointFailsIfThreadNotStarted() {
    final Thread thread = thread(() -> {});
    final IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> waitForBreakpoint(thread, "dummy"));
    assertEquals("Cannot wait for unstarted thread", exception.getMessage());
  }

  @Test
  void waitForBreakpointFailsIfUnexpectedBreakpointHit() {
    final Thread thread =
        thread(
            () -> {
              visitBreakpoint("id1");
              visitBreakpoint("id2");
            });
    start(thread);
    enableBreakpoint(thread, "id1");
    enableBreakpoint(thread, "id2");

    // should fail
    assertThrows(AssertionError.class, () -> waitForBreakpoint(thread, "id2"));
  }

  @Test
  void breakpointHaltsOnCondition() {
    AtomicInteger counter = new AtomicInteger();
    final Thread thread =
        thread(
            () -> {
              while (counter.get() <= 1001) {
                counter.incrementAndGet();
                ConTesterDriver.visitBreakpoint("id", () -> counter.get() == 1000);
              }
            });
    runToBreakpoint(thread, "id");
    assertEquals(1000, counter.get());
  }

  @Test
  void runToBreakpointOnlyDisablesBreakpointsForTheGivenThread() {
    final Thread thread1 =
        thread(
            () -> {
              visitBreakpoint("id1");
              visitBreakpoint("id2");
            });
    final Thread thread2 = thread(() -> visitBreakpoint("id2"));
    enableBreakpoint(thread2, "id2");
    runToBreakpoint(thread1, "id1");
    start(thread2);
    waitForBreakpoint(thread2, "id2");
    resume(thread1);
    assertThrows(IllegalArgumentException.class, () -> waitForBreakpoint(thread1, "id2"));
  }

  @Test
  void correctDriverDataLocated() {
    Thread innerDriver =
        thread(
            () -> {
              final Thread inner = thread(() -> visitBreakpoint("id"));
              runToBreakpoint(inner, "id");
              visitBreakpoint("driver");
              join(inner);
            });
    start(innerDriver);
    Thread outer = thread(() -> visitBreakpoint("id"));
    start(outer);
    join(outer);
    assertThrows(
        AssertionError.class,
        () -> {
          // Depending on the order in which the drivers are visited, either of these calls
          // will fail
          runToBreakpoint(innerDriver, "driver");
          runToBreakpoint(outer, "id");
        });
  }
}
