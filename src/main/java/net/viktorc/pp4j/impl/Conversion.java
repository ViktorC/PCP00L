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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * A simple utility class for converting objects into strings and vice versa platform-independently using Base64 encoding and standard Java
 * serialization.
 *
 * @author Viktor Csomor
 */
class Conversion {

  /**
   * Only static methods.
   */
  private Conversion() {
  }

  ;

  /**
   * Serializes the specified object into a string and encodes it using Base64.
   *
   * @param o The object to serialize and encode.
   * @return The serialized and encoded object as a string.
   * @throws IOException If the serialization fails.
   * @throws NotSerializableException If some object to be serialized does not implement the {@link java.io.Serializable} interface.
   */
  static String toString(Object o) throws IOException {
    try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOutput = new ObjectOutputStream(byteArrayOut)) {
      objectOutput.writeObject(o);
      return Base64.getEncoder().encodeToString(byteArrayOut.toByteArray());
    }
  }

  /**
   * Decodes the string and deserializes it into an object.
   *
   * @param s The Base64-encoded string to deserialize.
   * @return The decoded and deserialized string as an object.
   * @throws IOException If the deserialization fails.
   * @throws ClassNotFoundException If the deserialization fails due to the class of the object not having been found.
   */
  static Object toObject(String s) throws IOException, ClassNotFoundException {
    byte[] bytes = Base64.getDecoder().decode(s);
    try (ObjectInputStream objectInput = new ObjectInputStream(
        new ByteArrayInputStream(bytes))) {
      return objectInput.readObject();
    }
  }

}
