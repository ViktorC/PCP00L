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

import java.io.File;
import java.net.URISyntaxException;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;

/**
 * A utility class for testing.
 *
 * @author Viktor Csomor
 */
public class TestUtils {

  public static final String TEST_TITLE_FORMAT = "%nTest %d%n" +
      "-------------------------------------------------%n";

  /**
   * Not initializable.
   */
  private TestUtils() {
  }

  /**
   * Returns a {@link java.io.File} instance representing the test executable.
   *
   * @return A <code>File</code> pointing to the test executable.
   */
  public static File getExecutable() {
    // Support testing both on Linux and Windows.
    boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
    try {
      File file = new File(ClassLoader.getSystemClassLoader()
          .getResource(windows ? "win/testwrapper.exe" : "linux/testwrapper")
          .toURI().getPath());
      file.setExecutable(true);
      return file;
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a {@link java.io.File} instance representing the test library.
   *
   * @return A <code>File</code> pointing to the test library.
   */
  public static File getLibrary() {
    // Support testing both on Linux and Windows.
    boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
    try {
      File file = new File(ClassLoader.getSystemClassLoader()
          .getResource(windows ? "win/test.dll" : "linux/libtest.so")
          .toURI().getPath());
      file.setExecutable(true);
      return file;
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a test {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance.
   *
   * @return A test <code>ProcessManagerFactory</code> instance.
   * @throws URISyntaxException If the path to the executable cannot be resolved.
   */
  public static ProcessManagerFactory createTestProcessManagerFactory()
      throws URISyntaxException {
    return new TestProcessManagerFactory();
  }

  /**
   * A simple test process manager factory for starting process managers for the test program.
   *
   * @author Viktor Csomor
   */
  private static class TestProcessManagerFactory implements ProcessManagerFactory {

    ProcessBuilder builder;

    /**
     * Constructs an instance for creating process managers.
     *
     * @throws URISyntaxException If the path to the test executable cannot be resolved.
     */
    TestProcessManagerFactory() throws URISyntaxException {
      builder = new ProcessBuilder(getExecutable().getAbsolutePath());
    }

    @Override
    public ProcessManager newProcessManager() {
      return new SimpleProcessManager(builder) {

        @Override
        public boolean startsUpInstantly() {
          return false;
        }

        @Override
        public boolean isStartedUp(String outputLine, boolean standard) {
          return standard && "hi".equals(outputLine);
        }
      };
    }

  }

}
