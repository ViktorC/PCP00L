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

import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link SimpleJavaProcessConfig}.
 *
 * @author Viktor Csomor
 */
public class SimpleJavaProcessConfigTest extends TestCase {

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfJavaLauncherCommandNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(null);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfJavaLauncherCommandEmpty() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig("");
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfInitHeapSizeNegative() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(-1, 1, 1);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfInitHeapSizeZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(0, 1, 1);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfMaxHeapSizeNegative() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(1, -1, 1);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfMaxHeapSizeZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(1, 0, 1);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfStackSizeNegative() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(1, 1, -1);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfStackSizeZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(1, 1, 0);
  }

  @Test
  public void testConstructorThrowsIllegalArgumentExceptionIfInitHeapSizeGreaterThanMaxHeapSize() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleJavaProcessConfig(2, 1, 0);
  }

  @Test
  public void testAdditionalClassPathsIncludedInJointClassPath() {
    String additionalClassPath1 = "home/classes";
    String additionalClassPath2 = "somewhere_else/classes";
    SimpleJavaProcessConfig config = new SimpleJavaProcessConfig(SimpleJavaProcessConfig.DEFAULT_JAVA_LAUNCHER_COMMAND,
        additionalClassPath1, additionalClassPath2);
    Optional<String> optionalClassPath = config.getClassPath();
    Assert.assertTrue(optionalClassPath.isPresent());
    String classPath = optionalClassPath.get();
    Assert.assertTrue(classPath.contains(additionalClassPath1));
    Assert.assertTrue(classPath.contains(additionalClassPath2));
  }

}
