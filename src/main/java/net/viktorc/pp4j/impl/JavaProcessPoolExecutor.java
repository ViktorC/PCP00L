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

import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.JavaProcessExecutorService;
import net.viktorc.pp4j.api.JavaProcessOptions;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;

/**
 * A sub-class of {@link net.viktorc.pp4j.impl.ProcessPoolExecutor} that implements the {@link net.viktorc.pp4j.api.JavaProcessExecutorService}
 * interface. It uses Java processes for the implementation of multiprocessing. It communicates with the processes via their standard
 * streams exchanging serialized and encoded objects. It can send {@link java.lang.Runnable} and {@link java.util.concurrent.Callable}
 * instances to the processes; and it receives the result or exception object serialized and encoded into a string.
 *
 * @author Viktor Csomor
 */
public class JavaProcessPoolExecutor extends ProcessPoolExecutor implements JavaProcessExecutorService {

  /**
   * Constructs a Java process pool executor using the specified parameters.
   *
   * @param options The options for the "java" program used to create the new JVM.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @param startupTask The task to execute in each process on startup, before the process starts accepting submissions. If it is
   * <code>null</code>, no taks are executed on startup.
   * @param verbose Whether the events related to the management of the process pool should be logged. Setting this parameter to
   * <code>true</code> does not guarantee that logging will be performed as logging depends on the SLF4J binding and the logging
   * configurations, but setting it to <code>false</code> guarantees that no logging will be performed by the constructed instance.
   * @param <T> The type of the startup task.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If <code>options</code> is <code>null</code>, the minimum pool size is less than 0, or the maximum
   * pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public <T extends Runnable & Serializable> JavaProcessPoolExecutor(JavaProcessOptions options, int minPoolSize,
      int maxPoolSize, int reserveSize, T startupTask, boolean verbose) throws InterruptedException {
    super(new JavaProcessManagerFactory<>(options, startupTask), minPoolSize, maxPoolSize,
        reserveSize, verbose);
  }

  /**
   * Returns the Java process options associated with the pool.
   *
   * @return The Java process options used to create the processes.
   */
  public JavaProcessOptions getJavaProcessOptions() {
    return ((JavaProcessManagerFactory<?>) getProcessManagerFactory()).options;
  }

  /**
   * It executes a serializable {@link java.util.concurrent.Callable} instance with a serializable return type in one of the processes of
   * the pool and returns its return value. If the implementation contains non-serializable, non-transient fields, or the return type is not
   * serializable, the method fails.
   *
   * @param task The runnablePart to execute.
   * @param terminateProcessAfterwards Whether the process that executes the runnablePart should be terminated afterwards.
   * @param <T> The serializable return type variable of the <code>Callable</code>
   * @param <S> A serializable <code>Callable</code> instance with the return type <code>T</code>.
   * @return A {@link java.util.concurrent.Future} instance associated with the return value of the runnablePart.
   * @throws IOException If the serialization fails.
   * @throws NotSerializableException If some object to be serialized does not implement the {@link java.io.Serializable} interface.
   */
  public <T extends Serializable, S extends Callable<T> & Serializable> Future<T> submitExplicitly(S task,
      boolean terminateProcessAfterwards) throws IOException {
    return submit(new JavaSubmission<>(task), terminateProcessAfterwards);
  }

  @Override
  public void execute(Runnable command) {
    try {
      submit(command).get();
    } catch (ExecutionException e) {
      throw new ProcessException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessException(e);
    }
  }

