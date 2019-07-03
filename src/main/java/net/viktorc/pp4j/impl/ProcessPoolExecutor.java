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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.ProcessExecutorService;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutorService} interface for maintaining and managing a pool of pre-started
 * processes. The processes are executed in instances of an own {@link net.viktorc.pp4j.api.ProcessExecutor} implementation. Each executor
 * is assigned an instance of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface using an implementation of the
 * {@link net.viktorc.pp4j.api.ProcessManagerFactory} interface. The pool accepts submissions in the form of {@link
 * net.viktorc.pp4j.api.Submission} implementations which are executed in any one of the available active process executors maintained by
 * the pool. While executing a submission, the executor cannot accept further submissions. The submissions are queued and executed as soon
 * as there is an available executor. The size of the pool is always kept between the minimum pool size and the maximum pool size (both
 * inclusive). The reserve size specifies the minimum number of processes that should always be available (there are no guarantees that
 * there actually will be this many available executors at any given time). This class uses
 * <a href="https://www.slf4j.org/">SLF4J</a> for logging.
 *
 * @author Viktor Csomor
 */
public class ProcessPoolExecutor implements ProcessExecutorService {

  /**
   * The number of milliseconds after which idle process executor instances and process executor threads are evicted from the object pool
   * and the thread pool respectively.
   */
  protected static final long EVICT_TIME = 60L * 1000;

  private final ProcessManagerFactory procManagerFactory;
  private final int minPoolSize;
  private final int maxPoolSize;
  private final int reserveSize;
  private final boolean verbose;
  private final InternalProcessExecutorThreadPool procExecThreadPool;
  private final InternalProcessExecutorObjectPool procExecObjectPool;
  private final ExecutorService auxThreadPool;
  private final Queue<InternalProcessExecutor> activeProcExecutors;
  private final BlockingDeque<InternalSubmission<?>> submissionQueue;
  private final CountDownLatch prestartLatch;
  private final CountDownLatch poolTermLatch;
  private final Lock shutdownLock;
  private final Lock poolLock;
  private final Logger logger;
  private volatile int numOfSubmissions;
  private volatile boolean shutdown;

