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
package io.github.davidburstrom.contester.benchmarks.withdriver;

import io.github.davidburstrom.contester.ConTesterBreakpoint;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
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

  @Measurement(iterations = 1)
  @Fork(value = 1, warmups = 1)
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  public void withBreakpointSingleShot(Blackhole blackhole) {
    ConTesterBreakpoint.defineBreakpoint("id");
    blackhole.consume(1);
  }

  @Measurement(iterations = 1)
  @Fork(value = 1, warmups = 1)
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  public void withConditionalBreakpointSingleShot(Blackhole blackhole) {
    ConTesterBreakpoint.defineBreakpoint("id", () -> true);
    blackhole.consume(1);
  }

  @Measurement(iterations = 1)
  @Fork(value = 1, warmups = 1)
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  public void withoutBreakpointSingleShot(Blackhole blackhole) {
    blackhole.consume(1);
  }
}
