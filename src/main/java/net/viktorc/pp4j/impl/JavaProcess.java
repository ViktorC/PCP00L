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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/**
 * The class whose main method is run as a separate process by the Java process based process pool executor.
 *
 * @author Viktor Csomor
 */
class JavaProcess {

  /**
   * A string for signaling the startup of the Java process; it contains characters that cannot ever appear in Base64 encoded messages and
   * are extremely likely to be contained in any kind of output.
   */
  static final String STARTUP_SIGNAL = "-<._r34Dy_.>-";
  /**
   * A prefix denoting that the response contains the result of the task in serialized form.
   */
  static final String RESULT_PREFIX = "-<._r3ZuLt_.>-";
  /**
   * A prefix denoting that the response contains a serialized <code>Throwable<code> instance that was thrown during the execution of the
   * runnablePart.
   */
  static final String ERROR_PREFIX = "-<._3rR0r_.>-";
  /**
   * The request for the termination of the program.
   */
  static final String STOP_REQUEST = "-<._st0p_.>-";
  /**
   * The response to a termination request.
   */
  static final String STOP_SIGNAL = "-<._st0pP3d_.>-";

  private static final String CHARSET = StandardCharsets.UTF_8.name();

  /**
   * The method executed as a separate process. It listens to its standard in for encoded and serialized {@link
   * java.util.concurrent.Callable} instances, which it decodes, deserializes, and executes on receipt. The return value is normally output
   * to its stdout stream in the form of the serialized and encoded return values of the
   * <code>Callable</code> instances. If an exception occurs, it is serialized, encoded,
   * and output to the stderr stream.
   *
   * @param args They are ignored for now.
   */
  public static void main(String[] args) {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, CHARSET));
        DummyPrintStream dummyOut = new DummyPrintStream();
        DummyPrintStream dummyErr = new DummyPrintStream()) {
      /* As the process' stderr is redirected to the stdout, there is no need for a
       * reference to this stream; neither should the submissions be able to print to
       * stdout through the System.err stream. */
      try {
        System.setErr(dummyErr);
      } catch (SecurityException e) {
        // Ignore it.
      }
      PrintStream out = System.out;
      // Send the startup signal.
      System.out.println(STARTUP_SIGNAL);
      try {
        for (;;) {
          try {
            String line = in.readLine();
            if (line == null) {
              return;
            }
            line = line.trim();
            if (line.isEmpty()) {
              continue;
            }
            if (STOP_REQUEST.equals(line)) {
              System.out.println(STOP_SIGNAL);
              return;
            }
            Object input = Conversion.toObject(line);
            if (input instanceof Callable<?>) {
              Callable<?> c = (Callable<?>) input;
              /* Try to redirect the out stream to make sure that print
               * statements and such do not cause the submission to be assumed
               * done falsely. */
              try {
                System.setOut(dummyOut);
              } catch (SecurityException e) {
                // Ignore it.
              }
              Object output = c.call();
              try {
                System.setOut(out);
              } catch (SecurityException e) {
                // Ignore it.
              }
              System.out.println(RESULT_PREFIX + Conversion.toString(output));
            }
          } catch (Throwable e) {
            try {
              System.setOut(out);
            } catch (SecurityException e1) {
              // Ignore it.
            }
            System.out.println(ERROR_PREFIX + Conversion.toString(e));
          }
        }
      } catch (Throwable e) {
        try {
          System.setOut(out);
        } catch (SecurityException e1) {
          // Ignore it.
        }
        throw e;
      }
    } catch (Throwable e) {
      try {
        System.out.println(ERROR_PREFIX + Conversion.toString(e));
      } catch (Exception e1) {
        // Give up all hope.
      }
    }
  }

  /**
   * A dummy implementation of {@link java.io.PrintStream} that does nothing on <code>write</code>.
   *
   * @author Viktor Csomor
   */
  private static class DummyPrintStream extends PrintStream {

    /**
     * Default constructor.
     */
    DummyPrintStream() {
      super(new OutputStream() {

        @Override
        public void write(int b) {
          // This is why it is called "DummyPrintStream".
        }
      });
    }

  }

}
