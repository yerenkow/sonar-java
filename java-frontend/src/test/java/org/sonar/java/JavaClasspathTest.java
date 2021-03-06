/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.config.Settings;
import org.sonar.squidbridge.api.AnalysisException;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

public class JavaClasspathTest {

  private DefaultFileSystem fs;
  private Settings settings;
  private JavaClasspath javaClasspath;

  @Before
  public void setUp() throws Exception {
    fs = new DefaultFileSystem(new File("src/test/files/classpath/"));
    DefaultInputFile inputFile = new DefaultInputFile("","foo.java");
    inputFile.setLanguage("java");
    inputFile.setType(InputFile.Type.MAIN);
    fs.add(inputFile);
    settings = new Settings();
  }

  @Test
  public void properties() throws Exception {
    assertThat(JavaClasspathProperties.getProperties()).hasSize(4);
  }

  @Test
  public void when_property_not_defined_project_classpath_null_getElements_should_be_empty() {
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).isEmpty();
  }

  @Test
  public void setting_binary_prop_should_fill_elements() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_BINARIES, "bin");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getBinaryDirs()).hasSize(1);
    assertThat(javaClasspath.getElements()).hasSize(1);
    assertThat(javaClasspath.getElements().get(0)).exists();
  }

  @Test
  public void setting_library_prop_should_fill_elements() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "lib/hello.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getBinaryDirs()).isEmpty();
    assertThat(javaClasspath.getElements()).hasSize(1);
    assertThat(javaClasspath.getElements().get(0)).exists();
  }

  @Test
  public void absolute_file_name_should_be_resolved() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, new File("src/test/files/classpath/lib/hello.jar").getAbsolutePath());
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(1);
    assertThat(javaClasspath.getElements().get(0)).exists();
  }

  @Test
  public void directory_specified_for_library_should_find_jars() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "lib");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(3);
    assertThat(javaClasspath.getElements().get(0)).exists();
    assertThat(javaClasspath.getElements().get(1)).exists();
    assertThat(javaClasspath.getElements().get(2)).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("lib","hello.jar", "world.jar");
  }

  @Test
  public void libraries_should_accept_path_ending_with_wildcard() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "lib/*");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(2);
    assertThat(javaClasspath.getElements().get(0)).exists();
    assertThat(javaClasspath.getElements().get(1)).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar", "world.jar");
  }

  @Test
  public void libraries_should_accept_relative_paths() throws Exception {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "../../files/classpath/lib/*.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(2);
    File jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar","world.jar");
  }

  @Test
  public void libraries_should_accept_relative_paths_with_wildcard() throws Exception {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "../../files/**/lib");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(5);
    File jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar","world.jar", "lib", "lib", "hello.jar");
  }

  @Test
  public void libraries_should_accept_path_ending_with_wildcard_jar() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "lib/h*.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(1);
    File jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(jar.getName()).isEqualTo("hello.jar");

    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "lib/*.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(2);
    jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar","world.jar");

  }

  @Test
  public void directory_wildcard_should_be_resolved() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "**/*.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(2);
    File jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar", "world.jar");
  }

  @Test
  public void both_path_separator_should_be_supported_on_one_JVM() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "**/*.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(2);
    File jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar","world.jar");
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "**\\*.jar");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(2);
    jar = javaClasspath.getElements().get(0);
    assertThat(jar).exists();
    assertThat(javaClasspath.getElements()).onProperty("name").contains("hello.jar","world.jar");
  }

  @Test
  public void non_existing_resources_should_fail() throws Exception {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "toto/**/hello.jar");
    checkIllegalStateException("No files nor directories matching 'toto/**/hello.jar'");
  }

  @Test
  public void deprecated_properties_set_should_fail_the_analysis() throws Exception {
    settings.setProperty("sonar.binaries", "bin");
    settings.setProperty("sonar.libraries", "hello.jar");
    try {
      javaClasspath = createJavaClasspath();
      javaClasspath.getElements();
      fail("Exception should have been raised");
    }catch (AnalysisException ise) {
      assertThat(ise.getMessage()).isEqualTo("sonar.binaries and sonar.libraries are not supported since version 4.0 of sonar-java-plugin, please use sonar.java.binaries and sonar.java.libraries instead");
    }
  }

  @Test
  public void libraries_should_read_dir_of_class_files() {
    fs = new DefaultFileSystem(new File("src/test/files/"));
    DefaultInputFile inputFile = new DefaultInputFile("","foo.java");
    inputFile.setLanguage("java");
    inputFile.setType(InputFile.Type.MAIN);
    fs.add(inputFile);
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "classpath");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(3);
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "classpath/");
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).hasSize(3);
  }

  @Test
  public void parent_module_should_not_validate_sonar_libraries() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_LIBRARIES, "non-existing.jar");
    javaClasspath = createJavaClasspath();
    checkIllegalStateException("No files nor directories matching 'non-existing.jar'");
  }

  @Test
  public void sonar_binaries_should_not_check_for_existence_of_files_when_no_sources() throws Exception {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_BINARIES, "toto/**/hello.jar");
    fs = new DefaultFileSystem(new File("src/test/files/classpath/"));
    DefaultInputFile inputFile = new DefaultInputFile("", "plop.java");
    inputFile.setType(InputFile.Type.TEST);
    inputFile.setLanguage("java");
    fs.add(inputFile);
    javaClasspath = createJavaClasspath();
    assertThat(javaClasspath.getElements()).isEmpty();
  }

  @Test
  public void invalid_sonar_java_binaries_should_fail_analysis() {
    settings.setProperty(JavaClasspathProperties.SONAR_JAVA_BINARIES, "dummyDir");
    checkIllegalStateException("No files nor directories matching 'dummyDir'");
  }

  private void checkIllegalStateException(String message) {
    try {
      javaClasspath = createJavaClasspath();
      javaClasspath.getElements();
      fail("Exception should have been raised");
    }catch (IllegalStateException ise) {
      assertThat(ise.getMessage()).isEqualTo(message);
    }
  }

  private JavaClasspath createJavaClasspath() {
    return new JavaClasspath(settings, fs);
  }
}
