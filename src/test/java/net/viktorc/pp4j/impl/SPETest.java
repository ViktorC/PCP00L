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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Test cases for the {@link net.viktorc.pp4j.impl.SimpleProcessExecutor}.
 *
 * @author Viktor
 */
public class SPETest {

  @Rule
  public final ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void test01() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(
        TestUtils.createTestProcessManagerFactory().newProcessManager())) {
      SimpleCommand command = new SimpleCommand("process 3",
          (c, o) -> "ready".equals(o), (c, o) -> false);
      executor.start();
      executor.execute(new SimpleSubmission(command));
      Assert.assertFalse(executor.stop(false));
      Assert.assertTrue(executor.stop(true));
      Assert.assertTrue(command.getJointStandardOutLines()
          .contains("in progress\nin progress\nready"));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void test02() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(
        TestUtils.createTestProcessManagerFactory().newProcessManager())) {
      executor.start();
      exceptionRule.expect(IllegalStateException.class);
      executor.start();
    }
  }

  @Test
  public void test03() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(
        TestUtils.createTestProcessManagerFactory().newProcessManager())) {
      Assert.assertFalse(executor.isRunning());
      executor.start();
      Assert.assertTrue(executor.isRunning());
    }
  }

  @Test
  public void test04() throws Exception {
    Thread t = null;
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(
        TestUtils.createTestProcessManagerFactory().newProcessManager())) {
      t = new Thread(() -> {
        try {
          executor.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });
      executor.start();
      t.start();
      Assert.assertTrue(t.isAlive());
      executor.stop(true);
      executor.join();
      Thread.sleep(20);
      Assert.assertFalse(t.isAlive());
    }
  }

}
