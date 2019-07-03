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

import java.io.IOException;
import net.viktorc.pp4j.api.ProcessManager;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface.
 *
 * @author Viktor Csomor
 */
public abstract class AbstractProcessManager implements ProcessManager {

  private final ProcessBuilder builder;
  private final long keepAliveTime;

  /**
   * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder} with the specified maximum life span.
   *
   * @param builder The instance to build the processes with.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is
   * <code>0</code> or less, the life span of the process will not be limited.
   */
  protected AbstractProcessManager(ProcessBuilder builder, long keepAliveTime) {
    this.builder = builder;
    this.keepAliveTime = keepAliveTime;
  }

  @Override
  public Process start() throws IOException {
    return builder.start();
  }

  @Override
  public long getKeepAliveTime() {
    return keepAliveTime;
  }

}