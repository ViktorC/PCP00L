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

import java.util.function.Consumer;
import net.viktorc.pp4j.api.ProcessExecutor;

/**
 * A simple sub-class of the {@link net.viktorc.pp4j.impl.AbstractProcessManager} abstract class. It assumes that the process is immediately
 * started up as soon as it is launched (without having to wait for a certain output denoting that the process is ready), it has the process
 * forcibly killed every time it needs to be terminated due to timing out or not being reusable, and it implements no callback for when the
 * process terminates.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessManager extends AbstractProcessManager {

  private final Consumer<ProcessExecutor> onStartup;

  /**
   * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder} with the specified maximum life span.
   *
   * @param builder The instance to build the processes with.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is
   * <code>0</code> or less, the life span of the process will not be limited.
   * @param onStartup A consumer that is called after the process started up to allow for the execution of commands to 'prepare' the process
   * for the pool. If it is null, no preprocessing is performed.
   */
  public SimpleProcessManager(ProcessBuilder builder, long keepAliveTime,
      Consumer<ProcessExecutor> onStartup) {
    super(builder, keepAliveTime);
    this.onStartup = onStartup;
  }

  /**
   * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder} with the specified maximum life span.
   *
   * @param builder The instance to build the processes with.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is
   * <code>0</code> or less, the life span of the process will not be limited.
   */
  public SimpleProcessManager(ProcessBuilder builder, long keepAliveTime) {
    this(builder, keepAliveTime, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param onStartup A consumer that is called after the process started up to allow for the execution of commands to 'prepare' the process
   * for the pool.
   */
  public SimpleProcessManager(ProcessBuilder builder, Consumer<ProcessExecutor> onStartup) {
    this(builder, 0, onStartup);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   */
  public SimpleProcessManager(ProcessBuilder builder) {
    this(builder, 0);
  }

  @Override
  public boolean startsUpInstantly() {
    return true;
  }

  @Override
  public boolean isStartedUp(String outputLine, boolean standard) {
    return true;
  }

  @Override
  public void onStartup(ProcessExecutor executor) {
    if (onStartup != null) {
      onStartup.accept(executor);
    } else {
      super.onStartup(executor);
    }
  }

}