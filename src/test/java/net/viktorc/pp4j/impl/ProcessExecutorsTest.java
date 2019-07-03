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

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import net.viktorc.pp4j.api.JavaProcessExecutorService;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessExecutorService;
import org.junit.Assert;
import org.junit.Test;

/**
 * A simple test class for the {@link net.viktorc.pp4j.impl.ProcessExecutors} class.
 *
 * @author Viktor Csomor
 */
public class ProcessExecutorsTest {

  /**
   * Tests if the specified properties match those of the provided process executor.
   *
   * @param executor The process pool to check.
   * @param minSize The expected minimum size of the pool.
   * @param maxSize The expected maximum size of the pool.
   * @param reserveSize The expected reserve size of the pool.
   * @param verbose The expected verbosity of the pool.
   * @return Whether the specified values match those of the properties of the pool.
   */
  private boolean test(ProcessExecutor executor, int minSize, int maxSize, int reserveSize,
      boolean verbose) {
    if (executor instanceof ProcessPoolExecutor) {
      ProcessPoolExecutor stdPool = (ProcessPoolExecutor) executor;
      return stdPool.getMinSize() == minSize && stdPool.getMaxSize() == maxSize &&
          stdPool.getReserveSize() == reserveSize && stdPool.isVerbose() == verbose;
    }
    return false;
  }

  @Test
  public void test01() throws InterruptedException, URISyntaxException {
    ProcessExecutorService pool = ProcessExecutors.newCustomProcessPool(TestUtils
        .createTestProcessManagerFactory(), 0, 5, 2);
    try {
      Assert.assertTrue(test(pool, 0, 5, 2, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test02() throws InterruptedException, URISyntaxException {
    ProcessExecutorService pool = ProcessExecutors.newFixedProcessPool(TestUtils
        .createTestProcessManagerFactory(), 5);
    try {
      Assert.assertTrue(test(pool, 5, 5, 0, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test03() throws InterruptedException, URISyntaxException {
    ProcessExecutorService pool = ProcessExecutors.newCachedProcessPool(TestUtils
        .createTestProcessManagerFactory());
    try {
      Assert.assertTrue(test(pool, 0, Integer.MAX_VALUE, 0, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test04() throws InterruptedException, URISyntaxException {
    ProcessExecutorService pool = ProcessExecutors.newSingleProcessPool(TestUtils
        .createTestProcessManagerFactory());
    try {
      Assert.assertTrue(test(pool, 1, 1, 0, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test05() throws InterruptedException {
    JavaProcessExecutorService pool = ProcessExecutors.newCustomJavaProcessPool(0, 5, 2);
    try {
      Assert.assertTrue(test(pool, 0, 5, 2, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test06() throws InterruptedException {
    JavaProcessExecutorService pool = ProcessExecutors.newFixedJavaProcessPool(5);
    try {
      Assert.assertTrue(test(pool, 5, 5, 0, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test07() throws InterruptedException {
    JavaProcessExecutorService pool = ProcessExecutors.newCachedJavaProcessPool();
    try {
      Assert.assertTrue(test(pool, 0, Integer.MAX_VALUE, 0, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  @Test
  public void test08() throws InterruptedException {
    JavaProcessExecutorService pool = ProcessExecutors.newSingleJavaProcessPool();
    try {
      Assert.assertTrue(test(pool, 1, 1, 0, false));
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

}
