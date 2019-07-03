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
 * An interface that defines methods that provide a textual command instruction and allow for the processing of the outputs of the process
 * in response to the instruction. Besides possible processing activities, the {@link #isProcessed(String, boolean)} method is also
 * responsible for determining when the process finished processing the command. E.g. if a process takes the command "go" which triggers the
 * execution of a long-running task, and it prints "ready" to its standard out stream once the task is completed, the method should only
 * return true if the output "ready" has been written to the standard out, in any other case, it should return false (unless perhaps an
 * error message is printed to the standard error stream). The interface also defines a method that is called before the execution of
 * chained commands with the previous command as its parameter to determine whether the current command should be executed based on the
 * results of the previous commands.
 *
 * @author Viktor Csomor
 */
public interface Command {

  /**
   * Returns the instruction to write to the process' standard in.
   *
   * @return The instruction to write to the process' standard in.
   */
  String getInstruction();

  /**
   * A method called before the issuing of the instruction. It denotes whether the command is expected to generate output. If it returns
   * <code>false</code>, the command is considered processed as soon as it is written to the process' standard in and therefore the process
   * is considered ready for new commands right away. If it returns <code>true</code>, the {@link #isProcessed(String, boolean)} method
   * determines when the command is deemed processed.
   *
   * @return Whether the executing process is expected to generate output in response to the command.
   */
  boolean generatesOutput();

  /**
   * A method called every time a new line is printed to the standard out or standard error stream of the process after the command has been
   * sent to its standard in until the method returns <code>true</code>.
   *
   * @param outputLine The new line of output printed to the standard out of the process.
   * @param standard Whether this line has been output to the standard out or to the standard error stream.
   * @return Whether this line of output denotes that the process has finished processing the command. The {@link
   * net.viktorc.pp4j.api.ProcessExecutor} instance executing the command will not accept new commands until the processing of the command
   * is completed.
   */
  boolean isProcessed(String outputLine, boolean standard);

}