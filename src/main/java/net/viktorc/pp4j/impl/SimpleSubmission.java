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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;

/**
 * A simple implementation of the {@link net.viktorc.pp4j.api.Submission} interface that allows for the specification of the commands to
 * execute and whether the process is to be terminated after the execution of the commands.
 *
 * @author Viktor Csomor
 */
public class SimpleSubmission implements Submission<Object> {

  private final List<Command> commands;

  /**
   * Constructs an instance according to the specified parameters.
   *
   * @param commands A list of commands to execute. It should not contain null references.
   * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
   */
  public SimpleSubmission(List<Command> commands) {
    if (commands == null) {
      throw new IllegalArgumentException("The commands cannot be null.");
    }
    if (commands.isEmpty()) {
      throw new IllegalArgumentException("The commands cannot be empty.");
    }
    if (!commands.stream().filter(Objects::isNull).collect(Collectors.toList()).isEmpty()) {
      throw new IllegalArgumentException("The commands cannot include null references.");
    }
    this.commands = new ArrayList<>(commands);
  }

  /**
   * Constructs an instance according to the specified parameters.
   *
   * @param command A command to execute.
   * @throws IllegalArgumentException If the command is null.
   */
  public SimpleSubmission(Command command) {
    this(Collections.singletonList(command));
  }

  @Override
  public List<Command> getCommands() {
    return new ArrayList<>(commands);
  }

  @Override
  public String toString() {
    return String.format("{commands:[%s]}", String.join(",", commands.stream()
        .map(c -> "\"" + c.getInstruction() + "\"").collect(Collectors.toList())));
  }

}