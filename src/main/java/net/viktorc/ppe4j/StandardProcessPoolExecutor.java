package net.viktorc.ppe4j;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

/**
 * An implementation of the {@link net.viktorc.ppe4j.ProcessPoolExecutor} interface for maintaining and managing a pool of pre-started 
 * processes. The processes are executed in {@link net.viktorc.ppe4j.StandardProcessShell} instances. Each shell is assigned an instance 
 * of an implementation of the {@link net.viktorc.ppe4j.ProcessManager} interface using an implementation of the {@link net.viktorc.ppe4j.ProcessManagerFactory} 
 * interface. The pool accepts submissions in the form of {@link net.viktorc.ppe4j.Submission} implementations which are executed on any 
 * one of the available active process shells maintained by the pool. While executing a submission, the shell cannot accept further 
 * submissions. The submissions are queued and executed as soon as there is an available shell. The size of the pool is always kept between 
 * the minimum pool size and the maximum pool size (both inclusive). The reserve size specifies the minimum number of processes that should 
 * always be available (there are no guarantees that there actually will be this many available shells at any given time).
 * 
 * @author Viktor
 *
 */
public class StandardProcessPoolExecutor implements ProcessPoolExecutor {
	
	/**
	 * The number of milliseconds after which idle process shell instances and process shell executor threads are evicted if 
	 * {@link #keepAliveTime} is non-positive.
	 */
	private static final long DEFAULT_EVICT_TIME = 60L*1000;
	
	private final ProcessManagerFactory managerFactory;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final long keepAliveTime;
	private final boolean verbose;
	private final Thread submissionLoop;
	private final ProcessShellExecutor shellExecutor;
	private final ExecutorService auxExecutor;
	private final ProcessShellPool shellPool;
	// All shells borrowed from the pool including those that are in the startup or termination phases.
	private final Queue<StandardProcessShell> activeShells;
	// Shells borrowed from the pool that are ready for submissions or are currently executing submissions.
	private final Queue<StandardProcessShell> hotShells;
	private final BlockingQueue<InternalSubmission> submissions;
	private final AtomicInteger numOfExecutingSubmissions;
	private final CountDownLatch prestartLatch;
	private final Object lock;
	private final Logger logger;
	private volatile boolean close;

