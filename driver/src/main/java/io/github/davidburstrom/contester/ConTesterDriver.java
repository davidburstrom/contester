package io.github.davidburstrom.contester;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * The ConTester driver utility.
 *
 * <h2>Overview</h2>
 *
 * The ConTester API mimics the functionality of a JVM debugger, with breakpoints, suspended/resumed
 * threads, etc. This makes it ideal to prove that concurrency constructs work as intended and to
 * regression test any concurrency related bug that has been or can be proven through debugging.
 *
 * <p>The production code uses the {@code #defineBreakpoint} method to indicate where threads can be
 * suspended, and the unit test code uses the other API methods to precisely control the execution
 * of the threads.
 *
 * <h2>Simple usage</h2>
 *
 * <ol>
 *   <li>Introduce one or more {@code #defineBreakpoint}s in the production code, as required.
 *       Thinking in terms of debugging, place them before the statements that'd have the debugger
 *       breakpoint set.
 *   <li>In the test code, use {@link #thread} to set up two or more {@link Thread}s that execute
 *       the production code, using the supplied {@link Runnable}s.
 *   <li>Use a combination of {@link #runToBreakpoint} and {@link #resume} to control how the {@link
 *       Thread}s execute the production code.
 *   <li>If necessary, verify that the {@link Thread}s did or didn't throw any uncaught exception by
 *       using {@link #join} and {@link #getUncaughtThrowable}.
 * </ol>
 */
@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public final class ConTesterDriver {

  /** Standard timeout, in milliseconds, for blocking APIs. */
  public static final long STANDARD_TIMEOUT_MS = 10_000;

  private static final Map<Thread, DriverData> DRIVER_REGISTRY =
      Collections.synchronizedMap(new WeakHashMap<>());

  /** Prohibit instantiation */
  private ConTesterDriver() {}

  /**
   * Creates a convenient {@link Thread} that can be used to exercise a {@link Runnable}.
   *
   * @param runnable The {@link Runnable} to execute.
   * @return A {@link Thread} that has been registered in the driver, but not started.
   */
  public static Thread thread(final Runnable runnable) {
    final DriverData driverData = getOrCreateDriverData();
    final Thread thread =
        new Thread(
            runnable,
            Thread.currentThread().getName()
                + " / ConTester Thread "
                + driverData.getNextThreadId());
    register(thread);
    return thread;
  }

  /**
   * Runs the given thread until it suspends on the given breakpoint ID.
   *
   * <p>Any other breakpoints enabled for the thread are ignored while this method executes.
   *
   * <p>If the timeout as specified by {@link #STANDARD_TIMEOUT_MS} occurs before the breakpoint is
   * hit, an exception will be thrown.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   * @param id A breakpoint ID.
   */
  public static void runToBreakpoint(Thread thread, String id) {
    runToBreakpoint(thread, id, STANDARD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Runs the given thread until it suspends on the given breakpoint ID.
   *
   * <p>Any other breakpoints enabled for the thread are ignored while this method executes.
   *
   * <p>If the timeout occurs before the breakpoint is hit, an exception will be thrown.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   * @param id A breakpoint ID.
   * @param timeout A duration.
   * @param timeUnit The time unit of the given timeout.
   */
  public static void runToBreakpoint(Thread thread, String id, long timeout, TimeUnit timeUnit) {
    Set<String> enabledBreakpoints = getEnabledBreakpoints(thread);
    enabledBreakpoints.forEach(enabledId -> disableBreakpoint(thread, enabledId));

    enableBreakpoint(thread, id);

    resumeIfNecessary(thread);

    startIfNecessary(thread);

    waitForBreakpoint(thread, id, timeout, timeUnit);

    enabledBreakpoints.forEach(enabledId -> enableBreakpoint(thread, enabledId));
  }

  /**
   * Runs a thread until it is either blocked or finished.
   *
   * <p>Use this to guarantee that the thread either is waiting to enter critical block held by
   * another thread, or that it is done executing.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   */
  // TODO: Really should be `runUntilBlocked` right? Though, it will fail if synchronization is not
  // introduced first.
  public static void runUntilBlockedOrTerminated(Thread thread) {
    startIfNecessary(thread);

    resumeIfNecessary(thread);

    waitForBlockedOrTerminated(thread);
  }

  /**
   * Registers the given thread so that it is controlled by the driver thread.
   *
   * <p>This method must be called from the driver thread.
   *
   * <p>It is an error to try to register the thread more than once.
   *
   * @param thread A previously unregistered thread, different from the driver thread.
   */
  public static void register(final Thread thread) {
    if (isRegistered(thread)) {
      throw new IllegalArgumentException("Thread " + thread + " has already been registered");
    }

    if (thread.equals(Thread.currentThread())) {
      throw new IllegalArgumentException("A thread cannot self-register");
    }

    DriverData driverData = getOrCreateDriverData();

    final ThreadData threadData = new ThreadData();

    driverData.getThreadRegistry().put(thread, threadData);

    final Thread.UncaughtExceptionHandler installedExceptionHandler =
        thread.getUncaughtExceptionHandler();

    thread.setUncaughtExceptionHandler(
        (t, e) -> {
          threadData.setUncaughtThrowable(e);
          installedExceptionHandler.uncaughtException(t, e);
        });
  }

  public static void start(final Thread thread) {
    registerIfNecessary(thread);
    thread.start();
  }

  /**
   * Enables a breakpoint so that the given thread will suspend if it's hit.
   *
   * @param thread A previously (implicitly or explicitly) registered thread.
   * @param id A breakpoint ID.
   */
  public static void enableBreakpoint(Thread thread, String id) {
    checkRegistered(thread);

    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());
    Set<Thread> threads =
        driverData.getEnabledBreakpoints().computeIfAbsent(id, k -> new HashSet<>(4));

    if (!threads.add(thread)) {
      throw new IllegalArgumentException("Breakpoint '" + id + "' is already enabled");
    }
  }

  /**
   * Disables a breakpoint so that the given thread will suspend if it's hit.
   *
   * @param thread A previously (implicitly or explicitly) registered thread.
   * @param id A breakpoint ID.
   */
  public static void disableBreakpoint(Thread thread, String id) {
    checkRegistered(thread);

    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());
    final Set<Thread> threads = driverData.getEnabledBreakpoints().get(id);
    if (threads != null) {
      if (!threads.remove(thread)) {
        throw new IllegalArgumentException("Breakpoint '" + id + "' is already disabled");
      }
    } else {
      throw new IllegalArgumentException("Breakpoint '" + id + "' is already disabled");
    }
  }

  /**
   * Waits until the thread hits the given breakpoint.
   *
   * <p>If the timeout (as specified by {@link #STANDARD_TIMEOUT_MS}) occurs before the breakpoint
   * is hit, an {@link AssertionError} will be thrown.
   *
   * @param thread A previously (implicitly or explicitly) registered thread.
   * @param id A breakpoint ID.
   */
  public static void waitForBreakpoint(Thread thread, String id) {
    waitForBreakpoint(thread, id, STANDARD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Waits until the thread hits the given breakpoint.
   *
   * <p>If the timeout occurs before the breakpoint is hit, or the thread throws an uncaught
   * exception, an {@link AssertionError} will be thrown.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   * @param id A breakpoint ID.
   * @param timeout A duration.
   * @param timeUnit The time unit of the given timeout.
   */
  public static void waitForBreakpoint(Thread thread, String id, long timeout, TimeUnit timeUnit) {

    if (thread.getState() == Thread.State.NEW) {
      throw new IllegalArgumentException("Cannot wait for unstarted thread");
    }

    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());

    final Set<Thread> threadSet = driverData.getEnabledBreakpoints().get(id);
    if (threadSet == null || !threadSet.contains(thread)) {
      throw new IllegalArgumentException(
          "Breakpoint '" + id + "' is not enabled for " + thread.getName());
    }

    final long endTime = System.nanoTime() + timeUnit.toNanos(timeout);
    final ThreadData threadData = driverData.getThreadRegistry().get(thread);
    synchronized (threadData) {
      while (threadData.getSuspended() == null && System.nanoTime() < endTime) {

        try {
          threadData.wait(1);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }

        if (thread.getState() == Thread.State.TERMINATED) {
          final Optional<Throwable> uncaughtThrowable = getUncaughtThrowable(thread);
          if (uncaughtThrowable.isPresent()) {
            throw new AssertionError(
                thread + " threw an uncaught exception", uncaughtThrowable.get());
          } else {
            throw new AssertionError(thread + " has terminated");
          }
        }
      }
      if (threadData.getSuspended() == null) {
        throw new AssertionError(
            "Breakpoint wasn't hit within "
                + timeout
                + " "
                + timeUnit.toString().toLowerCase(Locale.ROOT));
      } else if (!threadData.getSuspended().equals(id)) {
        throw new AssertionError(
            "Thread suspended on unexpected breakpoint '" + threadData.getSuspended());
      }
    }
  }

  /**
   * Waits until the thread is blocked or terminated.
   *
   * <p>Use this to guarantee that the thread either is waiting to enter critical block held by
   * another thread, or that it is done executing.
   *
   * <p>If the timeout (as specified by {@link #STANDARD_TIMEOUT_MS}) occurs before the breakpoint
   * is hit, or the thread throws an uncaught exception, an {@link AssertionError} will be thrown.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   */
  public static void waitForBlockedOrTerminated(Thread thread) {
    waitForBlockedOrTerminated(thread, STANDARD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Waits until the thread is blocked or terminated.
   *
   * <p>Use this to guarantee that the thread either is waiting to enter critical block held by
   * another thread, or that it is done executing.
   *
   * <p>If the timeout occurs before the thread is blocked or finished, or the thread throws an
   * uncaught exception, an {@link AssertionError} will be thrown.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   * @param timeout A duration.
   * @param timeUnit The time unit of the given timeout.
   */
  public static void waitForBlockedOrTerminated(Thread thread, long timeout, TimeUnit timeUnit) {
    if (thread.getState() == Thread.State.NEW) {
      throw new IllegalArgumentException("Cannot wait for unstarted thread");
    }

    Thread.State state;

    final long endTime = System.nanoTime() + timeUnit.toNanos(timeout);
    do {
      // TODO: Handle the case where the thread hits a breakpoint. Should they be disabled while
      // waiting?
      if (isSuspended(thread)) {
        throw new IllegalArgumentException("Cannot wait while " + thread + " is suspended");
      }

      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      state = thread.getState();
      if (System.nanoTime() >= endTime) {
        break;
      }
    } while (state != Thread.State.BLOCKED
        && state != Thread.State.TERMINATED
        && state != Thread.State.WAITING);

    if (state != Thread.State.BLOCKED
        && state != Thread.State.TERMINATED
        && state != Thread.State.WAITING) {
      throw new AssertionError(
          String.format(
              Locale.US,
              "Thread state (%s) is not %s nor %s nor %s after %d ms",
              state,
              Thread.State.BLOCKED,
              Thread.State.TERMINATED,
              Thread.State.WAITING,
              timeUnit.toMillis(timeout)));
    }

    if (thread.getState() == Thread.State.TERMINATED) {
      final Optional<Throwable> uncaughtThrowable = getUncaughtThrowable(thread);
      if (uncaughtThrowable.isPresent()) {
        throw new AssertionError(thread + " threw an uncaught exception", uncaughtThrowable.get());
      }
    }
  }

  /**
   * Resume execution after being suspended at a breakpoint.
   *
   * <p>It is considered an error to resume a thread if it is not suspended.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   */
  public static void resume(Thread thread) {
    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());
    final ThreadData threadData = driverData.getThreadRegistry().get(thread);
    synchronized (threadData) {
      if (threadData.getSuspended() != null) {
        threadData.setSuspended(null);
        threadData.semaphore.release();
      } else {
        throw new AssertionError("Thread is not suspended");
      }
    }
  }

  /**
   * Waits for a thread to finish executing.
   *
   * <p>All its breakpoints will be disabled and if it is currently suspended, it will be
   * automatically resumed.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   */
  public static void join(final Thread thread) {
    join(thread, STANDARD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  /**
   * Waits for a thread to finish executing.
   *
   * <p>All its breakpoints will be disabled and if it is currently suspended, it will be
   * automatically resumed.
   *
   * @param thread A thread, registered or unregistered, different from the driver thread.
   * @param timeout A duration.
   * @param timeUnit The time unit of the given timeout.
   */
  public static void join(final Thread thread, long timeout, TimeUnit timeUnit) {
    checkRegistered(thread);

    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());
    final Map<String, Set<Thread>> enabledBreakpoints = driverData.getEnabledBreakpoints();
    synchronized (enabledBreakpoints) {
      enabledBreakpoints.forEach((id, threads) -> threads.remove(thread));
    }

    if (isSuspended(thread)) {
      resume(thread);
    }

    try {
      thread.join(timeUnit.toMillis(timeout));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    if (thread.isAlive()) {
      throw new AssertionError(thread.getName() + " is still alive");
    }

    final Optional<Throwable> uncaughtThrowable = getUncaughtThrowable(thread);
    if (uncaughtThrowable.isPresent()) {
      throw new AssertionError(thread + " threw an uncaught exception", uncaughtThrowable.get());
    }
  }

  /**
   * Use this to clean up known resources.
   *
   * <p>This method should be called from the driver thread.
   */
  public static void cleanUp() {
    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());

    if (driverData == null) {
      // Maybe the thread wasn't used for concurrency testing in a given testcase
      return;
    }

    driverData.getEnabledBreakpoints().clear();

    // MAYBE: Log a warning that a thread was suspended while tearing down
    driverData.getThreadRegistry().entrySet().stream()
        .filter(e -> e.getValue().getSuspended() != null)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet())
        .forEach(ConTesterDriver::resume);

    driverData.getThreadRegistry().clear();

    DRIVER_REGISTRY.remove(Thread.currentThread());
  }

  /**
   * Gets any uncaught {@link Throwable} from a registered and terminated {@link Thread}.
   *
   * @param thread A registered thread, different from the driver thread.
   * @return The uncaught throwable or null if none was thrown.
   */
  public static Optional<Throwable> getUncaughtThrowable(final Thread thread) {
    checkRegistered(thread);

    if (thread.isAlive()) {
      throw new IllegalStateException(thread + " is alive, must be terminated");
    }

    return requireNonNull(findThreadData(thread).orElse(null)).getUncaughtThrowable();
  }

  /**
   * Called from production code.
   *
   * @param id A breakpoint ID.
   */
  static void visitBreakpoint(String id) {
    visitBreakpoint(id, () -> true);
  }

  /**
   * Called from production code. It only suspends if the given condition evaluates to {@code true}.
   */
  static void visitBreakpoint(String id, BooleanSupplier condition) {
    final Optional<DriverData> driverData = findDriverData(Thread.currentThread());

    if (!driverData.isPresent()) {
      // The thread is unknown, maybe because it's not a tested one
      return;
    }

    final Map<String, Set<Thread>> enabledBreakpoints = driverData.get().getEnabledBreakpoints();
    final ThreadData threadData = driverData.get().getThreadRegistry().get(Thread.currentThread());
    final boolean suspend;
    synchronized (enabledBreakpoints) {
      final Set<Thread> set = enabledBreakpoints.get(id);
      if (set != null && set.contains(Thread.currentThread()) && condition.getAsBoolean()) {
        synchronized (threadData) {
          suspend = true;
          threadData.setSuspended(id);
          // This can only be mutation tested by injecting a custom wait time in waitForBreakpoint
          threadData.notifyAll();
        }
      } else {
        suspend = false;
      }
    }
    if (suspend) {
      try {
        threadData.semaphore.acquire();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static DriverData getOrCreateDriverData() {
    synchronized (DRIVER_REGISTRY) {
      return DRIVER_REGISTRY.computeIfAbsent(Thread.currentThread(), t -> new DriverData());
    }
  }

  /** Locates the ThreadData associated with the given Thread, if any exists. */
  private static Optional<ThreadData> findThreadData(Thread thread) {
    if (DRIVER_REGISTRY.isEmpty()) {
      return Optional.empty();
    }

    synchronized (DRIVER_REGISTRY) {
      return DRIVER_REGISTRY.values().stream()
          .map(driverData -> driverData.getThreadRegistry().get(thread))
          .filter(Objects::nonNull)
          .findFirst();
    }
  }

  /** Locates the {@link DriverData} associated with the given Thread, if any exists. */
  private static Optional<DriverData> findDriverData(Thread thread) {
    if (DRIVER_REGISTRY.isEmpty()) {
      return Optional.empty();
    }

    synchronized (DRIVER_REGISTRY) {
      return DRIVER_REGISTRY.entrySet().stream()
          .filter(entry -> entry.getValue().getThreadRegistry().containsKey(thread))
          .findFirst()
          .map(Map.Entry::getValue);
    }
  }

  private static boolean isRegistered(final Thread thread) {
    return findThreadData(thread).isPresent();
  }

  /**
   * Associates metadata with a given thread that tracks its interactions with breakpoints, and any
   * uncaught throwable.
   */
  private static void registerIfNecessary(final Thread thread) {
    if (!isRegistered(thread)) {
      register(thread);
    }
  }

  private static void checkRegistered(final Thread thread) {
    if (!isRegistered(thread)) {
      throw new IllegalArgumentException("The thread " + thread + " is not registered");
    }
  }

  private static void startIfNecessary(final Thread thread) {
    if (thread.getState() == Thread.State.NEW) {
      start(thread);
    }
  }

  private static boolean isSuspended(Thread thread) {
    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());
    final ThreadData threadData = driverData.getThreadRegistry().get(thread);
    synchronized (threadData) {
      return threadData.getSuspended() != null;
    }
  }

  private static void resumeIfNecessary(final Thread thread) {
    if (isSuspended(thread)) {
      resume(thread);
    }
  }

  private static Set<String> getEnabledBreakpoints(final Thread thread) {
    final DriverData driverData = DRIVER_REGISTRY.get(Thread.currentThread());
    return driverData.getEnabledBreakpoints().entrySet().stream()
        .filter(entry -> entry.getValue().contains(thread))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /** Holds the data associated with a given driver thread, e.g. a test worker thread. */
  private static class DriverData {

    private final Map<Thread, ThreadData> threadRegistry = new WeakHashMap<>();
    private final Map<String, Set<Thread>> enabledBreakpoints = new HashMap<>(4);
    private final AtomicInteger threadIdGenerator = new AtomicInteger(1);

    private int getNextThreadId() {
      return threadIdGenerator.getAndIncrement();
    }

    private Map<Thread, ThreadData> getThreadRegistry() {
      return threadRegistry;
    }

    private Map<String, Set<Thread>> getEnabledBreakpoints() {
      return enabledBreakpoints;
    }
  }

  private static class ThreadData {

    private Throwable uncaughtThrowable;
    private String breakpointId;
    private final Semaphore semaphore = new Semaphore(0);

    public Optional<Throwable> getUncaughtThrowable() {
      return Optional.ofNullable(uncaughtThrowable);
    }

    public void setUncaughtThrowable(Throwable throwable) {
      this.uncaughtThrowable = throwable;
    }

    public void setSuspended(String breakpointId) {
      this.breakpointId = breakpointId;
    }

    public String getSuspended() {
      return this.breakpointId;
    }
  }
}