  /**
   * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size depending on which one is
   * greater. This constructor blocks until the initial number of processes start up. The size of the pool is dynamically adjusted based on
   * the pool parameters and the rate of incoming submissions.
   *
   * @param procManagerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to build {@link
   * net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the pool.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @param verbose Whether the events related to the management of the process pool should be logged. Setting this parameter to
   * <code>true</code> does not guarantee that logging will be performed as logging depends on the SLF4J binding and the logging
   * configurations, but setting it to <code>false</code> guarantees that no logging will be performed by the constructed instance.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the maximum pool size is
   * less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public ProcessPoolExecutor(ProcessManagerFactory procManagerFactory, int minPoolSize, int maxPoolSize,
      int reserveSize, boolean verbose) throws InterruptedException {
    if (procManagerFactory == null) {
      throw new IllegalArgumentException("The process manager factory cannot be null.");
    }
    if (minPoolSize < 0) {
      throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
    }
    if (maxPoolSize < 1 || maxPoolSize < minPoolSize) {
      throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great " +
          "as the minimum pool size.");
    }
    if (reserveSize < 0 || reserveSize > maxPoolSize) {
      throw new IllegalArgumentException("The reserve has to be at least 0 and less than the maximum pool " +
          "size.");
    }
    this.procManagerFactory = procManagerFactory;
    this.minPoolSize = minPoolSize;
    this.maxPoolSize = maxPoolSize;
    this.reserveSize = reserveSize;
    this.verbose = verbose;
    procExecThreadPool = new InternalProcessExecutorThreadPool();
    procExecObjectPool = new InternalProcessExecutorObjectPool();
    int actualMinSize = Math.max(minPoolSize, reserveSize);
    /* One normal process requires minimum 2 auxiliary threads (stdout listener, submission handler), 3 if
     * the stderr is not redirected to stdout (stderr listener), and 4 if keepAliveTime is positive (timer). */
    auxThreadPool = new ThreadPoolExecutor(2 * actualMinSize, Integer.MAX_VALUE, EVICT_TIME,
        TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
        new CustomizedThreadFactory(this + "-auxThreadPool"));
    submissionQueue = new LinkedBlockingDeque<>();
    activeProcExecutors = new LinkedBlockingQueue<>();
    prestartLatch = new CountDownLatch(actualMinSize);
    poolTermLatch = new CountDownLatch(1);
    shutdownLock = new ReentrantLock();
    poolLock = new ReentrantLock(true);
    logger = verbose ? LoggerFactory.getLogger(getClass()) : NOPLogger.NOP_LOGGER;
    for (int i = 0; i < actualMinSize && !shutdown; i++) {
      poolLock.lock();
      try {
        startNewProcess();
      } finally {
        poolLock.unlock();
      }
    }
    // Wait for the processes in the initial pool to start up.
    prestartLatch.await();
    logger.info("Pool started up.");
  }

  /**
   * Returns the minimum number of processes to hold in the pool.
   *
   * @return The minimum size of the process pool.
   */
  public int getMinSize() {
    return minPoolSize;
  }

  /**
   * Returns the maximum allowed number of processes to hold in the pool.
   *
   * @return The maximum size of the process pool.
   */
  public int getMaxSize() {
    return maxPoolSize;
  }

  /**
   * Returns the minimum number of available processes to keep in the pool.
   *
   * @return The number of available processes to keep in the pool.
   */
  public int getReserveSize() {
    return reserveSize;
  }

  /**
   * Returns whether events relating to the management of the processes held by the pool are logged to the console.
   *
   * @return Whether the pool is verbose.
   */
  public boolean isVerbose() {
    return verbose;
  }

  /**
   * Returns the number of running processes currently held in the pool.
   *
   * @return The number of running processes.
   */
  public int getNumOfProcesses() {
    return activeProcExecutors.size();
  }

  /**
   * Returns the number of submissions currently being held in the queue or executed in the pool.
   *
   * @return The number of submissions currently handled by the pool.
   */
  public int getNumOfSubmissions() {
    return numOfSubmissions;
  }

  /**
   * Returns the number of active, queued, and currently executing processes as string.
   *
   * @return A string of statistics concerning the size of the process pool.
   */
  private String getPoolStats() {
    return "Processes: " + activeProcExecutors.size() + "; submissions: " + numOfSubmissions;
  }

  /**
   * Returns whether a new {@link InternalProcessExecutor} instance should be started.
   *
   * @return Whether the process pool should be extended.
   */
  private boolean doExtendPool() {
    return !shutdown && (activeProcExecutors.size() < minPoolSize || (activeProcExecutors.size() <
        Math.min(maxPoolSize, numOfSubmissions + reserveSize)));
  }

  /**
   * Starts a new process by borrowing a {@link net.viktorc.pp4j.impl.ProcessPoolExecutor.InternalProcessExecutor} instance from the pool
   * and executing it.
   *
   * @return Whether the process was successfully started.
   */
  private boolean startNewProcess() {
    InternalProcessExecutor executor;
    try {
      executor = procExecObjectPool.borrowObject();
    } catch (Exception e) {
      return false;
    }
    procExecThreadPool.execute(executor);
    activeProcExecutors.add(executor);
    logger.debug("Process executor {} started.{}", executor, System.lineSeparator() + getPoolStats());
    return true;
  }

  /**
   * Waits until all submissions are completed and calls {@link #syncForceShutdown()}.
   */
  private void syncShutdown() {
    synchronized (submissionQueue) {
      logger.info("Waiting for submissions to complete...");
      try {
        while (numOfSubmissions > 0) {
          submissionQueue.wait();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    syncForceShutdown();
  }

  /**
   * Kills all the processes, shuts down the pool, and waits for termination.
   */
  private void syncForceShutdown() {
    shutdownLock.lock();
    try {
      poolLock.lock();
      try {
        while (prestartLatch.getCount() != 0) {
          prestartLatch.countDown();
        }
        logger.debug("Shutting down process executors...");
        for (InternalProcessExecutor executor : activeProcExecutors) {
          if (!executor.stop(true)) // This should never happen.
          {
            logger.error("Process executor {} could not be stopped.", executor);
          }
        }
        logger.debug("Shutting down thread pools...");
        auxThreadPool.shutdown();
        procExecThreadPool.shutdown();
        procExecObjectPool.close();
      } finally {
        poolLock.unlock();
      }
      try {
        auxThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        procExecThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        for (InternalSubmission<?> submission : submissionQueue) {
          submission.setException(new Exception("The process pool has been shut down."));
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      logger.info("Process pool shut down.");
      poolTermLatch.countDown();
    } finally {
      shutdownLock.unlock();
    }
  }

  @Override
  public ProcessManagerFactory getProcessManagerFactory() {
    return procManagerFactory;
  }

  @Override
  public boolean execute(Submission<?> submission) {
    try {
      submit(submission).get();
      return true;
    } catch (ExecutionException e) {
      throw new ProcessException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessException(e);
    }
  }

  @Override
  public <T> Future<T> submit(Submission<T> submission, boolean terminateProcessAfterwards) {
    if (submission == null) {
      throw new IllegalArgumentException("The submission cannot be null or empty.");
    }
    InternalSubmission<T> internalSubmission;
    synchronized (submissionQueue) {
      if (shutdown) {
        throw new RejectedExecutionException("The pool has already been shut down.");
      }
      numOfSubmissions++;
      internalSubmission = new InternalSubmission<>(submission, terminateProcessAfterwards);
      submissionQueue.addLast(internalSubmission);
    }
    // If necessary, adjust the pool size given the new submission.
    poolLock.lock();
    try {
      if (doExtendPool()) {
        startNewProcess();
      }
    } finally {
      poolLock.unlock();
    }
    logger.info("Submission {} received.{}", internalSubmission, System.lineSeparator() +
        getPoolStats());
    // Return a Future holding the total execution time including the submission delay.
    return new InternalSubmissionFuture<>(internalSubmission);
  }

  @Override
  public void shutdown() {
    if (!shutdown && shutdownLock.tryLock()) {
      try {
        shutdown = true;
        (new Thread(this::syncShutdown)).start();
      } finally {
        shutdownLock.unlock();
      }
    }
  }

  @Override
  public List<Submission<?>> forceShutdown() {
    List<Submission<?>> queuedSubmissions = new ArrayList<>();
    synchronized (submissionQueue) {
      shutdown = true;
      /* Using the stream here resulted in strange behaviour that had the LBDSpliterator
       * freeze forever (not waiting on any lock)  presumably in response to concurrent
       * modification. */
      for (InternalSubmission<?> s : submissionQueue) {
        queuedSubmissions.add(s.origSubmission);
      }
    }
    if (poolTermLatch.getCount() != 0 && shutdownLock.tryLock()) {
      try {
        (new Thread(this::syncForceShutdown)).start();
      } finally {
        shutdownLock.unlock();
      }
    }
    return queuedSubmissions;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return poolTermLatch.getCount() == 0;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return poolTermLatch.await(timeout, unit);
  }

  @Override
  public String toString() {
    return String.format("stdProcPool@%s", Integer.toHexString(hashCode()));
  }

  /**
   * An implementation of the {@link net.viktorc.pp4j.api.Submission} interface for wrapping other instances of the interface into a class
   * that allows for waiting for the completion of the submission, the cancellation thereof, and the tracking of the time its processing
   * took.
   *
   * @author Viktor Csomor
   */
  private class InternalSubmission<T> implements Submission<T> {

    final Submission<T> origSubmission;
    final boolean terminateProcessAfterwards;
    final long receivedTime;
    final Object lock;
    Thread thread;
    Exception exception;
    volatile long submittedTime;
    volatile long processedTime;
    volatile boolean processed;
    volatile boolean cancelled;

    /**
     * Constructs an instance according to the specified parameters.
     *
     * @param originalSubmission The submission to wrap into an internal submission with extended features.
     * @throws IllegalArgumentException If the submission is null.
     */
    InternalSubmission(Submission<T> originalSubmission, boolean terminateProcessAfterwards) {
      if (originalSubmission == null) {
        throw new IllegalArgumentException("The submission cannot be null.");
      }
      this.origSubmission = originalSubmission;
      this.terminateProcessAfterwards = terminateProcessAfterwards;
      receivedTime = System.nanoTime();
      lock = new Object();
    }

    /**
     * Sets the thread that is executing the submission.
     *
     * @param t The thread that executes the submission.
     */
    void setThread(Thread t) {
      synchronized (lock) {
        thread = t;
      }
    }

    /**
     * Sets the exception thrown during the execution of the submission if there was any.
     *
     * @param e The exception thrown during the execution of the submission.
     */
    void setException(Exception e) {
      // Notify the InternalSubmissionFuture that an exception was thrown while processing the submission.
      synchronized (lock) {
        exception = e;
        lock.notifyAll();
      }
    }

    /**
     * Returns whether the <code>cancelled</code> flag of the submission has been set to true.
     *
     * @return Whether the submission has been cancelled.
     */
    boolean isCancelled() {
      synchronized (lock) {
        return cancelled;
      }
    }

    /**
     * Sets the <code>cancelled</code> flag of the submission to true.
     */
    void cancel() {
      synchronized (lock) {
        cancelled = true;
        lock.notifyAll();
      }
    }

    @Override
    public List<Command> getCommands() {
      return origSubmission.getCommands();
    }

    @Override
    public T getResult() throws ExecutionException {
      return origSubmission.getResult();
    }

    @Override
    public void onStartedProcessing() {
      // If it is the first time the submission is submitted to a process...
      if (submittedTime == 0) {
        submittedTime = System.nanoTime();
        origSubmission.onStartedProcessing();
      }
    }

    @Override
    public void onFinishedProcessing() {
      origSubmission.onFinishedProcessing();
      processedTime = System.nanoTime();
      // Notify the InternalSubmissionFuture that the submission has been processed.
      synchronized (lock) {
        processed = true;
        lock.notifyAll();
      }
    }

    @Override
    public String toString() {
      return String.format("%s@%s", origSubmission, Integer.toHexString(hashCode()));
    }

  }

  /**
   * An implementation of {@link java.util.concurrent.Future} that can be used to cancel, wait on, and retrieve the return value of a {@link
   * net.viktorc.pp4j.impl.ProcessPoolExecutor.InternalSubmission} instance.
   *
   * @author Viktor Csomor
   */
  private class InternalSubmissionFuture<T> implements Future<T> {

    final InternalSubmission<T> submission;

    /**
     * Constructs a {@link java.util.concurrent.Future} for the specified submission.
     *
     * @param submission The submission to get a {@link java.util.concurrent.Future} for.
     */
    InternalSubmissionFuture(InternalSubmission<T> submission) {
      this.submission = submission;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      synchronized (submission.lock) {
        /* If the submission has already been cancelled or if it has already been processed, don't do
         * anything and return false. */
        if (submission.cancelled || submission.processed) {
          return false;
        }
        /* If it is already being processed and mayInterruptIfRunning is true, interrupt the executor
         * thread. */
        if (submission.thread != null) {
          if (mayInterruptIfRunning) {
            submission.cancel();
            submission.thread.interrupt();
          }
          // If mayInterruptIfRunning is false, don't let the submission be cancelled.
        } else
        // If the processing of the submission has not commenced yet, cancel it.
        {
          submission.cancel();
        }
        return submission.cancelled;
      }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException, CancellationException {
      // Wait until the submission is processed, or cancelled, or fails.
      synchronized (submission.lock) {
        while (!submission.processed && !submission.cancelled && submission.exception == null) {
          submission.lock.wait();
        }
        if (submission.cancelled) {
          throw new CancellationException(String.format("Submission %s cancelled.", submission));
        }
        if (submission.exception != null) {
          throw new ExecutionException(submission.exception);
        }
        return submission.getResult();
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
      // Wait until the submission is processed, or cancelled, or fails, or the method times out.
      synchronized (submission.lock) {
        long timeoutNs = unit.toNanos(timeout);
        long start = System.nanoTime();
        while (!submission.processed && !submission.cancelled && submission.exception == null &&
            timeoutNs > 0) {
          submission.lock.wait(timeoutNs / 1000000, (int) (timeoutNs % 1000000));
          timeoutNs -= (System.nanoTime() - start);
        }
        if (submission.cancelled) {
          throw new CancellationException(String.format("Submission %s cancelled.", submission));
        }
        if (submission.exception != null) {
          throw new ExecutionException(submission.exception);
        }
        if (timeoutNs <= 0) {
          throw new TimeoutException(String.format("Submission %s timed out.", submission));
        }
        return submission.getResult();
      }
    }

    @Override
    public boolean isCancelled() {
      return submission.cancelled;
    }

    @Override
    public boolean isDone() {
      return submission.processed;
    }

  }

  /**
   * A sub-class of {@link net.viktorc.pp4j.impl.AbstractProcessExecutor} that utilizes an additional thread to listen to the submission
   * queue of the process pool and take submissions for executions.
   *
   * @author Viktor Csomor
   */
  private class InternalProcessExecutor extends AbstractProcessExecutor {

    final Object subThreadLock;
    Thread subThread;

    /**
     * Constructs an instances using a newly created process manager and the
     * <code>auxThreadPool</code>.
     */
    InternalProcessExecutor() {
      super(procManagerFactory.newProcessManager(), auxThreadPool, verbose);
      subThreadLock = new Object();
    }

    /**
     * Starts waiting on the blocking queue of submissions executing available ones one at a time.
     */
    void startHandlingSubmissions() {
      synchronized (subThreadLock) {
        subThread = Thread.currentThread();
      }
      try {
        while (isRunning() && !isStopped()) {
          InternalSubmission<?> submission = null;
          boolean submissionRetrieved = false;
          boolean completed = false;
          try {
            /* Wait until the startup phase is over and the mainLock is available to avoid retrieving
             * a submission only to find that it cannot be executed and thus has to be put back into
             * the queue. */
            submissionLock.lock();
            submissionLock.unlock();
            // Wait for an available submission.
            submission = submissionQueue.takeFirst();
            submissionRetrieved = true;
            submission.setThread(subThread);
            if (submission.isCancelled()) {
              submission = null;
              continue;
            }
            /* It can happen of course that in the mean time, the mainLock has been stolen (for
             * terminating the process) or that the process is already terminated, and thus the
             * execute method fails. In this case, the submission is put back into the queue. */
            if (execute(submission, submission.terminateProcessAfterwards)) {
              logger.info(String.format("Submission %s processed; delay: %.3f; " +
                      "execution time: %.3f.%n%s", submission, (float) ((double)
                      (submission.submittedTime - submission.receivedTime) / 1000000000),
                  (float) ((double) (submission.processedTime -
                      submission.submittedTime) / 1000000000), getPoolStats()));
              submission = null;
            }
            completed = true;
          } catch (InterruptedException e) {
            // Next round (unless the process is stopped).
          } catch (Exception e) {
            // Signal the exception to the future and do not put the submission back into the queue.
            if (submission != null) {
              logger.warn(String.format("Exception while executing submission %s.%n%s",
                  submission, getPoolStats()), e);
              submission.setException(e);
              submission = null;
            }
          } finally {
            /* If the execute method failed and there was no exception thrown, put the submission
             * back into the queue at the front. */
            if (submission != null && !submission.isCancelled()) {
              submission.setThread(null);
              submissionQueue.addFirst(submission);
            } else if (submissionRetrieved) {
              // If the pool is shutdown and there are no more submissions left, signalize it.
              synchronized (submissionQueue) {
                numOfSubmissions--;
                if (shutdown && numOfSubmissions == 0) {
                  submissionQueue.notifyAll();
                }
              }
            }
            // If the submission did not complete due to an error or cancellation, kill the process.
            if (!completed) {
              submissionLock.lock();
              try {
                stop(true);
              } finally {
                submissionLock.unlock();
              }
            }
          }
        }
      } finally {
        synchronized (subThreadLock) {
          subThread = null;
        }
        termSemaphore.release();
      }
    }

    @Override
    protected void onExecutorStartup(boolean orderly) {
      if (orderly) {
        threadsToWaitFor.incrementAndGet();
        threadPool.submit(this::startHandlingSubmissions);
      }
      prestartLatch.countDown();
    }

    @Override
    protected void onExecutorTermination() {
      /* Interrupt the submission handler thread to avoid having it stuck waiting for
       * submissions forever in case the queue is empty. */
      synchronized (subThreadLock) {
        if (subThread != null) {
          subThread.interrupt();
        }
      }
    }

    @Override
    public String toString() {
      return String.format("%s-intProcExecutor@%s", ProcessPoolExecutor.this,
          Integer.toHexString(hashCode()));
    }

  }

  /**
   * A sub-class of {@link org.apache.commons.pool2.impl.GenericObjectPool} for the pooling of {@link
   * net.viktorc.pp4j.impl.ProcessPoolExecutor.InternalProcessExecutor} instances.
   *
   * @author Viktor Csomor
   */
  private class InternalProcessExecutorObjectPool extends GenericObjectPool<InternalProcessExecutor> {

    /**
     * Constructs an object pool instance to facilitate the reuse of {@link net.viktorc.pp4j.impl.ProcessPoolExecutor.InternalProcessExecutor}
     * instances. The pool does not block if there are no available objects, it accommodates <code>maxPoolSize</code> objects, and if there
     * are more than <code>Math.max(minPoolSize, reserveSize)</code> idle objects in the pool, excess idle objects are eligible for eviction
     * after <code>EVICT_TIME</code> milliseconds. The eviction thread runs at the above specified intervals and performs at most
     * <code>maxPoolSize - Math.max(minPoolSize, reserveSize)</code> evictions per run.
     */
    InternalProcessExecutorObjectPool() {
      super(new PooledObjectFactory<InternalProcessExecutor>() {

        @Override
        public PooledObject<InternalProcessExecutor> makeObject() {
          return new DefaultPooledObject<>(new InternalProcessExecutor());
        }

        @Override
        public void activateObject(PooledObject<InternalProcessExecutor> p) {
          // No-operation.
        }

        @Override
        public boolean validateObject(PooledObject<InternalProcessExecutor> p) {
          return true;
        }

        @Override
        public void passivateObject(PooledObject<InternalProcessExecutor> p) {
          // No-operation.
        }

        @Override
        public void destroyObject(PooledObject<InternalProcessExecutor> p) {
          // No-operation.
        }
      });
      setBlockWhenExhausted(false);
      setMaxTotal(maxPoolSize);
      setMaxIdle(Math.max(minPoolSize, reserveSize));
      long evictTime = EVICT_TIME;
      setTimeBetweenEvictionRunsMillis(evictTime);
      setSoftMinEvictableIdleTimeMillis(evictTime);
      setNumTestsPerEvictionRun(maxPoolSize - Math.max(maxPoolSize, reserveSize));
    }

  }

  /**
   * An implementation the {@link java.util.concurrent.ThreadFactory} interface that provides more descriptive thread  names and extends the
   * {@link java.lang.Thread.UncaughtExceptionHandler} of the created threads by logging the uncaught exceptions if the enclosing {@link
   * net.viktorc.pp4j.impl.ProcessPoolExecutor} instance is verbose. It also attempts to shut down the enclosing pool if an exception is
   * thrown in one of the threads it created.
   *
   * @author Viktor Csomor
   */
  private class CustomizedThreadFactory implements ThreadFactory {

    final String poolName;
    final ThreadFactory defaultFactory;

    /**
     * Constructs an instance according to the specified parameters.
     *
     * @param poolName The name of the thread pool. It will be prepended to the name of the created threads.
     */
    CustomizedThreadFactory(String poolName) {
      this.poolName = poolName;
      defaultFactory = Executors.defaultThreadFactory();
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = defaultFactory.newThread(r);
      thread.setName(thread.getName().replaceFirst("pool-[0-9]+", poolName));
      thread.setUncaughtExceptionHandler((t, e) -> {
        logger.error(e.getMessage(), e);
        ProcessPoolExecutor.this.forceShutdown();
      });
      return thread;
    }

  }

  /**
   * A sub-class of {@link java.util.concurrent.ThreadPoolExecutor} for the execution of {@link net.viktorc.pp4j.impl.ProcessPoolExecutor.InternalProcessExecutor}
   * instances. It utilizes an extension of the {@link java.util.concurrent.LinkedTransferQueue} and an implementation of the {@link
   * java.util.concurrent.RejectedExecutionHandler} as per Robert Tupelo-Schneck's
   * <a href="https://stackoverflow.com/a/24493856">answer</a> to a StackOverflow question to facilitate a
   * queueing logic that has the pool first increase the number of its threads and only really queue tasks once the maximum pool size has
   * been reached.
   *
   * @author Viktor Csomor
   */
  private class InternalProcessExecutorThreadPool extends ThreadPoolExecutor {

    /**
     * Constructs thread pool for the execution of {@link net.viktorc.pp4j.impl.ProcessPoolExecutor.InternalProcessExecutor} instances. If
     * there are more than <code>Math.max(minPoolSize, reserveSize)</code> idle threads in the pool, excess threads are evicted after
     * <code>EVICT_TIME</code> milliseconds.
     */
    InternalProcessExecutorThreadPool() {
      super(Math.max(minPoolSize, reserveSize), maxPoolSize, EVICT_TIME, TimeUnit.MILLISECONDS,
          new LinkedTransferQueue<Runnable>() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean offer(Runnable r) {
              /* If there is at least one thread waiting on the queue, delegate the runnablePart immediately;
               * else decline it and force the pool to create a new thread for running the runnablePart. */
              return tryTransfer(r);
            }
          }, new CustomizedThreadFactory(ProcessPoolExecutor.this + "-procExecThreadPool"), (runnable, executor) -> {
            try {
              /* If there are no threads waiting on the queue (all of them are busy executing)
               * and the maximum pool size has been reached, when the queue declines the offer,
               * the pool will not create any more threads but call this handler instead. This
               * handler 'forces' the declined runnablePart on the queue, ensuring that it is not
               * rejected. */
              executor.getQueue().put(runnable);
            } catch (InterruptedException e) {
              // Should not happen.
              Thread.currentThread().interrupt();
            }
          });
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      InternalProcessExecutor executor = (InternalProcessExecutor) r;
      activeProcExecutors.remove(executor);
      procExecObjectPool.returnObject(executor);
      logger.debug("Process executor {} stopped.{}", executor, System.lineSeparator() + getPoolStats());
      // A process has terminated. Extend the pool if necessary by using the pooled executors.
      poolLock.lock();
      try {
        if (doExtendPool()) {
          startNewProcess();
        }
      } finally {
        poolLock.unlock();
      }
    }

  }

}