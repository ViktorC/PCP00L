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
package net.viktorc.pp4j.api;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * An interface for Java process pools that extends both the {@link net.viktorc.pp4j.api.ProcessExecutorService} and {@link
 * java.util.concurrent.ExecutorService} interfaces.
 *
 * @author Viktor Csomor
 */
public interface JavaProcessExecutorService extends ProcessExecutorService, ExecutorService {

  /**
   * See {@link #submit(Runnable)}.
   *
   * @param task The task to execute.
   * @param terminateProcessAfterwards Whether the process is to be terminated after the execution of the task.
   * @return A <code>Future</code> instance to allow for waiting for the task to be executed or to cancel it.
   */
  Future<?> submit(Runnable task, boolean terminateProcessAfterwards);

  /**
   * See {@link #submit(Runnable, Object)}.
   *
   * @param task The task to execute.
   * @param result The object representing the result of the operation.
   * @param terminateProcessAfterwards Whether the process is to be terminated after the execution of the task.
   * @param <T> The type of the result.
   * @return A <code>Future</code> instance to allow for waiting for the task to be executed, to cancel it, or to retrieve the value of the
   * result of the operation.
   */
  <T> Future<T> submit(Runnable task, T result, boolean terminateProcessAfterwards);

  /**
   * See {@link #submit(Callable)}.
   *
   * @param task The task to execute.
   * @param terminateProcessAfterwards Whether the process is to be terminated after the execution of the task.
   * @param <T> The type of the result.
   * @return A <code>Future</code> instance to allow for waiting for the task to be executed, to cancel it, or to retrieve the value of the
   * result of the operation.
   */
  <T> Future<T> submit(Callable<T> task, boolean terminateProcessAfterwards);

  @Override
  default Future<?> submit(Runnable task) {
    return submit(task, false);
  }

  @Override
  default <T> Future<T> submit(Runnable task, T result) {
    return submit(task, result, false);
  }

  @Override
  default <T> Future<T> submit(Callable<T> task) {
    return submit(task, false);
  }

}
