package com.github.davidburstrom.contester;

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
      Class.forName("com.github.davidburstrom.contester.ConTesterDriver");
      isDriverPresent = true;
    } catch (ClassNotFoundException ignored) {
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
