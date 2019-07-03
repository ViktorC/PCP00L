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

/**
 * An exception thrown if an unexpected error occurs while running or interacting with a process that solicits the instantaneous termination
 * of the process and possibly the entire pool.
 *
 * @author Viktor Csomor
 */
public class ProcessException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  /**
   * Constructs an exception with the specified message.
   *
   * @param message The exception message.
   */
  public ProcessException(String message) {
    super(message);
  }

  /**
   * Constructs a wrapper for the specified exception.
   *
   * @param e The source exception.
   */
  public ProcessException(Exception e) {
    super(e);
  }

}