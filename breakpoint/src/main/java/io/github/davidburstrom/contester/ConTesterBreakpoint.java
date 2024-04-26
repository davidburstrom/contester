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
package io.github.davidburstrom.contester;

import java.util.function.BooleanSupplier;

/**
 * API to define breakpoints in production code, to be referenced from automated tests.
 *
 * <p>See {@link ConTesterDriver} for instructions on how to set up test cases.
 */
public class ConTesterBreakpoint {

  private static final boolean IS_DRIVER_PRESENT;

  static {
    boolean isDriverPresent = false;
    try {
      Class.forName("io.github.davidburstrom.contester.ConTesterDriver");
      isDriverPresent = true;
    } catch (ClassNotFoundException ignored) {
      // the driver wasn't found
    }
    IS_DRIVER_PRESENT = isDriverPresent;
  }

  /**
   * Defines a breakpoint with a given ID.
   *
   * @param id A breakpoint ID. It is recommended but not mandatory that the ID is unique, for
   *     example based on the containing class or method.
   */
  public static void defineBreakpoint(String id) {
    if (IS_DRIVER_PRESENT) {
      ConTesterDriver.visitBreakpoint(id);
    }
  }

  /**
   * Defines a breakpoint with a given ID, and a given condition for the breakpoint to hit.
   *
   * @param id A breakpoint ID. It is recommended but not mandatory that the ID is unique, for
   *     example based on the containing class or method.
   * @param condition Will be evaluated every time an enabled breakpoint is reached, and if it
   *     returns {@code true}, the executing {@link Thread} will be suspended.
   */
  public static void defineBreakpoint(String id, BooleanSupplier condition) {
    if (IS_DRIVER_PRESENT) {
      ConTesterDriver.visitBreakpoint(id, condition);
    }
  }
}
