# PP4J [![Build Status](https://travis-ci.org/ViktorC/PP4J.svg?branch=master)](https://travis-ci.org/ViktorC/PP4J) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=net.viktorc:pp4j&metric=alert_status)](https://sonarcloud.io/dashboard?id=net.viktorc:pp4j) [![Coverage Status](https://coveralls.io/repos/github/ViktorC/PP4J/badge.svg?branch=master)](https://coveralls.io/github/ViktorC/PP4J?branch=master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.viktorc/pp4j/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/net.viktorc/pp4j) [![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt) [![Docs](https://img.shields.io/badge/docs-latest-blue.svg)](http://viktorc.github.io/PP4J/)
While the standard Java libraries support multithreading based concurrency extensively, they do not support effective multiprocessing out of the box. In many situations, multithreading is sufficient and in fact optimal; however, it can lead to complications due to the fact that threads share the address spaces of their parent processes. Multiprocessing ensures that execution units have their own address spaces and that no data is exchanged between them without explicit inter-process communication. This may be useful if a Java application has to a) execute non-thread-safe or non-reentrant code concurrently or b) invoke native code—whether via JNI/JNA or executable wrapper programs—without running the risk of crashing the main JVM. __PP4J__ (Process Pool for Java) is a multiprocessing library for Java that provides a flexible API and implementations of process pools and process executors to help meet the above requirements.

