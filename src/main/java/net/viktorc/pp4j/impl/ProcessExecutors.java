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

import net.viktorc.pp4j.api.JavaProcessExecutorService;
import net.viktorc.pp4j.api.JavaProcessOptions;
import net.viktorc.pp4j.api.ProcessExecutorService;
import net.viktorc.pp4j.api.ProcessManagerFactory;

/**
 * A class for convenience and factory methods for creating instances of implementations of the {@link
 * net.viktorc.pp4j.api.ProcessExecutorService} interface.
 *
 * @author Viktor Csomor
 */
public class ProcessExecutors {

  /**
   * Not initializable; only static methods...
   */
  private ProcessExecutors() {
  }

  /**
   * Returns a pool of processes. The initial size of the pool is the greater of the minimum pool size and the reserve size. This method
   * blocks until the initial number of processes started up. The size of the pool is dynamically adjusted based on the pool parameters and
   * the rate of incoming submissions. It is a convenience method for the constructor {@link net.viktorc.pp4j.impl.ProcessPoolExecutor#ProcessPoolExecutor(ProcessManagerFactory,
   * int, int, int, boolean)} with <code>verbose</code> set to <code>false</code>.
   *
   * @param managerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to build {@link
   * net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the pool.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @return A pool of process executors each hosting a process.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the processes to start up.
   */
  public static ProcessExecutorService newCustomProcessPool(ProcessManagerFactory managerFactory, int minPoolSize,
      int maxPoolSize, int reserveSize) throws InterruptedException {
    return new ProcessPoolExecutor(managerFactory, minPoolSize, maxPoolSize, reserveSize, false);
  }

  /**
   * Returns a pool of a fixed number of processes. It is a convenience method for calling {@link
   * #newCustomProcessPool(ProcessManagerFactory, int, int, int)} with <code>minPoolSize</code> and <code>maxPoolSize</code> equal and a
   * <code>reserveSize</code> of <code>0</code>. The number of executors in the pool is always kept at the specified value.
   *
   * @param managerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to build {@link
   * net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the pool.
   * @param size The number of processes to maintain in the pool.
   * @return A fixed size pool of process executors.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the processes to start up.
   */
  public static ProcessExecutorService newFixedProcessPool(ProcessManagerFactory managerFactory, int size)
      throws InterruptedException {
    return newCustomProcessPool(managerFactory, size, size, 0);
  }

  /**
   * Returns a pool of processes that grows in size as required. It is a convenience method for calling {@link
   * #newCustomProcessPool(ProcessManagerFactory, int, int, int)} with <code>0</code> as the <code>minPoolSize</code> and the
   * <code>reserveSize</code>, and <code>Integer.MAX_VALUE</code> as the maximum pool size.
   *
   * @param managerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to build {@link
   * net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the pool.
   * @return An unbounded pool of process executors.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the processes to start up.
   */
  public static ProcessExecutorService newCachedProcessPool(ProcessManagerFactory managerFactory)
      throws InterruptedException {
    return newCustomProcessPool(managerFactory, 0, Integer.MAX_VALUE, 0);
  }

  /**
   * Returns a fixed size pool holding a single process. It is a convenience method for calling the method {@link
   * #newFixedProcessPool(ProcessManagerFactory, int)} with <code>size</code> set to <code>1</code>.
   *
   * @param managerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to build {@link
   * net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the pool.
   * @return A pool holding a single process executor.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the processes to start up.
   */
  public static ProcessExecutorService newSingleProcessPool(ProcessManagerFactory managerFactory)
      throws InterruptedException {
    return newFixedProcessPool(managerFactory, 1);
  }

