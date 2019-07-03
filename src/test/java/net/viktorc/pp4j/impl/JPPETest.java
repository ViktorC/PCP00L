/*
 * Copyright 2017 Viktor Csomor
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
package net.viktorc.pp4j.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import net.viktorc.pp4j.api.JavaProcessOptions;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * A test class for the Java process based process pool executor implementation.
 *
 * @author Viktor Csomor
 */
public class JPPETest {

  @Rule
  public final ExpectedException exceptionRule = ExpectedException.none();

  // Startup testing
  @Test
  public void test01() throws InterruptedException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 1);
    long start = System.currentTimeMillis();
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 1, 1, 0, null, false);
    try {
      long time = System.currentTimeMillis() - start;
      boolean success = time < 1000;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test02() throws InterruptedException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 2);
    long start = System.currentTimeMillis();
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
        1, 1, 0, null, false);
    try {
      long time = System.currentTimeMillis() - start;
      boolean success = time < 500;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test03() throws InterruptedException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 3);
    long start = System.currentTimeMillis();
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.SERVER, 256, 4096, 4096,
            5000), 1, 1, 0, null, false);
    try {
      long time = System.currentTimeMillis() - start;
      boolean success = time < 500;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test04() throws InterruptedException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 4);
    long start = System.currentTimeMillis();
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 10, 15, 5, null, false);
    try {
      long time = System.currentTimeMillis() - start;
      boolean success = time < 2000;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test05() throws InterruptedException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 5);
    long start = System.currentTimeMillis();
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
        10, 15, 5, null, false);
    try {
      long time = System.currentTimeMillis() - start;
      boolean success = time < 2000;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test06() throws InterruptedException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 6);
    long start = System.currentTimeMillis();
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.SERVER, 256, 4096, 4096,
            5000), 10, 15, 5, null, false);
    try {
      long time = System.currentTimeMillis() - start;
      boolean success = time < 2000;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Submission testing.
  @Test
  public void test07() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 7");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 5, 5, 0, null, false);
    try {
      List<Future<?>> futures = new ArrayList<>();
      AtomicInteger j = new AtomicInteger(2);
      for (int i = 0; i < 5; i++) {
        futures.add(exec.submit((Runnable & Serializable) () -> {
          j.incrementAndGet();
          Thread t = new Thread(() -> {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            j.incrementAndGet();
          });
          t.start();
          try {
            t.join();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
      Assert.assertTrue(j.get() == 2);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test08() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 8");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
        5, 5, 0, null, false);
    try {
      List<Future<?>> futures = new ArrayList<>();
      AtomicInteger j = new AtomicInteger(2);
      for (int i = 0; i < 5; i++) {
        futures.add(exec.submit((Runnable & Serializable) () -> {
          j.incrementAndGet();
          Thread t = new Thread(() -> {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            j.incrementAndGet();
          });
          t.start();
          try {
            t.join();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }, j));
      }
      for (Future<?> f : futures) {
        Assert.assertTrue(((AtomicInteger) f.get()).get() == 4);
      }
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test09() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 9");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 1, 1, 0, null, false);
    int base = 13;
    try {
      Assert.assertTrue(exec.submit((Callable<Integer> & Serializable) () -> 4 * base)
          .get() == 52);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Synchronous execution testing.
  @Test
  public void test10() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 10);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      long start = System.currentTimeMillis();
      exec.execute((Runnable & Serializable) () -> {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });
      long time = System.currentTimeMillis() - start;
      boolean success = time < 5300 && time > 4995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Invocation testing.
  @SuppressWarnings("unchecked")
  @Test
  public void test11() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 11);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      int base = 13;
      List<Future<Integer>> results = new ArrayList<>();
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      long start = System.currentTimeMillis();
      results = exec.invokeAll(tasks);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 4300 && time > 3995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(results.get(0).get() == 169);
      Assert.assertTrue(results.get(1).get() == 2197);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test12() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 12);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 1, 1, 0, null, false);
    try {
      int base = 13;
      List<Future<Integer>> results = new ArrayList<>();
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      long start = System.currentTimeMillis();
      results = exec.invokeAll(tasks);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 6300 && time > 5995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(results.get(0).get() == 169);
      Assert.assertTrue(results.get(1).get() == 2197);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test13() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 13);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      int base = 13;
      List<Future<Integer>> results = new ArrayList<>();
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      long start = System.currentTimeMillis();
      results = exec.invokeAll(tasks, 3000, TimeUnit.MILLISECONDS);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 3300 && time > 2995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(results.get(0).get() == 169);
      exceptionRule.expect(CancellationException.class);
      results.get(1).get();
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test14() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 14);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 1, 1, 0, null, false);
    try {
      int base = 13;
      List<Future<Integer>> results = new ArrayList<>();
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      long start = System.currentTimeMillis();
      results = exec.invokeAll(tasks, 3000, TimeUnit.MILLISECONDS);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 3300 && time > 2995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(results.get(0).get() == 169);
      exceptionRule.expect(CancellationException.class);
      results.get(1).get();
    } finally {
      exec.shutdownNow();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test15() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 15);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      int base = 13;
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        throw new RuntimeException();
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      long start = System.currentTimeMillis();
      int result = exec.invokeAny(tasks);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 4300 && time > 3995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(result == 169 || result == 2197);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test16() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 16);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      int base = 13;
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        throw new Exception();
      });
      long start = System.currentTimeMillis();
      int result = exec.invokeAny(tasks);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 2300 && time > 1995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(result == 169);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test17() throws InterruptedException, ExecutionException, TimeoutException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 17);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      int base = 13;
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      long start = System.currentTimeMillis();
      int result = exec.invokeAny(tasks, 3000, TimeUnit.MILLISECONDS);
      long time = System.currentTimeMillis() - start;
      boolean success = time < 3300 && time > 2995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(result == 169);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test18() throws InterruptedException, ExecutionException, TimeoutException {
    System.out.println(System.lineSeparator() + "Test 18");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 2, 2, 0, null, false);
    try {
      int base = 13;
      List<Callable<Integer>> tasks = new ArrayList<>();
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(2000);
        return (int) Math.pow(base, 2);
      });
      tasks.add((Callable<Integer> & Serializable) () -> {
        Thread.sleep(4000);
        return (int) Math.pow(base, 3);
      });
      exceptionRule.expect(TimeoutException.class);
      exec.invokeAny(tasks, 1000, TimeUnit.MILLISECONDS);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Test of shutdownNow.
  @Test
  public void test19() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 19);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 1, 1, 0, null, false);
    try {
      Runnable r1 = (Runnable & Serializable) () -> {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };
      Runnable r2 = (Runnable & Serializable) () -> {
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };
      exec.submit((Runnable & Serializable) () -> {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });
      exec.submit(r1);
      exec.submit(r2);
      long start = System.currentTimeMillis();
      List<Runnable> queuedTasks = exec.shutdownNow();
      Runnable a1, a2;
      if (queuedTasks.size() == 2) {
        a1 = queuedTasks.get(0);
        a2 = queuedTasks.get(1);
      } else {
        a1 = queuedTasks.get(1);
        a2 = queuedTasks.get(2);
      }
      long time = System.currentTimeMillis() - start;
      boolean success = time < 20;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(a1 == r1 && a2 == r2);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Task and result exchange performance testing.
  @SuppressWarnings("unchecked")
  @Test
  public void test20() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 20);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(new JavaProcessOptions() {
    },
        1, 1, 0, null, false);
    try {
      long start = System.currentTimeMillis();
      AtomicInteger res = exec.submit((Callable<AtomicInteger> & Serializable) () -> {
        Thread.sleep(2000);
        return new AtomicInteger(13);
      }).get();
      long time = System.currentTimeMillis() - start;
      boolean success = time < 2300 && time > 1995;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
      Assert.assertTrue(res.get() == 13);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test21() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 21);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
        30, 80, 10, null, false);
    try {
      List<Future<AtomicInteger>> results = new ArrayList<>();
      long start = System.currentTimeMillis();
      for (int i = 0; i < 50; i++) {
        Thread.sleep(50);
        results.add(exec.submit((Callable<AtomicInteger> & Serializable) () -> {
          Thread.sleep(5000);
          return new AtomicInteger();
        }));
      }
      for (Future<AtomicInteger> res : results) {
        res.get();
      }
      long time = System.currentTimeMillis() - start;
      boolean success = time < 13500 && time > 7495;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void test22() throws InterruptedException, ExecutionException {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 22);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new SimpleJavaProcessOptions(2, 4, 256, 500), 30, 80, 10, null, false);
    try {
      List<Future<AtomicInteger>> results = new ArrayList<>();
      long start = System.currentTimeMillis();
      for (int i = 0; i < 50; i++) {
        Thread.sleep(50);
        results.add(exec.submit((Callable<AtomicInteger> & Serializable) () -> {
          Thread.sleep(5000);
          return new AtomicInteger();
        }));
      }
      for (Future<AtomicInteger> res : results) {
        res.get();
      }
      long time = System.currentTimeMillis() - start;
      boolean success = time < 15000 && time > 7500;
      System.out.printf("Time: %.3f %s%n", ((double) time) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Java process options testing.
  @Test
  public void test23() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 23");
    SimpleJavaProcessOptions options = new SimpleJavaProcessOptions(0);
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(options,
        5, 5, 0, null, false);
    try {
      Assert.assertTrue(exec.getJavaProcessOptions() == options);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Java process testing.
  @SuppressWarnings("unchecked")
  @Test
  public void test24() throws IOException, InterruptedException, ClassNotFoundException {
    System.out.println(System.lineSeparator() + "Test 24");
    PrintStream origOutStream = System.out;
    String testInput = String.format("%s%n%s%n%s%n%s%n%s%n%s%n", "", Conversion.toString("test"),
        Conversion.toString((Callable<Long> & Serializable) () -> Math.round(Math.E)),
        Conversion.toString((Callable<Object> & Serializable) () -> {
          throw new Exception("test");
        }), "test", JavaProcess.STOP_REQUEST);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes())) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertTrue(lines.length == 5);
      Assert.assertTrue(JavaProcess.STARTUP_SIGNAL.equals(lines[0]));
      Assert.assertTrue(lines[1].startsWith(JavaProcess.RESULT_PREFIX));
      Assert.assertTrue((3 == (Long) Conversion.toObject(lines[1]
          .substring(JavaProcess.RESULT_PREFIX.length()))));
      Assert.assertTrue(lines[2].startsWith(JavaProcess.ERROR_PREFIX));
      Assert.assertTrue("test".equals(((Exception) Conversion.toObject(lines[2]
          .substring(JavaProcess.ERROR_PREFIX.length()))).getMessage()));
      Assert.assertTrue(lines[3].startsWith(JavaProcess.ERROR_PREFIX));
      Assert.assertTrue(JavaProcess.STOP_SIGNAL.equals(lines[4]));
    } finally {
      System.setOut(origOutStream);
    }
  }

  // Not serializable task testing.
  @Test
  public void test25() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 25");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 0, 1, 0, null, false);
    try {
      exceptionRule.expect(IllegalArgumentException.class);
      exec.submit(() -> 1);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test26() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 26");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 0, 1, 0, null, false);
    try {
      exceptionRule.expect(IllegalArgumentException.class);
      exec.submit(() -> System.gc());
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test27() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 27");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 0, 1, 0, null, false);
    try {
      exceptionRule.expect(IllegalArgumentException.class);
      AtomicInteger n = new AtomicInteger(0);
      exec.submit(() -> n.set(1), n);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  // Startup task testing.
  @Test
  public void test28() throws InterruptedException, ExecutionException {
    System.out.println(System.lineSeparator() + "Test 28");
    JavaProcessPoolExecutor exec = new JavaProcessPoolExecutor(
        new JavaProcessOptions() {
        }, 0, 1, 0, (Runnable & Serializable) () -> {
      for (int i = 0; i < 10; i++) {
        System.out.println("Doing stuff");
      }
    }, false);
    try {
      AtomicInteger n = new AtomicInteger(0);
      Assert.assertTrue(exec.submit((Runnable & Serializable) () -> n.set(1), n).get().get() == 1);
    } finally {
      exec.shutdown();
      exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

}