## Java Process Pool
PP4J includes a Java process pool implementation that uses JVM instances to execute tasks in separate processes. This class, `JavaProcessPoolExecutor`, implements the `JavaProcessExecutorService` interface which extends both the `ProcessExecutorService` and `ExecutorService` interfaces. This allows it to be used similarly to the standard Java thread pools with the difference that the tasks submitted must implement the `Serializable` interface. This implicit requirement enables the pool to serialize and encode the tasks before sending them to the JVM instances for execution. The JVM instances return the results of the tasks or the exceptions thrown the same way, which requires the return values of the tasks to be serializable as well.
```java
JavaProcessOptions jvmOptions = new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 8, 256, 60000);
JavaProcessExecutorService jvmPool = new JavaProcessPoolExecutor(jvmOptions, 10, 20, 2, null, false);
```
The code snippet above demonstrates the construction of a `JavaProcessPoolExecutor` instance. The first argument of the constructor is an instance of the `JavaProcessOptions` interface, which allows for the definition of options to use for the JVM instances and for the specification of the timeout value of idle processes. These options include the architecture, type, minimum heap size, maximum heap size, and stack size of the JVM instances. The first argument after the JVM options is the minimum size of the pool. This is the minimum number of JVM instances the process pool will strive to maintain, even if the submission queue is empty. The second argument is the maximum size. The number of JVM instances maintained by the pool is guaranteed to never exceed this value. The third argument is the reserve size. This is the minimum number of available JVM instances the pool will strive to maintain at all times. E.g. if the reserve size is 3, the current pool size is 5, the number of submissions being processed by the pool is 2, and a 3rd submission is submitted to the process pool, the size manager will launch a new JVM, unless the maximum size is 5, to ensure that there will be at least 3 available processes. The second to last argument is an optional serializable `Runnable` task that is executed in every JVM on startup, before they are considered ready for submissions. Finally, the last argument determines whether the pool is to be verbose, i.e. whether events related to the management of the pool are to be logged using [SLF4J](https://www.slf4j.org/). It is important to note that the constructor blocks until the minimum number of JVM processes have successfully started up. Specifying a startup task negatively affects the startup times of the processes; however, it may significantly reduce initial submission execution delays by ensuring that the JVM instances load some of the required classes beforehand. Moreover, the JVM options can be used to limit the heap sizes of the JVM processes, thus enabling the running of a great number of them without taking up too much RAM.
```java
Random rand = new Random();
List<Future<Long>> results = new ArrayList<>();
for (int i = 0; i < 10; i++) {
  Future<Long> future = jvmPool.submit((Callable<Long> & Serializable) () -> {
    Thread.sleep(1000);
    return rand.nextLong();
  });
  results.add(future);
}
for (Future<Long> res : results) {
  System.out.println(res.get());
}
jvmPool.shutdown();
jvmPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
```
Lambda expressions may be defined as serializable by casting them to an intersection of types as shown in the above snippet. Although somewhat slower than multithreading, the execution of the code above still takes only a few milliseconds longer than a second. This example also demonstrates the primary difference between multithreading and multiprocessing, i.e. processes have their own address spaces as opposed to threads. All the invocations of the `nextLong` method of `rand` return the same value as each process has its own copy of the object.

While it is possible to achieve good performance using the Java process pool, the startup and warmup times of the JVM cannot be ignored. The usage of native processes, whenever possible, allows for superior performance. If the need for a process pool is induced by the requirement of the parallel execution of non-thread-safe or non-reentrant native code, pools of native processes are almost always a better choice. They might require the writing of an executable wrapper program, but they eliminate the need for JNI or JNA and their performance blows away that of Java process pools. The following section introduces the flexible API and process pool implementation that `JavaProcessPoolExecutor` is built upon.

## Standard Process Pool
The high level design diagram below sums up the gist of the base process pool of the PP4J library. This process pool maintains a number of processes that implement a communication protocol over their standard streams (possibly to expose methods of a native library). It also accepts textual command submissions that it then delegates to available processes. These submissions implement the communication protocol as well and are responsible for handling the responses of the processes they have been delegated to. Through callback methods, the submissions notify their submitters when the processes are done processing them. In the mean time, if deemed necessary, the pool dynamically adjusts its size to maintain its throughput. It does so via process managers that may or may not need to communicate with the processes. To explain how such a process pool can be set up, the following sections introduce the library's base API. 

![arch](https://user-images.githubusercontent.com/12938964/37467074-9a508dde-285f-11e8-9deb-395de62cd9e0.png)

All process pools of PP4J implement the `ProcessExecutorService` interface. The standard process pool, `ProcessPoolExecutor`, communicates with the processes via their standard streams. Instances of `ProcessPoolExecutor` can be created by invoking the constructor directly or using one of the methods of the `ProcessExecutors` class. The first parameter of both the constructor and the factory methods is an implementation of the `ProcessManagerFactory` functional interface for creating new instances of an implementation of the `ProcessManager` interface. These instances are responsible for specifying the processes and handling their startup and possibly termination. Other parameters of the `ProcessPoolExecutor` constructor include the minimum and maximum size of the pool and its reserve size. The size of the pool is always kept between the minimum pool size and the maximum pool size (both inclusive). Once the process pool is initialized, it accepts commands in the form of implementations of the `Submission` interface which contain one or more `Command` implementations. The submission is assigned to any one of the available processes in the pool. While executing a submission, the process cannot accept further submissions. The `Command` implementation allows for communication with a process via its standard in and standard out/error streams. This implementation specifies the instruction to send to the process' standard in and handles the output generated by the process as a response to the instruction. Moreover, the `Command` implementation also determines when the instruction may be considered processed and when the process is ready for the next instruction. The PP4J library also provides some standard implementations of the `ProcessManager`, `Submission`, and `Command` interfaces to allow for the concise definition of process pooling systems for typical situations.
```java
ProcessManagerFactory processManagerFactory = () -> new AbstractProcessManager(new ProcessBuilder("test.exe"), 60000) {

  @Override
  public boolean startsUpInstantly() {
    return false;
  }

  @Override
  public boolean isStartedUp(String output, boolean standard) {
    return !standard || "hi".equals(output);
  }

  @Override
  public void onStartup(ProcessExecutor executor) {
    Command command = new AbstractCommand("start") {

      @Override
      public boolean generatesOutput() {
        return true;
      }

      @Override
      protected boolean onOutput(String outputLine, boolean standard) {
        return !standard || "ok".equals(outputLine);
      }
    };
    Submission<?> submission = new SimpleSubmission(command)
    executor.execute(submission);
  }

  @Override
  public boolean terminateGracefully(ProcessExecutor executor) {
    AtomicBoolean success = new AtomicBoolean(true);
    Command command = new AbstractCommand("stop") {

      @Override
      public boolean generatesOutput() {
        return true;
      }

      @Override
      protected boolean onOutput(String outputLine, boolean standard) {
        if (!standard) {
          success.set(false);
          return true;
        } else {
          return "bye".equals(outputLine);
        }
      }
    };
    Submission<?> submission = new SimpleSubmission(command);
    executor.execute(submission);
    return success.get();
  }
};
ProcessExecutorService pool = new ProcessPoolExecutor(processManagerFactory, 10, 50, 5, true);
```
In the example above, a process pool for instances of a program called "test.exe" is created. Every time the pool starts a new process, it waits until the message "hi" is output to the process' standard out, signaling that it has started up, then the pool sends the instruction "start" to the process' standard in. The instruction "start" has the process perform some startup activities before it outputs "ok". Once this message is output to the process' standard out, the pool considers the process ready for submissions. Whenever the process needs to be terminated (either due to timing out or having it cancelled after the execution of a submission), the pool tries to terminate the process in an orderly way by sending it the "stop" instruction. If the response to this is "bye", the process is considered terminated. However, if something is printed to the process' error out in response to the "stop" instruction, the process is killed forcibly. As specified by the second argument of the constructor of `AbstractProcessManager`, processes in the pool are terminated after 1 minute of idleness. The pool's minimum size is 10, its maximum size is 50, its reserve size is 5, and it is verbose.
```java
List<Future<?>> futures = new ArrayList<>();
for (int i = 0; i < 30; i++) {
  Thread.sleep(100);
  Command command = new AbstractCommand("process 5") {

    @Override
    public boolean generatesOutput() {
      return true;
    }

    @Override
    protected boolean onOutput(String outputLine, boolean standard) {
      if (standard) {
        if ("ready".equals(outputLine)) {
          System.out.println(getJointStandardOutLines());
          return true;
        }
        return false;
      } else {
        System.out.println(getJointStandardErrLines());
        return true;
      }
    }
  };
  Submission<?> submission = new SimpleSubmission(command);
  futures.add(pool.submit(submission, true));
}
pool.shutdown();
pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
```
Once the pool is initialized, it is sent 30 instructions within 3 seconds. The instruction "process 5" has the process sleep for 5 seconds, printing "in progress" to its standard out every second except for the 5th second, when it prints "ready". This final output signals the completion of the execution of the command. The submissions above also results in the termination of the executing processes as denoted by the second, boolean parameter of the `submit` method of the pool. As the pool receives the submissions, it manages its size according to its minimum, maximum, and reserve size parameters. After the execution of the submissions, the pool is shutdown identically in behaviour to Java thread pools.

While the above example aptly demonstrates the flexibility of the API and `ProcessPoolExecutor`, it is also fairly verbose. However, the definition of PP4J process pools need not be verbose.
```java
ProcessManagerFactory processManagerFactory = () -> new SimpleProcessManager(new ProcessBuilder("bash"));
ProcessExecutorService pool = ProcessExecutors.newFixedProcessPool(processManagerFactory, 10);
for (int i = 0; i < 10; i++) {
  Command command = new SimpleCommand("sleep 5; echo $USER",
      (c, o) -> {
        System.out.println(o);
        return true;
      },
      (c, o) -> true);
  Submission<?> submission = new SimpleSubmission(command);
  pool.submit(submission);
}
pool.shutdown();
pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
```
As demonstrated by the above code snippet, the `ProcessExecutors`, `SimpleProcessManager`, `SimpleSubmission`, and `SimpleCommand` convenience classes allow for the concise definition of process pools and tasks to benefit from multiprocessing.

## Process Executor
Besides the process pools, PP4J also provides a standard implementation of the `ProcessExecutor` interface, `SimpleProcessExecutor` for the running of single processes without pooling. This, as presented below, allows for the synchronous execution of submissions in a single separate process with ease. In fact, `ProcessPoolExecutor` is based on the concept of pooling instances of a similar implementation of the `ProcessExecutor` interface.
```java
ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe");
ProcessManager processManager = new SimpleProcessManager(processBuilder);
try (SimpleProcessExecutor executor = new SimpleProcessExecutor(processManager)) {
  SimpleCommand command = new SimpleCommand("netstat & echo netstat done",
      (c, o) -> "netstat done".equals(o),
      (c, o) -> false);
  executor.start();
  executor.execute(new SimpleSubmission(command));
  System.out.println(command.getJointStandardOutLines());
}
```
