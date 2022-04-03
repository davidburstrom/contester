package io.github.davidburstrom.contester.benchmarks.withdriver;

import io.github.davidburstrom.contester.ConTesterBreakpoint;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

public class WithDriverBenchmark {

  @Warmup(iterations = 1)
  @Measurement(iterations = 1)
  @Fork(value = 1, warmups = 1)
  @Benchmark
  public void withBreakpoint(Blackhole blackhole) {
    ConTesterBreakpoint.defineBreakpoint("id");
    blackhole.consume(1);
  }

  @Warmup(iterations = 1)
  @Measurement(iterations = 1)
  @Fork(value = 1, warmups = 1)
  @Benchmark
  public void withConditionalBreakpoint(Blackhole blackhole) {
    ConTesterBreakpoint.defineBreakpoint("id", () -> true);
    blackhole.consume(1);
  }

  @Warmup(iterations = 1)
  @Measurement(iterations = 1)
  @Fork(value = 1, warmups = 1)
  @Benchmark
  public void withoutBreakpoint(Blackhole blackhole) {
    blackhole.consume(1);
  }
}
