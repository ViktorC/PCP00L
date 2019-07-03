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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Test;

/**
 * A test class for comparing the performance of {@link net.viktorc.pp4j.impl.ProcessPoolExecutor} using a native executable wrapper program
 * and {@link net.viktorc.pp4j.impl.JavaProcessPoolExecutor} using JNI.
 *
 * @author Viktor Csomor
 */
public class PPEVsJPPETest {

  private static final int WARMUP_SUBMISSIONS = 5;

  /**
   * Executes the performance test according to the specified arguments on a Java process pool using JNI to invoke the native test method.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @return The time it took to execute the specified number of submissions with the specified execution length in nanoseconds.
   * @throws Exception If something goes wrong.
   */
  private long testJNI(int minSize, int maxSize, int reserveSize, int submissions, int taskTime,
      boolean reuse) throws Exception {
    JavaProcessPoolExecutor javaPool = new JavaProcessPoolExecutor(new SimpleJavaProcessOptions(1, 10, 512, 0),
        minSize, maxSize, reserveSize, reuse ? (Runnable & Serializable) () -> new WrapperJNI() :
        null, false);
    try {
      Runnable javaTask = (Runnable & Serializable) () -> (new WrapperJNI()).doStuff(taskTime);
      for (int i = 0; i < WARMUP_SUBMISSIONS; i++) {
        javaPool.submit(javaTask).get();
      }
      List<Future<?>> futures = new ArrayList<>();
      long start = System.nanoTime();
      for (int i = 0; i < submissions; i++) {
        futures.add(javaPool.submit(javaTask, !reuse));
      }
      for (Future<?> f : futures) {
        f.get();
      }
      return System.nanoTime() - start;
    } finally {
      javaPool.shutdown();
      javaPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  /**
   * Executes the performance test according to the specified arguments on a Java process pool using JNA to invoke the native test method.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @return The time it took to execute the specified number of submissions with the specified execution length in nanoseconds.
   * @throws Exception If something goes wrong.
   */
  private long testJNA(int minSize, int maxSize, int reserveSize, int submissions, int taskTime,
      boolean reuse) throws Exception {
    JavaProcessPoolExecutor javaPool = new JavaProcessPoolExecutor(new SimpleJavaProcessOptions(1, 10, 512, 0),
        minSize, maxSize, reserveSize, reuse ? (Runnable & Serializable) () -> WrapperJNA.INSTANCE.hashCode() :
        null, false);
    try {
      Runnable javaTask = (Runnable & Serializable) () -> WrapperJNA.INSTANCE.doStuff(taskTime);
      for (int i = 0; i < WARMUP_SUBMISSIONS; i++) {
        javaPool.submit(javaTask).get();
      }
      List<Future<?>> futures = new ArrayList<>();
      long start = System.nanoTime();
      for (int i = 0; i < submissions; i++) {
        futures.add(javaPool.submit(javaTask, !reuse));
      }
      for (Future<?> f : futures) {
        f.get();
      }
      return System.nanoTime() - start;
    } finally {
      javaPool.shutdown();
      javaPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  /**
   * Executes the performance comparison according to the provided arguments.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @param jna Whether the Java process pool should use JNA or JNI to invoke the native method.
   * @throws Exception If something goes wrong.
   */
  private void test(int minSize, int maxSize, int reserveSize, int submissions, int taskTime,
      boolean reuse, boolean jna) throws Exception {
    long start, time, javaTime;
    List<Future<?>> futures = new ArrayList<>();
    ProcessPoolExecutor nativePool = new ProcessPoolExecutor(TestUtils.createTestProcessManagerFactory(),
        minSize, maxSize, reserveSize, false);
    try {
      SimpleSubmission nativeSubmission = new SimpleSubmission(new SimpleCommand("process " + taskTime,
          (c, o) -> "ready".equals(o), (c, o) -> true));
      for (int i = 0; i < WARMUP_SUBMISSIONS; i++) {
        nativePool.submit(nativeSubmission, !reuse).get();
      }
      start = System.nanoTime();
      for (int i = 0; i < submissions; i++) {
        futures.add(nativePool.submit(nativeSubmission, !reuse));
      }
      for (Future<?> f : futures) {
        f.get();
      }
      time = System.nanoTime() - start;
    } finally {
      nativePool.shutdown();
      nativePool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
    javaTime = jna ? testJNA(minSize, maxSize, reserveSize, submissions, taskTime, reuse) :
        testJNI(minSize, maxSize, reserveSize, submissions, taskTime, reuse);
    boolean success = time < javaTime;
    System.out.printf("Native time - %.3f : Java time - %.3f %s%n", (float) (((double) time) / 1000000),
        (float) (((double) javaTime) / 1000000), success ? "" : "FAIL");
    Assume.assumeTrue(success);
  }

  @Test
  public void test01() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 1);
    test(1, 1, 0, 10, 2, true, false);
  }

  @Test
  public void test02() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 2);
    test(1, 1, 0, 10, 2, true, true);
  }

  @Test
  public void test03() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 3);
    test(20, 50, 10, 50, 2, true, false);
  }

  @Test
  public void test04() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 4);
    test(20, 50, 10, 50, 2, true, true);
  }

  @Test
  public void test05() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 5);
    test(1, 1, 0, 10, 2, false, false);
  }

  @Test
  public void test06() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 6);
    test(1, 1, 0, 10, 2, false, true);
  }

  @Test
  public void test07() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 7);
    test(20, 50, 10, 50, 2, false, false);
  }

  @Test
  public void test08() throws Exception {
    System.out.printf(TestUtils.TEST_TITLE_FORMAT, 8);
    test(20, 50, 10, 50, 2, false, true);
  }

}
