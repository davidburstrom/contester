package com.github.davidburstrom.contester.examples;

import com.github.davidburstrom.contester.ConTesterBreakpoint;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

/**
 * Simulates a scenario where a producer-consumer is operating on a queue, but where the consumer
 * might run faster than the producer, causing underflow. A fix (other than switching to a {@link
 * java.util.concurrent.BlockingQueue} of course) is to signal availability with a {@link
 * Semaphore}.
 */
public interface Underflow {

  void produce();

  void consume();

  class Broken implements Underflow {
    Queue<Object> buffer = new LinkedList<>();

    @Override
    public void produce() {
      ConTesterBreakpoint.defineBreakpoint("produce");
      buffer.add(new Object());
    }

    @Override
    public void consume() {
      buffer.remove();
    }
  }

  class Fixed implements Underflow {
    Queue<Object> buffer = new LinkedList<>();
    Semaphore semaphore = new Semaphore(0);

    @Override
    public void produce() {
      ConTesterBreakpoint.defineBreakpoint("produce");
      buffer.add(new Object());
      semaphore.release();
    }

    @Override
    public void consume() {
      try {
        semaphore.acquire();
      } catch (InterruptedException ignore) {
        return;
      }
      buffer.remove();
    }
  }
}
