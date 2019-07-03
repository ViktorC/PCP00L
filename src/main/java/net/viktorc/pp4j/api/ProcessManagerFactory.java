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
 * A functional interface that defines a method for creating new instances of an implementation of the {@link
 * net.viktorc.pp4j.api.ProcessManager} interface.
 *
 * @author Viktor Csomor
 */
public interface ProcessManagerFactory {

  /**
   * Constructs and returns a new instance of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface.
   *
   * @return A new instance of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface.
   */
  ProcessManager newProcessManager();

}