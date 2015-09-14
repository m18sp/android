/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser;

import com.intellij.pom.java.LanguageLevel;
import org.junit.Test;

import static com.android.tools.idea.gradle.dsl.parser.GradleLanguageLevel.parseFromGradleString;
import static com.android.tools.idea.gradle.dsl.parser.GradleLanguageLevel.convertToGradleString;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link GradleLanguageLevel}.
 */
public class GradleLanguageLevelTest {
  @Test
  public void testParseFromGradleString() {
    // Different version
    assertEquals(LanguageLevel.JDK_1_4, parseFromGradleString("1.4"));
    assertEquals(LanguageLevel.JDK_1_5, parseFromGradleString("1.5"));
    assertEquals(LanguageLevel.JDK_1_6, parseFromGradleString("1.6"));

    // Different format
    assertEquals(LanguageLevel.JDK_1_6, parseFromGradleString("'1.6'"));
    assertEquals(LanguageLevel.JDK_1_6, parseFromGradleString("VERSION_1_6"));
    assertEquals(LanguageLevel.JDK_1_6, parseFromGradleString("JavaVersion.VERSION_1_6"));
  }

  @Test
  public void testConvertToGradleString() {
    // Different version
    assertEquals("1.4", convertToGradleString(LanguageLevel.JDK_1_4, "1.3"));
    assertEquals("1.5", convertToGradleString(LanguageLevel.JDK_1_5, "1.3"));
    assertEquals("1.6", convertToGradleString(LanguageLevel.JDK_1_6, "1.3"));

    // Different format
    assertEquals("'1.6'", convertToGradleString(LanguageLevel.JDK_1_6, "'1.3'"));
    assertEquals("VERSION_1_6", convertToGradleString(LanguageLevel.JDK_1_6, "VERSION_1_3"));
    assertEquals("JavaVersion.VERSION_1_6", convertToGradleString(LanguageLevel.JDK_1_6, "JavaVersion.VERSION_1_3"));
  }
}
