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

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * An interface that defines methods necessary for the submission and execution of commands in {@link net.viktorc.pp4j.api.ProcessExecutor}
 * instances. It also defines methods to call once the processing of the submitted commands has started or finished which are by default
 * no-operations.
 *
 * @param <T> The return type associated with the submission.
 * @author Viktor Csomor
 */
public interface Submission<T> {

  /**
   * Returns the commands to execute.
   *
   * @return A list of the commands to execute.
   */
  List<Command> getCommands();

  /**
   * Returns the result of the submission. By default, it returns <code>null</code>.
   *
   * @return The object representing the result of the submission or <code>null</code> if no result is associated with the submission.
   * @throws ExecutionException if an error occurred while executing the submission.
   */
  default T getResult() throws ExecutionException {
    return null;
  }

  /**
   * A method that is executed once the processing of the submitted commands has begun.
   */
  default void onStartedProcessing() {
  }

  /**
   * A method to execute once the processing of the submitted commands has completed.
   */
  default void onFinishedProcessing() {
  }

}