  @Override
  public <T> Future<T> submit(Callable<T> task, boolean terminateProcessAfterwards) {
    try {
      return new CastFuture<>(submitExplicitly(new SerializableCallable<>((Callable<T> & Serializable) task), terminateProcessAfterwards));
    } catch (Exception e) {
      throw new IllegalArgumentException("The task is not serializable.", e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Future<T> submit(Runnable task, T result, boolean terminateProcessAfterwards) {
    try {
      return new CastFuture<>(submitExplicitly(new SerializableCallable<>((Callable<T> & Serializable)
          () -> {
            task.run();
            return result;
          }, task), terminateProcessAfterwards));
    } catch (Exception e) {
      throw new IllegalArgumentException("The task is not serializable.", e);
    }
  }

  @Override
  public Future<?> submit(Runnable task, boolean terminateProcessAfterwards) {
    try {
      return submit(task, null, terminateProcessAfterwards);
    } catch (Exception e) {
      throw new IllegalArgumentException("The task is not serializable.", e);
    }
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    List<Future<T>> futures = new ArrayList<>();
    for (Callable<T> t : tasks) {
      futures.add(submit(t));
    }
    for (Future<T> f : futures) {
      try {
        if (!f.isDone()) {
          f.get();
        }
      } catch (ExecutionException | CancellationException e) {
        // Ignore it.
      }
    }
    return futures;
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
      long timeout, TimeUnit unit) throws InterruptedException {
    List<Future<T>> futures = new ArrayList<>();
    for (Callable<T> t : tasks) {
      futures.add(submit(t));
    }
    long waitTimeNs = unit.toNanos(timeout);
    for (int i = 0; i < futures.size(); i++) {
      Future<T> f = futures.get(i);
      long start = System.nanoTime();
      try {
        if (!f.isDone()) {
          f.get(waitTimeNs, TimeUnit.NANOSECONDS);
        }
      } catch (ExecutionException | CancellationException e) {
        // Ignore it.
      } catch (TimeoutException e) {
        for (int j = i; j < futures.size(); j++) {
          futures.get(j).cancel(true);
        }
        break;
      } finally {
        waitTimeNs -= (System.nanoTime() - start);
      }
    }
    return futures;
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    ExecutionException execException = null;
    for (Future<T> f : invokeAll(tasks)) {
      try {
        return f.get();
      } catch (ExecutionException e) {
        execException = e;
      } catch (CancellationException e) {
        // Ignore it.
      }
    }
    if (execException == null) {
      throw new ExecutionException(new Exception("No runnablePart completed successfully."));
    }
    throw execException;
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    ExecutionException execException = null;
    for (Future<T> f : invokeAll(tasks, timeout, unit)) {
      try {
        return f.get();
      } catch (ExecutionException e) {
        execException = e;
      } catch (CancellationException e) {
        // Ignore it.
      }
    }
    if (execException == null) {
      throw new TimeoutException();
    }
    throw execException;
  }

  /**
   * See {@link java.util.concurrent.ExecutorService#shutdownNow()}. It is equivalent to {@link #forceShutdown()} with the only difference
   * being that this method filters and converts the returned submissions to a list of {@link java.lang.Runnable} instances excluding {@link
   * java.util.concurrent.Callable} based submissions.
   */
  @Override
  public List<Runnable> shutdownNow() {
    return super.forceShutdown().stream()
        .filter(s -> s instanceof JavaSubmission)
        .map(s -> ((SerializableCallable<?, ?>) ((JavaSubmission<?, ?>) s).task).runnablePart)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * An implementation of the {@link net.viktorc.pp4j.api.ProcessManagerFactory} for the creation of {@link
   * net.viktorc.pp4j.impl.JavaProcessPoolExecutor.JavaProcessManager} instances using a single {@link java.lang.ProcessBuilder} instance.
   *
   * @param <T> A type variable implementing the {@link java.lang.Runnable} and {@link java.io.Serializable} interfaces that defines the
   * base class of the startup tasks.
   * @author Viktor Csomor
   */
  private static class JavaProcessManagerFactory<T extends Runnable & Serializable> implements ProcessManagerFactory {

    JavaProcessOptions options;
    T startupTask;

    /**
     * Constructs an instance based on the specified JVM options and <code>keepAliveTime</code> which are used for the creation of all
     * processes of the pool.
     *
     * @param options The JVM options for starting the Java process.
     * @param startupTask The task to execute in each process on startup, before the process starts accepting submissions. If it is
     * <code>null</code>, no taks are executed on startup.
     * @throws IllegalArgumentException If <code>options</code> is <code>null</code>.
     */
    JavaProcessManagerFactory(JavaProcessOptions options, T startupTask) {
      if (options == null) {
        throw new IllegalArgumentException("The options argument cannot be null.");
      }
      this.startupTask = startupTask;
      this.options = options;
    }

    @Override
    public ProcessManager newProcessManager() {
      String javaPath = System.getProperty("java.home") + File.separator + "bin" +
          File.separator + "java";
      String classPath = System.getProperty("java.class.path");
      ClassLoader classLoader = this.getClass().getClassLoader();
      if (classLoader instanceof URLClassLoader) {
        @SuppressWarnings("resource")
        URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
        Set<String> classPathEntries = new HashSet<>(Arrays.asList(classPath.split(File.pathSeparator)));
        for (URL url : urlClassLoader.getURLs()) {
          try {
            classPathEntries.add(Paths.get(url.toURI()).toAbsolutePath().toString());
          } catch (FileSystemNotFoundException | URISyntaxException e) {
            // Ignore it.
          }
        }
        classPath = String.join(File.pathSeparator, classPathEntries);
      }
      String className = JavaProcess.class.getCanonicalName();
      long keepAliveTime;
      List<String> javaOptions = new ArrayList<>();
      JVMArch arch = options.getArch();
      JVMType type = options.getType();
      Integer initHeap = options.getInitHeapSizeMb();
      Integer maxHeap = options.getMaxHeapSizeMb();
      Integer stack = options.getStackSizeKb();
      keepAliveTime = options.getKeepAliveTime();
      if (arch != null) {
        javaOptions.add(arch.equals(JVMArch.BIT_32) ? "-d32" : "-d64");
      }
      if (type != null) {
        javaOptions.add(type.equals(JVMType.CLIENT) ? "-client" : "-server");
      }
      if (initHeap > 0) {
        javaOptions.add(String.format("-Xms%dm", initHeap));
      }
      if (maxHeap > 0) {
        javaOptions.add(String.format("-Xmx%dm", maxHeap));
      }
      if (stack > 0) {
        javaOptions.add(String.format("-Xss%dk", stack));
      }
      List<String> args = new ArrayList<>();
      args.add(javaPath);
      args.addAll(javaOptions);
      args.add("-cp");
      args.add(classPath);
      args.add(className);
      ProcessBuilder builder = new ProcessBuilder(args);
      // Redirect the error stream to reduce the number of used threads per process.
      builder.redirectErrorStream(true);
      return new JavaProcessManager<>(builder, keepAliveTime, startupTask);
    }

  }

  /**
   * A sub-class of {@link net.viktorc.pp4j.impl.AbstractProcessManager} for the management of process instances of the {@link
   * net.viktorc.pp4j.impl.JavaProcess} class.
   *
   * @param <T> A type variable implementing the {@link java.lang.Runnable} and {@link java.io.Serializable} interfaces that defines the
   * base class of the startup task.
   * @author Viktor Csomor
   */
  private static class JavaProcessManager<T extends Runnable & Serializable> extends AbstractProcessManager {

    T startupTask;

    /**
     * Constructs an instance using the specified <code>builder</code> and <code>keepAliveTime</code>.
     *
     * @param builder The <code>ProcessBuilder</code> to use for starting the Java processes.
     * @param keepAliveTime The number of milliseconds of idleness after which the processes should be terminated. If it is non-positive,
     * the life-cycle of processes will not be limited based on idleness.
     */
    JavaProcessManager(ProcessBuilder builder, long keepAliveTime, T startupTask) {
      super(builder, keepAliveTime);
      this.startupTask = startupTask;
    }

    @Override
    public boolean startsUpInstantly() {
      return false;
    }

    @Override
    public boolean isStartedUp(String outputLine, boolean standard) {
      return standard && JavaProcess.STARTUP_SIGNAL.equals(outputLine);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onStartup(ProcessExecutor executor) {
      try {
        // If there is a startup task, execute it.
        if (startupTask != null) {
          executor.execute(new JavaSubmission<>((Callable<Integer> & Serializable) () -> {
            startupTask.run();
            return null;
          }));
        }
      } catch (Exception e) {
        // Ignore it.
      }
    }

    @Override
    public boolean terminateGracefully(ProcessExecutor executor) {
      AtomicBoolean success = new AtomicBoolean(false);
      executor.execute(new SimpleSubmission(new SimpleCommand(JavaProcess.STOP_REQUEST,
          (c, l) -> {
            success.set(JavaProcess.STOP_SIGNAL.equals(l));
            return true;
          }, (c, l) -> true)));
      return success.get();
    }

    @Override
    public Charset getEncoding() {
      return StandardCharsets.UTF_8;
    }

  }

  /**
   * An implementation of {@link net.viktorc.pp4j.api.Submission} for serializable {@link java.util.concurrent.Callable} instances to submit
   * in Java process. It serializes, encodes, and sends the <code>Callable</code> to the process for execution. It also looks for the
   * serialized and encoded return value of the <code>Callable</code>, and for a serialized and encoded {@link java.lang.Throwable} instance
   * output to the stderr stream in case of an error.
   *
   * @param <T> The serializable return type variable of the <code>Callable</code>
   * @param <S> A serializable <code>Callable</code> instance with the return type <code>T</code>.
   * @author Viktor Csomor
   */
  private static class JavaSubmission<T extends Serializable, S extends Callable<T> & Serializable>
      implements Submission<T> {

    final S task;
    final String command;
    volatile T result;
    volatile Throwable error;

    /**
     * Creates a submission for the specified {@link java.util.concurrent.Callable}.
     *
     * @param task The task to execute.
     * @throws IOException If the encoding of the serialized runnablePart fails.
     * @throws NotSerializableException If some object to be serialized does not implement the {@link java.io.Serializable} interface.
     */
    JavaSubmission(S task)
        throws IOException {
      this.task = task;
      command = Conversion.toString(task);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Command> getCommands() {
      return Collections.singletonList(new SimpleCommand(command, (c, l) -> {
        try {
          if (l.startsWith(JavaProcess.ERROR_PREFIX)) {
            error = (Throwable) Conversion.toObject(l.substring(
                JavaProcess.ERROR_PREFIX.length()));
          } else if (l.startsWith(JavaProcess.RESULT_PREFIX)) {
            result = (T) Conversion.toObject(l.substring(
                JavaProcess.RESULT_PREFIX.length()));
          } else {
            return false;
          }
          return true;
        } catch (Exception e) {
          throw new ProcessException(e);
        }
      }, (c, l) -> true /* It cannot happen, as stderr is redirected. */));
    }

    @Override
    public T getResult() throws ExecutionException {
      if (error != null) {
        throw new ExecutionException(error);
      }
      return result;
    }

    @Override
    public String toString() {
      return task.toString();
    }

  }

  /**
   * An implementation of the {@link java.util.concurrent.Future} interface for wrapping a <code>Future</code> instance into a
   * <code>Future</code> object with a return type that is a sub-type of that of the wrapped instance.
   *
   * @param <T> The return type of the original <code>Future</code> instance.
   * @param <S> A subtype of <code>T</code>; the return type of the wrapper <code>Future</code> instance.
   * @author Viktor Csomor
   */
  private static class CastFuture<T, S extends T> implements Future<T> {

    final Future<S> origFuture;

    /**
     * Constructs the wrapper object for the specified <code>Future</code> instance.
     *
     * @param origFuture The  <code>Future</code> instance to wrap.
     */
    CastFuture(Future<S> origFuture) {
      this.origFuture = origFuture;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return origFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return origFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
      return origFuture.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      return origFuture.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return origFuture.get(timeout, unit);
    }

  }

  /**
   * A wrapper class implementing the {@link java.util.concurrent.Callable} interface for turning a serializable
   * <code>Callable</code> instance with a not explicitly serializable return type into a serializable
   * <code>Callable</code> instance with an explicitly serializable return type.
   *
   * @param <T> The serializable return type.
   * @param <S> The serializable <code>Callable</code> implementation with a not explicitly serializable return type.
   * @author Viktor Csomor
   */
  private static class SerializableCallable<T extends Serializable, S extends Callable<? super T> & Serializable>
      implements Callable<T>, Serializable {

    static final long serialVersionUID = -5418713087898561239L;

    final Callable<T> callable;
    final Runnable runnablePart;

    /**
     * Constructs a serializable <code>Callable</code> instance with a serializable return type based on the specified serializable
     * <code>Callable</code> instance with a not explicitly serializable return type.
     *
     * @param callable The <code>Callable</code> to wrap.
     * @param runnablePart The optional <code>Runnable</code> part of the <code>Callable</code> instance in case it consists of a
     * <code>Runnable</code> and a return object.
     */
    @SuppressWarnings("unchecked")
    SerializableCallable(S callable, Runnable runnablePart) {
      this.callable = (Callable<T> & Serializable) callable;
      this.runnablePart = runnablePart;
    }

    /**
     * Constructs a serializable <code>Callable</code> instance with a serializable return type based on the specified serializable
     * <code>Callable</code> instance with a not explicitly serializable return type.
     *
     * @param callable The <code>Callable</code> to wrap.
     */
    SerializableCallable(S callable) {
      this(callable, null);
    }

    @Override
    public T call() throws Exception {
      return callable.call();
    }

  }

}