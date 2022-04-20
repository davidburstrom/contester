# ConTester – Concurrency Tester for JVM languages

## Overview

ConTester is tool that enables precise testing of Java concurrency, by providing granular control of
thread execution. It is quite similar to how one would use a debugger to set breakpoints in the
production code, which makes it easy to learn and apply. Thanks to JVM runtime optimizations, the
library has zero performance impact in production.

It consists of two libraries: `contester-breakpoint` and `contester-driver`. The production code
uses the former to define breakpoints, and the test code uses the latter to set up test scenarios
based on those breakpoints.

```java
    field=true;
    ConTesterBreakpoint.defineBreakpoint("my-breakpoint");
    field=false;
```

```java
    ConTesterDriver.runToBreakpoint(thread,"my-breakpoint");
    assertTrue(field);
    ConTesterDriver.join(thread);
    assertFalse(field);
```

See `examples` for more use-cases.

## Usage

1. Introduce one or more `ConTesterBreakpoint.defineBreakpoint`s in the production code, as
   required. Thinking in terms of debugging, place them before the statements that'd have debugger
   breakpoints set.
2. In the test code, use `ConTesterDriver.thread` to set up two or more `Thread`s that execute the
   production code, using the supplied `Runnable`s.
3. Use a combination of `ConTesterDriver.runToBreakpoint` and `resume` to control how the `Thread`s
   execute the production code.
4. If necessary, verify that the `Thread`s did or didn't throw any uncaught exception by using
   `ConTesterDriver.join` and `getUncaughtThrowable`.

### Gradle

Add the following dependencies to the project:

```groovy
dependencies {
    implementation("io.github.davidburstrom.contester:contester-breakpoint:0.1.0")
    testImplementation("io.github.davidburstrom.contester:contester-driver:0.1.0")
}
```

## Advanced usage

### Breakpoint IDs

The breakpoint IDs don't have to be globally unique, though it might lead to confusion if a thread
can hit two identical breakpoint IDs during execution. Therefore, it can be a good idea to namespace
them.

### Bring your own thread

If the production code requires a particular Thread class, the `ConTesterDriver.thread` method
cannot be used. In that case, call `ConTesterDriver.register` first to let ConTester control the
thread execution.

### Parallel test execution

ConTester is designed to handle parallel unit test execution, by associating all thread operations
with each individual test worker thread.

## Production performance

Even though the production code invokes `ConTesterBreakpoint.defineBreakpoint`, there is no performance
penalty, if the `ConTesterDriver` class is missing on the runtime classpath. The JVM will optimise
away the calls.

If ProGuard/R8 is used (for example in an Android development environment), it is also possible to
completely eliminate the `ConTesterBreakpoint.defineBreakpoint` invocations in the
minification/obfuscation phase. See `proguard-rules.pro`.

### Benchmarks

To verify that the production runtime is not impacted by defined breakpoints when the driver is not
present on the classpath, run the `./gradlew jmh` tasks. This tests both repeated calls to
`defineBreakpoint` as well as first time invocations.

Sample output from JDK 1.8 on an M1 Max:

```
Benchmark                                                   Mode           Score  Units
WithDriverBenchmark.withBreakpoint                          thrpt  109101587.440  ops/s
WithDriverBenchmark.withConditionalBreakpoint               thrpt  109854616.334  ops/s
WithDriverBenchmark.withoutBreakpoint                       thrpt  465717746.227  ops/s
WithDriverBenchmark.withBreakpointSingleShot                ss            ≈ 10⁻⁶  s/op
WithDriverBenchmark.withConditionalBreakpointSingleShot     ss            ≈ 10⁻⁶  s/op
WithDriverBenchmark.withoutBreakpointSingleShot             ss            ≈ 10⁻⁵  s/op
WithoutDriverBenchmark.withBreakpoint                       thrpt  457506448.191  ops/s
WithoutDriverBenchmark.withConditionalBreakpoint            thrpt  462785179.461  ops/s
WithoutDriverBenchmark.withoutBreakpoint                    thrpt  453185836.467  ops/s
WithoutDriverBenchmark.withBreakpointSingleShot             ss            ≈ 10⁻⁶  s/op
WithoutDriverBenchmark.withConditionalBreakpointSingleShot  ss            ≈ 10⁻⁶  s/op
WithoutDriverBenchmark.withoutBreakpointSingleShot          ss            ≈ 10⁻⁵  s/op
```