  /**
   * Returns a custom process pool using Java processes. The initial size of the pool is the greater of the minimum pool size and the
   * reserve size. This method blocks until the initial number of processes started up. The size of the pool is dynamically adjusted based
   * on the pool parameters and the rate of incoming submissions. It is a convenience method for the constructor {@link
   * net.viktorc.pp4j.impl.JavaProcessPoolExecutor#JavaProcessPoolExecutor(JavaProcessOptions, int, int, int, Runnable, boolean)} with
   * <code>startupTask</code> set to <code>null</code> and <code>verbose</code> set to <code>false</code>.
   *
   * @param options The options for the "java" program used to create the new JVM.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @return A custom pool of Java processes.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the maximum pool size is
   * less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public static JavaProcessExecutorService newCustomJavaProcessPool(JavaProcessOptions options, int minPoolSize, int maxPoolSize,
      int reserveSize) throws InterruptedException {
    return new JavaProcessPoolExecutor(options, minPoolSize, maxPoolSize, reserveSize, null, false);
  }

  /**
   * Returns a custom process pool using Java processes. The initial size of the pool is the greater of the minimum pool size and the
   * reserve size. This method blocks until the initial number of processes started up. The size of the pool is dynamically adjusted based
   * on the pool parameters and the rate of incoming submissions. It is a convenience method for {@link
   * #newCustomJavaProcessPool(JavaProcessOptions, int, int, int)} with <code>options</code> set to <code>null</code>.
   *
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @return A custom pool of Java processes.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If the minimum pool size is less than 0, or the maximum pool size is less than the minimum pool size
   * or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public static JavaProcessExecutorService newCustomJavaProcessPool(int minPoolSize, int maxPoolSize, int reserveSize)
      throws InterruptedException {
    return newCustomJavaProcessPool(new JavaProcessOptions() {
    }, minPoolSize, maxPoolSize, reserveSize);
  }

  /**
   * Returns a pool of a fixed number of Java processes. It is a convenience method for calling {@link
   * #newCustomJavaProcessPool(JavaProcessOptions, int, int, int)} with <code>minPoolSize</code> and
   * <code>maxPoolSize</code> set to the value of <code>size</code> and <code>reserveSize</code> set to
   * <code>0</code>. The number of executors in the pool is always kept at the specified value.
   *
   * @param options The options for the "java" program used to create the new JVM.
   * @param size The number of processes to maintain in the pool.
   * @return A fixed-sized pool of Java processes.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  public static JavaProcessExecutorService newFixedJavaProcessPool(JavaProcessOptions options, int size)
      throws InterruptedException {
    return newCustomJavaProcessPool(options, size, size, 0);
  }

  /**
   * Returns a pool of a fixed number of Java processes. It is a convenience method for calling {@link
   * #newFixedJavaProcessPool(JavaProcessOptions, int)} with <code>options</code> set to
   * <code>null</code>. The number of executors in the pool is always kept at the specified value.
   *
   * @param size The number of processes to maintain in the pool.
   * @return A fixed-sized pool of Java processes.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  public static JavaProcessExecutorService newFixedJavaProcessPool(int size) throws InterruptedException {
    return newFixedJavaProcessPool(new JavaProcessOptions() {
    }, size);
  }

  /**
   * Returns a pool of Java processes that grows in size as required.. It is a convenience method for calling {@link
   * #newCustomJavaProcessPool(JavaProcessOptions, int, int, int)} with <code>minPoolSize
   * </code> set to <code>0</code>, <code>maxPoolSize</code> set to <code>Integer.MAX_VALUE</code> and
   * <code>reserveSize</code> set to <code>0</code>.
   *
   * @param options The options for the "java" program used to create the new JVM.
   * @return A pool of Java processes that grows in size as required.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  public static JavaProcessExecutorService newCachedJavaProcessPool(JavaProcessOptions options)
      throws InterruptedException {
    return newCustomJavaProcessPool(options, 0, Integer.MAX_VALUE, 0);
  }

  /**
   * Returns a pool of Java processes that grows in size as required.. It is a convenience method for calling {@link
   * #newCachedJavaProcessPool(JavaProcessOptions)} with <code>options</code> set to
   * <code>null</code>.
   *
   * @return A pool of Java processes that grows in size as required.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  public static JavaProcessExecutorService newCachedJavaProcessPool()
      throws InterruptedException {
    return newCachedJavaProcessPool(new JavaProcessOptions() {
    });
  }

  /**
   * Returns a fixed size pool holding a single Java process. It is a convenience method for calling the method {@link
   * #newFixedJavaProcessPool(JavaProcessOptions, int)} with <code>size</code> set to <code>1</code>.
   *
   * @param options The options for the "java" program used to create the new JVM.
   * @return A pool maintaining a single Java process executor.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  public static JavaProcessExecutorService newSingleJavaProcessPool(JavaProcessOptions options)
      throws InterruptedException {
    return newFixedJavaProcessPool(options, 1);
  }

  /**
   * Returns a fixed size pool holding a single Java process. It is a convenience method for calling the method {@link
   * #newSingleJavaProcessPool(JavaProcessOptions)} with <code>options</code> set to <code>null</code>.
   *
   * @return A pool maintaining a single Java process executor.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  public static JavaProcessExecutorService newSingleJavaProcessPool() throws InterruptedException {
    return newSingleJavaProcessPool(new JavaProcessOptions() {
    });
  }

}
