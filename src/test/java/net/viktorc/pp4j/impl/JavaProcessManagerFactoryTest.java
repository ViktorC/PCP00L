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

import org.junit.Test;

/**
 * A unit test class for {@link JavaProcessManagerFactory}.
 *
 * @author Viktor Csomor
 */
public class JavaProcessManagerFactoryTest extends TestCase {

  @Test
  public void testThrowsIllegalArgumentExceptionIfConfigNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(null);
  }

}
