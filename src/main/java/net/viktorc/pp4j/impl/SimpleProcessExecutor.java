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

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.Submission;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface that allows for the running and management of a process
 * based on a {@link net.viktorc.pp4j.api.ProcessManager} instance to enable the execution of submissions in this process. The process may
 * be launched by invoking the {@link #start()} method and submissions may be executed in the process using the {@link #execute(Submission)}
 * method.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessExecutor extends AbstractProcessExecutor implements AutoCloseable {

  private final Semaphore startupSemaphore;

  /**
   * Constructs a process executor instance using the argument to manage the life-cycle of the process.
   *
   * @param manager The manager of the underlying process.
   */
  public SimpleProcessExecutor(ProcessManager manager) {
    super(manager, Executors.newCachedThreadPool(), false);
    startupSemaphore = new Semaphore(0);
  }

  /**
   * It launches the underlying process and blocks until it is ready for the execution of submissions.
   *
   * @throws InterruptedException If the thread is interrupted while waiting for the startup to complete.
   * @throws IllegalStateException If the process is already running.
   */
  public void start() throws InterruptedException {
    if (runLock.tryLock()) {
      try {
        threadPool.submit(this);
      } finally {
        runLock.unlock();
      }
      startupSemaphore.acquire();
    } else {
      throw new IllegalStateException("The executor is already running.");
    }
  }

  /**
   * It prompts the currently running process, if there is one, to terminate. Once the process has successfully terminated, subsequent calls
   * are ignored and return true.
   *
   * @param forcibly Whether the process should be killed forcibly or using the {@link net.viktorc.pp4j.api.ProcessManager#terminateGracefully)}
   * method of the
   * <code>ProcessManager</code> instance assigned to the executor. The latter might be ineffective if
   * the process is currently executing a submission or has not started up yet.
   * @return Whether the process was successfully terminated.
   */
  public boolean stop(boolean forcibly) {
    return super.stop(forcibly);
  }

  /**
   * Returns whether the underlying process is currently running.
   *
   * @return Whether the process is running at the moment.
   */
  public boolean isRunning() {
    if (runLock.tryLock()) {
      try {
        return false;
      } finally {
        runLock.unlock();
      }
    } else {
      return true;
    }
  }

  /**
   * It blocks the calling thread until the underlying process terminates. If the process is not running, it immediately returns.
   *
   * @throws InterruptedException If the thread has been interrupted while waiting for the process to terminate.
   */
  public void join() throws InterruptedException {
    try {
      runLock.lockInterruptibly();
    } finally {
      runLock.unlock();
    }
  }

  @Override
  public boolean execute(Submission<?> submission) {
    submissionLock.lock();
    try {
      return super.execute(submission);
    } finally {
      submissionLock.unlock();
    }
  }

  @Override
  protected void onExecutorStartup(boolean orderly) {
    startupSemaphore.release();
  }

  @Override
  protected void onExecutorTermination() {
  }

  @Override
  public void close() throws Exception {
    stop(true);
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
  }

}
