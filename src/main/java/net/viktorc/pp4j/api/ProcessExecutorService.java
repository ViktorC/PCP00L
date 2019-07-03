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

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * An interface that outlines an executing mechanism for {@link net.viktorc.pp4j.api.Submission} instances in separate processes and allows
 * for the tracking of the progress of the execution via {@link java.util.concurrent.Future} instances. It also defines the same
 * shutdown-related methods as the {@link java.util.concurrent.ExecutorService} interface to conform to its behaviour specifications.
 *
 * @author Viktor Csomor
 */
public interface ProcessExecutorService extends ProcessExecutor {

  /**
   * Returns the {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance responsible for creating instances of an implementation of the
   * {@link net.viktorc.pp4j.api.ProcessManager} interface for managing the processes of the pool.
   *
   * @return The process manager factory of the process pool.
   */
  ProcessManagerFactory getProcessManagerFactory();

  /**
   * Submits the specified submission for execution and returns a {@link java.util.concurrent.Future} instance which allows for the
   * cancellation of the submission. It does not block until the submission is processed. The termination of the executing process after the
   * execution may be requested.
   *
   * @param submission The submission to execute.
   * @param terminateProcessAfterwards Whether the process to which the submission is delegated should be terminated after the execution of
   * the submission.
   * @param <T> The type variable of the submission.
   * @return A {@link java.util.concurrent.Future} instance that allows for the waiting for the completion of the execution, the
   * cancellation thereof, or the retrieval of its optional result.
   */
  <T> Future<T> submit(Submission<T> submission, boolean terminateProcessAfterwards);

  /**
   * Initiates the orderly shutdown of the process pool. It does not affect the execution of previously submitted tasks. See {@link
   * java.util.concurrent.ExecutorService#shutdown()}.
   */
  void shutdown();

  /**
   * Kills all the and returns a list of the submissions that have been submitted but never processed. It does not block until the processes
   * terminate. See {@link java.util.concurrent.ExecutorService#shutdownNow()}.
   *
   * @return A list of the submissions that were awaiting execution.
   */
  List<Submission<?>> forceShutdown();

  /**
   * Returns whether the shutdown of the pool has been initiated. See {@link java.util.concurrent.ExecutorService#isShutdown()}.
   *
   * @return Whether the shutdown of the pool has been initiated.
   */
  boolean isShutdown();

  /**
   * Returns whether the process pool has successfully been shut down with all its processes terminated. See {@link
   * java.util.concurrent.ExecutorService#isTerminated()}.
   *
   * @return Whether all the processes of the pool have terminated.
   */
  boolean isTerminated();

  /**
   * It blocks until the {@link #isTerminated()} method returns true or a timeout occurs. See {@link
   * java.util.concurrent.ExecutorService#awaitTermination(long, TimeUnit)}.
   *
   * @param timeout The amount of time to wait for the pool's termination.
   * @param unit The unit of the amount.
   * @return Whether the pool has successfully terminated or a timeout occurred.
   * @throws InterruptedException If the thread is interrupted while the method is blocking.
   */
  boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

  /**
   * Submits the specified submission for execution and returns a {@link java.util.concurrent.Future} instance which allows for the
   * cancellation of the submission. It does not block until the submission is processed. It is equivalent to calling {@link
   * #submit(Submission, boolean)} with <code>terminateProcessAfterwards</code> set to <code>false</code>.
   *
   * @param submission The submission to execute.
   * @param <T> The type variable of the submission.
   * @return A {@link java.util.concurrent.Future} instance that allows for the waiting for the completion of the execution, the
   * cancellation thereof, or the retrieval of its optional result.
   */
  default <T> Future<T> submit(Submission<T> submission) {
    return submit(submission, false);
  }

}