	/**
	 * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size depending on which 
	 * one is greater. This constructor blocks until the initial number of processes start up. The size of the pool is dynamically 
	 * adjusted based on the pool parameters and the rate of incoming submissions.
	 * 
	 * @param managerFactory A {@link net.viktorc.ppe4j.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.ppe4j.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @param verbose Whether events relating to the management of the pool should be logged.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public StandardProcessPoolExecutor(ProcessManagerFactory managerFactory, int minPoolSize, int maxPoolSize, int reserveSize,
			long keepAliveTime, boolean verbose) throws InterruptedException {
		if (managerFactory == null)
			throw new IllegalArgumentException("The process manager factory cannot be null.");
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the " +
					"minimum pool size.");
		if (reserveSize < 0 || reserveSize > maxPoolSize)
			throw new IllegalArgumentException("The reserve has to be greater than 0 and less than the maximum pool size.");
		this.managerFactory = managerFactory;
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.reserveSize = reserveSize;
		this.keepAliveTime = Math.max(0, keepAliveTime);
		this.verbose = verbose;
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		submissionLoop = new Thread(this::submissionLoop, "submissionLoop");
		shellExecutor = new ProcessShellExecutor();
		auxExecutor = Executors.newCachedThreadPool(new CustomizedThreadFactory("auxExecutor",
				"Error while interacting with the process."));
		shellPool = new ProcessShellPool();
		activeShells = new LinkedBlockingQueue<>();
		hotShells = new LinkedBlockingQueue<>();
		submissions = new LinkedBlockingQueue<>();
		numOfExecutingSubmissions = new AtomicInteger(0);
		prestartLatch = new CountDownLatch(actualMinSize);
		lock = new Object();
		logger = Logger.getAnonymousLogger();
		for (int i = 0; i < actualMinSize; i++) {
			synchronized (lock) {
				startNewProcess(null);
			}
		}
		// Wait for the processes in the initial pool to start up.
		prestartLatch.await();
		// Start the thread responsible for submitting commands.
		synchronized (lock) {
			if (!close)
				submissionLoop.start();
		}
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
	 * Returns the minimum number of processes to hold in the pool.
	 * 
	 * @return The minimum size of the process pool.
	 */
	public int getMinSize() {
		return minPoolSize;
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
	 * Returns the number of milliseconds after which idle processes should be terminated. If it is 0 or less, 
	 * the processes are never terminated due to a timeout.
	 * 
	 * @return The number of milliseconds after which idle processes should be terminated.
	 */
	public long getKeepAliveTime() {
		return keepAliveTime;
	}
	/**
	 * Returns whether events relating to the management of the processes held by the pool are logged to the 
	 * console.
	 * 
	 * @return Whether the pool is verbose.
	 */
	public boolean isVerbose() {
		return verbose;
	}
	/**
	 * Returns the total number of running processes currently held in the pool.
	 * 
	 * @return The total number of running processes.
	 */
	public int getTotalNumOfProcesses() {
		return activeShells.size();
	}
	/**
	 * Returns the number of active, i.e. started up and not yet cancelled, processes held in the pool.
	 * 
	 * @return The number of active processes.
	 */
	public int getNumOfActiveProcesses() {
		return hotShells.size();
	}
	/**
	 * Returns the number of submissions currently being executed in the pool.
	 * 
	 * @return The number of submissions currently being executed in the pool.
	 */
	public int getNumOfExecutingSubmissions() {
		return numOfExecutingSubmissions.get();
	}
	/**
	 * Returns the number of submissions queued and waiting for execution.
	 * 
	 * @return The number of queued submissions.
	 */
	public int getNumOfQueuedSubmissions() {
		return submissions.size();
	}
	/**
	 * Returns the number of active, queued, and currently executing processes as string.
	 * 
	 * @return A string of statistics concerning the size of the process pool.
	 */
	private String getPoolStats() {
		return "Total processes: " + activeShells.size() + "; acitve processes: " + hotShells.size() +
				"; submitted commands: " + (numOfExecutingSubmissions.get() + submissions.size());
	}
	/**
	 * Returns whether a new process {@link net.viktorc.ppe4j.StandardProcessShell} instance should be started.
	 * 
	 * @return Whether the process pool should be extended.
	 */
	private boolean doExtendPool() {
		return !close && (activeShells.size() < minPoolSize || (activeShells.size() < Math.min(maxPoolSize,
				numOfExecutingSubmissions.get() + submissions.size() + reserveSize)));
	}
	/**
	 * Starts a new process by executing the provided {@link net.viktorc.ppe4j.StandardProcessShell}. If it is null, it creates a new 
	 * instance, adds it to the pool, and executes it.
	 * 
	 * @param processShell An optional {@link net.viktorc.ppe4j.StandardProcessShell} instance to re-start in case one is available.
	 * @return Whether the process was successfully started.
	 * @throws ProcessException If a process shell instance cannot be borrowed from the pool.
	 */
	private boolean startNewProcess(StandardProcessShell processShell) {
		if (processShell == null) {
			try {
				processShell = shellPool.borrowObject();
			} catch (Exception e) {
				return false;
			}
		}
		/* Try to execute the process. It may happen that the count of active processes is not correct and in fact the pool 
		 * has reached its capacity in the mean time. It is ignored for now. !TODO Devise a mechanism that takes care of this. */
		try {
			shellExecutor.execute(processShell);
			activeShells.add(processShell);
			return true;
		} catch (RejectedExecutionException e) {
			shellPool.returnObject(processShell);
			if (verbose)
				logger.log(Level.WARNING, "Failed to start new process due to the pool having reached its capacity.");
			return false;
		}
	}
	/**
	 * A method that handles the submission of commands from the queue to the processes.
	 */
	private void submissionLoop() {
		InternalSubmission nextSubmission = null;
		while (!close) {
			try {
				// Wait until there is a command submitted.
				if (nextSubmission == null)
					nextSubmission = submissions.take();
				InternalSubmission submission = nextSubmission;
				// Execute it in any of the available processes.
				for (StandardProcessShell shell : hotShells) {
					if (shell.isReady()) {
						Future<?> future = auxExecutor.submit(() -> {
							try {
								submission.submitted = false;
								submission.semaphore.drainPermits();
								if (shell.execute(submission)) {
									if (verbose)
										logger.info(String.format("Command(s) %s processed; submission delay: %.3f;" +
												" execution time: %.3f.", submission,
												(float) ((double) (submission.submittedTime -
												submission.receivedTime)/1000000000),
												(float) ((double) (submission.processedTime -
												submission.submittedTime)/1000000000)));
								} else
									submission.semaphore.release();
							} catch (Exception e) {
								if (verbose)
									logger.log(Level.SEVERE, "Exception while executing command(s) " +
											submission + ".", e);
								if (submission.semaphore.availablePermits() == 0)
									submission.semaphore.release();
								synchronized (submission) {
									submission.exception = e;
									submission.notifyAll();
								}
							}
						});
						submission.semaphore.acquire();
						if (submission.submitted) {
							synchronized (submission) {
								submission.future = future;	
							}
							submissions.remove(submission);
							nextSubmission = null;
							break;
						}
					}
				}
				// Extend the pool if needed.
				synchronized (lock) {
					if (doExtendPool())
						startNewProcess(null);
				}
			} catch (InterruptedException e) {
				return;
			} catch (Exception e) {
				if (verbose)
					logger.log(Level.SEVERE, "An error occurred while submitting commands.", e);
			}
		}
	}
	/**
	 * Executes the submission on any of the available processes in the pool.
	 * 
	 * @param submission The submission including all information necessary for executing and processing the command(s).
	 * @return A {@link java.util.concurrent.Future} instance of the time it took to execute the command including the submission 
	 * delay in milliseconds.
	 * @throws IllegalStateException If the pool has already been shut down.
	 * @throws IllegalArgumentException If the submission is null.
	 */
	@Override
	public Future<Long> submit(Submission submission) {
		if (close)
			throw new IllegalStateException("The pool has already been shut down.");
		if (submission == null)
			throw new IllegalArgumentException("The submission cannot be null or empty.");
		InternalSubmission internalSubmission = new InternalSubmission(submission);
		submissions.add(internalSubmission);
		// Return a Future holding the total execution time including the submission delay.
		return new InternalSubmissionFuture(internalSubmission);
	}
	/**
	 * Attempts to shut the process pool including all its processes down. The method blocks until all {@link net.viktorc.ppe4j.StandardProcessShell} 
	 * instances maintained by the pool are closed.
	 * 
	 * @throws IllegalStateException If the pool has already been shut down.
	 */
	@Override
	public synchronized void shutdown() {
		if (close)
			throw new IllegalStateException("The pool has already been shut down.");
		synchronized (lock) {
			if (verbose)
				logger.info("Initiating shutdown...");
			close = true;
			while (prestartLatch.getCount() != 0)
				prestartLatch.countDown();
			submissionLoop.interrupt();
			if (verbose)
				logger.info("Shutting down process shells...");
			for (StandardProcessShell shell : activeShells)
				shell.stop(true);
			if (verbose)
				logger.info("Shutting down thread pools...");
			shellExecutor.shutdown();
			auxExecutor.shutdown();
			shellPool.close();
			if (verbose)
				logger.info("Process pool shut down.");
		}
	}
	
	/**
	 * An implementation the {@link java.util.concurrent.ThreadFactory} interface that provides more descriptive thread names and 
	 * extends the {@link java.lang.Thread.UncaughtExceptionHandler} of the created threads by logging the uncaught exceptions if 
	 * the enclosing {@link net.viktorc.ppe4j.StandardProcessPoolExecutor} instance is verbose. It also attempts to shut down the 
	 * enclosing pool if a {@link net.viktorc.ppe4j.ProcessException} is thrown in one of the threads it created.
	 * 
	 * @author Viktor
	 *
	 */
	private class CustomizedThreadFactory implements ThreadFactory {

		final ThreadFactory defaultFactory;
		final String poolName;
		final String executionErrorMessage;
		
		/**
		 * Constructs an instance according to the specified parameters.
		 * 
		 * @param poolName The name of the thread pool. It will be prepended to the name of the created threads.
		 * @param executionErrorMessage The error message to log in case an exception is thrown while 
		 * executing the {@link java.lang.Runnable}.
		 */
		CustomizedThreadFactory(String poolName, String executionErrorMessage) {
			defaultFactory = Executors.defaultThreadFactory();
			this.poolName = poolName;
			this.executionErrorMessage = executionErrorMessage;
		}
		@Override
		public Thread newThread(Runnable r) {
			Thread t = defaultFactory.newThread(r);
			t.setName(t.getName().replaceFirst("pool-[0-9]+", poolName));
			UncaughtExceptionHandler handler = t.getUncaughtExceptionHandler();
			t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				
				@Override
				public void uncaughtException(Thread t, Throwable e) {
					if (e instanceof ProcessException) {
						e.printStackTrace();
						StandardProcessPoolExecutor.this.shutdown();
						return;
					}
					if (verbose)
						logger.log(Level.SEVERE, executionErrorMessage, e);
					if (handler != null)
						handler.uncaughtException(t, e);
				}
			});
			return t;
		}
		
	}
	
	/**
	 * A sub-class of {@link java.util.concurrent.ThreadPoolExecutor} for the execution of {@link net.viktorc.ppe4j.StandardProcessShell} 
	 * instances.
	 * 
	 * @author Viktor
	 *
	 */
	private class ProcessShellExecutor extends ThreadPoolExecutor {

		/**
		 * Constructs thread pool for the execution of {@link net.viktorc.ppe4j.StandardProcessShell} instances. If there are more than 
		 * <code>Math.max(minPoolSize, reserveSize)</code> idle threads in the pool, excess threads are evicted after <code>keepAliveTime</code> 
		 * milliseconds, or if it is non-positive, after <code>DEFAULT_EVICT_TIME</code> milliseconds.
		 */
		ProcessShellExecutor() {
			super(Math.max(minPoolSize, reserveSize), maxPoolSize, keepAliveTime > 0 ? keepAliveTime : DEFAULT_EVICT_TIME,
					TimeUnit.MILLISECONDS, new SynchronousQueue<>(), new CustomizedThreadFactory("shellExecutor",
							"Error while excuting the process."));
		}
		@Override
		protected void beforeExecute(Thread t, Runnable r) {
			super.beforeExecute(t, r);
			if (verbose)
				logger.info("Process shell " + (ProcessShell) r + " starting execution." + System.lineSeparator() +
						getPoolStats());
		}
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			StandardProcessShell shell = (StandardProcessShell) r;
			activeShells.remove(shell);
			if (verbose)
				logger.info("Process shell " + shell + " stopped executing." + System.lineSeparator() +
						getPoolStats());
			if (t == null) {
				synchronized (lock) {
					if (doExtendPool())
						startNewProcess(shell);
					else
						shellPool.returnObject(shell);
				}
			} else {
				try {
					shellPool.invalidateObject(shell);
				} catch (Exception e) {
					// This cannot practically happen.
					if (verbose)
						logger.log(Level.SEVERE, "Error while invalidating process shell " + shell + ".", e);
				}
			}
		}
		
	}
	
	/**
	 * An implementation of the {@link org.apache.commons.pool2.PooledObjectFactory} interface to handle the creation of 
	 * pooled {@link net.viktorc.ppe4j.StandardProcessShell} instances.
	 * 
	 * @author Viktor
	 *
	 */
	private class PooledProcessShellFactory implements PooledObjectFactory<StandardProcessShell> {

		@Override
		public PooledObject<StandardProcessShell> makeObject() throws Exception {
			PooledProcessManager manager = new PooledProcessManager(managerFactory.createNewProcessManager());
			StandardProcessShell processShell = new StandardProcessShell(manager, keepAliveTime, auxExecutor);
			manager.processShell = processShell;
			return new DefaultPooledObject<StandardProcessShell>(processShell);
		}
		@Override
		public void activateObject(PooledObject<StandardProcessShell> p) {
			// No-operation.
		}
		@Override
		public boolean validateObject(PooledObject<StandardProcessShell> p) {
			return true;
		}
		@Override
		public void passivateObject(PooledObject<StandardProcessShell> p) {
			// No-operation
		}
		@Override
		public void destroyObject(PooledObject<StandardProcessShell> p) {
			// No-operation.
		}
		
	}
	
	/**
	 * A sub-class of {@link org.apache.commons.pool2.impl.GenericObjectPool} for the pooling of {@link net.viktorc.ppe4j.StandardProcessShell} 
	 * instances.
	 * 
	 * @author Viktor
	 *
	 */
	private class ProcessShellPool extends GenericObjectPool<StandardProcessShell> {

		/**
		 * Constructs an object pool instance to facilitate the reuse of {@link net.viktorc.ppe4j.StandardProcessShell} instances. The pool 
		 * does not block if there are no available objects, it accommodates <code>maxPoolSize</code> objects, and if there are more than 
		 * <code>Math.max(minPoolSize, reserveSize)</code> idle objects in the pool, excess idle objects are eligible for eviction after 
		 * <code>keepAliveTime</code> milliseconds, or if it is non-positive, after <code>DEFAULT_EVICT_TIME</code> milliseconds. The eviction 
		 * thread runs at the above specified intervals and performs at most <code>maxPoolSize - minPoolSize</code> evictions per run.
		 */
		public ProcessShellPool() {
			super(new PooledProcessShellFactory());
			setBlockWhenExhausted(false);
			setMaxTotal(maxPoolSize);
			setMaxIdle(Math.max(minPoolSize, reserveSize));
			long evictTime = keepAliveTime > 0 ? keepAliveTime : DEFAULT_EVICT_TIME;
			setTimeBetweenEvictionRunsMillis(evictTime);
			setSoftMinEvictableIdleTimeMillis(evictTime);
			setNumTestsPerEvictionRun(maxPoolSize - minPoolSize);
		}
		
	}
	
	/**
	 * An implementation of the {@link net.viktorc.ppe4j.ProcessManager} interface for managing the life cycle of 
	 * individual pooled processes.
	 * 
	 * @author Viktor
	 *
	 */
	private class PooledProcessManager implements ProcessManager {
		
		final ProcessManager originalManager;
		ProcessShell processShell;
		
		/**
		 * Constructs a wrapper around the specified process manager.
		 * 
		 * @param originalManager The original process manager.
		 */
		PooledProcessManager(ProcessManager originalManager) {
			this.originalManager = originalManager;
		}
		@Override
		public Process start() throws IOException {
			return originalManager.start();
		}
		@Override
		public boolean startsUpInstantly() {
			return originalManager.startsUpInstantly();
		}
		@Override
		public boolean isStartedUp(String output, boolean standard) {
			return originalManager.isStartedUp(output, standard);
		}
		@Override
		public void onStartup(ProcessShell shell) {
			originalManager.onStartup(shell);
			hotShells.add((StandardProcessShell) shell);
			prestartLatch.countDown();
		}
		@Override
		public boolean terminate(ProcessShell shell) {
			return originalManager.terminate(shell);
		}
		@Override
		public void onTermination(int resultCode) {
			originalManager.onTermination(resultCode);
			if (processShell != null)
				hotShells.remove(processShell);
		}
		
	}
	
	/**
	 * An implementation of the {@link net.viktorc.ppe4j.Submission} interface for wrapping submissions into 'internal' 
	 * submissions to keep track of the number of commands being executed at a time and to establish a mechanism for 
	 * canceling submitted commands via the {@link java.util.concurrent.Future} returned by the 
	 * {@link net.viktorc.ppe4j.StandardProcessPoolExecutor#submit(Submission)} method.
	 * 
	 * @author Viktor
	 *
	 */
	private class InternalSubmission implements Submission {
		
		final Submission originalSubmission;
		final long receivedTime;
		final Semaphore semaphore;
		volatile Long submittedTime;
		volatile Long processedTime;
		volatile boolean cancel;
		volatile boolean submitted;
		volatile boolean processed;
		volatile Future<?> future;
		volatile Exception exception;
		
		/**
		 * Constructs an instance according to the specified parameters.
		 * 
		 * @param originalSubmission The submission to wrap into an internal submission with extended features.
		 * @throws IllegalArgumentException If the submission is null.
		 */
		InternalSubmission(Submission originalSubmission) {
			if (originalSubmission == null)
				throw new IllegalArgumentException("The submission cannot be null.");
			this.originalSubmission = originalSubmission;
			receivedTime = System.nanoTime();
			semaphore = new Semaphore(0);
		}
		@Override
		public List<Command> getCommands() {
			return originalSubmission.getCommands();
		}
		@Override
		public boolean doTerminateProcessAfterwards() {
			return originalSubmission.doTerminateProcessAfterwards();
		}
		@Override
		public synchronized boolean isCancelled() {
			return originalSubmission.isCancelled() || cancel || close;
		}
		@Override
		public void onStartedProcessing() {
			submittedTime = System.nanoTime();
			submitted = true;
			semaphore.release();
			originalSubmission.onStartedProcessing();
			numOfExecutingSubmissions.incrementAndGet();
		}
		@Override
		public void onFinishedProcessing() {
			originalSubmission.onFinishedProcessing();
			processedTime = System.nanoTime();
			synchronized (this) {
				processed = true;
				notifyAll();
			}
			numOfExecutingSubmissions.decrementAndGet();
		}
		@Override
		public String toString() {
			return originalSubmission.toString();
		}
		
	}
	
	/**
	 * An implementation of {@link java.util.concurrent.Future} that returns the time it took to process the 
	 * submission.
	 * 
	 * @author Viktor
	 *
	 */
	private class InternalSubmissionFuture implements Future<Long> {
		
		final InternalSubmission submission;
		
		/**
		 * Constructs a {@link java.util.concurrent.Future} for the specified submission.
		 * 
		 * @param submission The submission to get a {@link java.util.concurrent.Future} for.
		 */
		InternalSubmissionFuture(InternalSubmission submission) {
			this.submission = submission;
		}
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			synchronized (submission) {
				if (submission.isCancelled() || (submission.future != null &&
						submission.future.cancel(mayInterruptIfRunning))) {
					submission.cancel = true;
					submission.notifyAll();
					submissions.remove(submission);
					return true;
				} else
					return false;
			}
		}
		@Override
		public Long get() throws InterruptedException, ExecutionException, CancellationException {
			synchronized (submission) {
				while (!submission.processed && !submission.isCancelled() && submission.exception == null)
					submission.wait();
			}
			if (submission.isCancelled() || close)
				throw new CancellationException();
			if (submission.exception != null)
				throw new ExecutionException(submission.exception);
			return (long) Math.round(((double) (submission.processedTime - submission.receivedTime))/1000000);
		}
		@Override
		public Long get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
			long timeoutNs = unit.toNanos(timeout);
			long start = System.nanoTime();
			synchronized (submission) {
				while (!submission.processed && !submission.isCancelled() && submission.exception == null &&
						timeoutNs > 0) {
					submission.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
					timeoutNs -= (System.nanoTime() - start);
				}
			}
			if (submission.isCancelled() || close)
				throw new CancellationException();
			if (submission.exception != null)
				throw new ExecutionException(submission.exception);
			if (timeoutNs <= 0)
				throw new TimeoutException();
			return timeoutNs <= 0 ? null : (long) Math.round(((double) (submission.processedTime -
					submission.receivedTime))/1000000);
		}
		@Override
		public boolean isCancelled() {
			return submission.isCancelled();
		}
		@Override
		public boolean isDone() {
			return submission.processed;
		}
		
	}
	
}