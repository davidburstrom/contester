package io.github.davidburstrom.contester.examples;

import io.github.davidburstrom.contester.ConTesterBreakpoint;

/**
 * Simulates an issue where one method is modifying a field while another method is referencing it,
 * which causes spurious NullPointerExceptions.
 */
@SuppressWarnings("PMD.SystemPrintln")
public interface Modification {

  void reset();

  void print();

  class Broken implements Modification {
    private Object member = new Object();

    @Override
    public void reset() {
      ConTesterBreakpoint.defineBreakpoint("reset");
      member = null;
    }

    @Override
    public void print() {
      /* Since there is no synchronization block, member can become null within the if-clause.
       */
      if (member != null) {
        ConTesterBreakpoint.defineBreakpoint("print");
        System.out.println(member.getClass());
      }
    }
  }

  class Fixed implements Modification {
    final Object lock = new Object();
    private Object member = new Object();

    @Override
    public void reset() {
      ConTesterBreakpoint.defineBreakpoint("reset");
      synchronized (lock) {
        member = null;
      }
    }

    @Override
    public void print() {
      /* this synchronized block is introduced during a bug fix, and is proven to work */
      synchronized (lock) {
        if (member != null) {
          ConTesterBreakpoint.defineBreakpoint("print");
          System.out.println(member.getClass());
        }
      }
    }
  }
}
