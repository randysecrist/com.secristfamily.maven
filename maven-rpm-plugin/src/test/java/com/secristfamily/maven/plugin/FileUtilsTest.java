package com.secristfamily.maven.plugin;

import java.io.File;

import junit.framework.TestCase;

public class FileUtilsTest extends TestCase {
  public void testReadFile() throws Throwable {
    File in = new File("./pom.xml");
    String result = FileUtils.readFile(in);
    assertNotNull(result);
  }
}
