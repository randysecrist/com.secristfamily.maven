package com.secristfamily.maven.plugin;

import static com.secristfamily.maven.plugin.FileUtils.readFile;

import java.io.File;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;

public class ScriptLoader {
  /**
   * The pre-installation script.
   * @parameter
   */
  private String preinstall;
  
  /**
   * The post-installation script.
   * @parameter
   */
  private String postinstall;
  
  /**
   * The installation script.
   * @parameter
   */
  private String install;
  
  /**
   * The pre-removal script.
   * @parameter
   */
  private String preremove;
  
  /**
   * The post-removal script.
   * @parameter
   */
  private String postremove;
  
  /**
   * The verification script.
   * @parameter
   */
  private String verify;
  
  /**
   * The clean script.
   * @parameter
   */
  private String clean;
  
  /**
   * Class Logger
   */
  private Log log;
  
  protected ScriptLoader() {
    super();
  }
  
  protected ScriptLoader(Log log) {
    this.log = log;
  }
  
  private Log getLog() {
    return log;
  }
  
  protected void init(Map<String,String> params) {
    String pombr = params.get("pombr");
    String preLoc = System.getProperty("pre", params.get("pre"));
    String postLoc = System.getProperty("post", params.get("post"));
    String installLoc = System.getProperty("install", params.get("install"));
    String preRemLoc = System.getProperty("preun", params.get("preun"));
    String postRemLoc = System.getProperty("postun", params.get("postun"));
    String verLoc = System.getProperty("verifyscript", params.get("verifyscript"));
    String cleanLoc = System.getProperty("clean", params.get("clean"));
    
    try {
      preinstall = (preLoc != null) ? readFile(new File(pombr, preLoc)) : readFile(getClass().getResourceAsStream("/scripts/preInstall.sh"));
      postinstall = (postLoc != null) ? readFile(new File(pombr, postLoc)) : readFile(getClass().getResourceAsStream("/scripts/postInstall.sh"));
      install = (installLoc != null) ? readFile(new File(pombr, installLoc)) : readFile(getClass().getResourceAsStream("/scripts/install.sh"));
      preremove = (preRemLoc != null) ? readFile(new File(pombr, preRemLoc)) : readFile(getClass().getResourceAsStream("/scripts/preRemove.sh"));
      postremove = (postRemLoc != null) ? readFile(new File(pombr, postRemLoc)) : readFile(getClass().getResourceAsStream("/scripts/postRemove.sh"));
      verify = (verLoc != null) ? readFile(new File(pombr, verLoc)) : readFile(getClass().getResourceAsStream("/scripts/verify.sh"));
      clean = (cleanLoc != null) ? readFile(new File(pombr, cleanLoc)) : readFile(getClass().getResourceAsStream("/scripts/clean.sh"));
      
      Log log = getLog();
      if (log != null && log.isInfoEnabled()) {
        getLog().info("Scripts Located:");
        getLog().info("%pre");  getLog().info( (preinstall != null) ? "\n" + preinstall : "NULL");
        getLog().info("%post");  getLog().info( (postinstall != null) ? "\n" + postinstall : "NULL");
        getLog().info("%install");  getLog().info( (install != null) ? "\n" + install : "NULL");
        getLog().info("%preun");  getLog().info( (preremove != null) ? "\n" + preremove : "NULL");
        getLog().info("%postun");  getLog().info( (postremove != null) ? "\n" + postremove : "NULL");
        getLog().info("%verifyscript");  getLog().info( (verify != null) ? "\n" + verify : "NULL");
        getLog().info("%clean");  getLog().info( (clean != null) ? "\n" + clean : "NULL");
      }
      
      searchReplace(params);
    }
    catch (Throwable t) {
      t.printStackTrace();
    }
  }
  
  private void searchReplace(Map params) {
    // MVN Macro Support
    // %setup -q -n ${install_dir}
  }

  public String getPreInstall() {
    return preinstall;
  }

  public String getPostInstall() {
    return postinstall;
  }

  public String getInstall() {
    return install;
  }

  public String getPreRemove() {
    return preremove;
  }

  public String getPostRemove() {
    return postremove;
  }

  public String getVerify() {
    return verify;
  }

  public String getClean() {
    return clean;
  }

}
