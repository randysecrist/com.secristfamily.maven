package com.secristfamily.maven.plugin;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;

import junit.framework.TestCase;

public class ScriptLoaderTest extends TestCase {
  public void testInit() throws MojoExecutionException {
    Map params = new HashMap();

    ScriptLoader sl = new ScriptLoader();
    sl.init(params);
  }
}
