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

import java.util.ArrayList;
import java.util.List;
import net.viktorc.pp4j.api.Command;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.api.Command} interface that stores all lines output to the process' standard
 * out and standard error in response to the command.
 *
 * @author Viktor Csomor
 */
public abstract class AbstractCommand implements Command {

  private final String instruction;
  private final List<String> stdOutLines;
  private final List<String> stdErrLines;

  /**
   * Constructs an instance holding the specified instruction.
   *
   * @param instruction The instruction to send to the process' standard in as the command.
   */
  protected AbstractCommand(String instruction) {
    this.instruction = instruction;
    stdOutLines = new ArrayList<>();
    stdErrLines = new ArrayList<>();
  }

  /**
   * Returns a list of lines output to the standard out of the underlying process after the instruction has been written to the standard in
   * of the process.
   *
   * @return A list of lines output to the standard out of the underlying process.
   */
  public List<String> getStandardOutLines() {
    return new ArrayList<>(stdOutLines);
  }

  /**
   * Returns a list of lines output to the standard error of the underlying process after the instruction has been written to the standard
   * in of the process.
   *
   * @return A list of lines output to the standard error stream of the underlying process.
   */
  public List<String> getStandardErrLines() {
    return new ArrayList<>(stdErrLines);
  }

  /**
   * Returns a string of the lines output to the standard out of the underlying process after the instruction has been written to the
   * standard in of the process.
   *
   * @return A string of the lines output to the standard out of the underlying process.
   */
  public String getJointStandardOutLines() {
    return String.join("\n", stdOutLines);
  }

  /**
   * Returns a string of the lines output to the standard error of the underlying process after the instruction has been written to the
   * standard in of the process.
   *
   * @return A string of the lines output to the standard error of the underlying process.
   */
  public String getJointStandardErrLines() {
    return String.join("\n", stdErrLines);
  }

  /**
   * Clears the lists holding the lines output to the standard out and standard error streams of the underlying process. Recommended in case
   * the {@link net.viktorc.pp4j.api.Command} instance is reused.
   */
  public void reset() {
    stdOutLines.clear();
    stdErrLines.clear();
  }

  @Override
  public String getInstruction() {
    return instruction;
  }

  @Override
  public final boolean isProcessed(String outputLine, boolean standard) {
    if (standard) {
      stdOutLines.add(outputLine);
    } else {
      stdErrLines.add(outputLine);
    }
    return onOutput(outputLine, standard);
  }

  /**
   * It allows for the processing of the output and is responsible for determining whether the output line denotes the completion of the
   * execution of the command.
   *
   * @param outputLine The new line of output printed to the standard out of the process.
   * @param standard Whether this line has been output to the standard out or to the standard error stream.
   * @return Whether this line of output denotes that the process has finished processing the command.
   */
  protected abstract boolean onOutput(String outputLine, boolean standard);

}