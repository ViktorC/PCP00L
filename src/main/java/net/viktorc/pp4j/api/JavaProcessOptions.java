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

/**
 * An interface for the definition of the timeout of Java processes and the options for the "java" program to enable performance
 * optimization for the forking of JVMs.
 *
 * @author Viktor Csomor
 */
public interface JavaProcessOptions {

  /**
   * Returns the architecture the JVM should use, i.e. 32-bit or 64-bit. If it returns
   * <code>null</code>, the option is not to be set. By default, it returns <code>null</code>.
   *
   * @return The architecture of the JVM.
   */
  default JVMArch getArch() {
    return null;
  }

  /**
   * Returns the type of the JVM, i.e. client or server. If it returns <code>null</code>, the option is not to be set. By default, it
   * returns <code>null</code>.
   *
   * @return The type of the JVM.
   */
  default JVMType getType() {
    return null;
  }

  /**
   * Returns the minimum and hence initial heap size the JVM should use in megabytes. If it returns <code>0</code>, the option is not to be
   * set. By default, it returns <code>0</code>.
   *
   * @return The minimum heap size of the JVM in megabytes.
   */
  default int getInitHeapSizeMb() {
    return 0;
  }

  /**
   * Returns the maximum heap size the JVM should use in megabytes. If it returns
   * <code>0</code>, the option is not to be set. By default, it returns <code>0</code>.
   *
   * @return The maximum heap size of the JVM in megabytes.
   */
  default int getMaxHeapSizeMb() {
    return 0;
  }

  /**
   * Return the max stack size the JVM should use in kilobytes. If it returns <code>0</code>, the option is not to be set. By default, it
   * returns <code>0</code>.
   *
   * @return The max stack size of the JVM in kilobytes.
   */
  default int getStackSizeKb() {
    return 0;
  }

  /**
   * Determines the duration of continuous idleness after which the process is to be terminated. The process is considered idle if it is
   * started up and not processing any submission. If it returns <code>0</code> or less, the life span of the process is not limited. By
   * default, it returns <code>0</code>.
   *
   * @return The number of milliseconds of idleness after which the process is to be terminated.
   */
  default long getKeepAliveTime() {
    return 0;
  }

  /**
   * The definitions of the different possible JVM architectures.
   *
   * @author Viktor Csomor
   */
  enum JVMArch {
    BIT_32, BIT_64;
  }

  /**
   * The definitions of the possible JVM types.
   *
   * @author Viktor Csomor
   */
  enum JVMType {
    CLIENT, SERVER;
  }

}