package com.secristfamily.maven.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

public class RPMBuild {

  private Log log;
  private File workarea;

  public RPMBuild(File workarea, Log log) {
    this.workarea = workarea;
    this.log = log;
  }

  private Log getLog() {
    return log;
  }

  /**
   * Build the structure of the work area.
   * 
   * @throws MojoFailureException
   *             if a directory cannot be built
   */
  protected void buildWorkArea() throws MojoFailureException {
    final String[] topdirs = { "BUILD", "RPMS", "SOURCES", "SPECS", "SRPMS" };

    // Build the top directory
    if (!workarea.exists()) {
      getLog().info("Creating directory " + workarea.getAbsolutePath());
      if (!workarea.mkdirs()) {
        throw new MojoFailureException("Unable to create directory "
            + workarea.getAbsolutePath());
      }
    }

    // Build each directory in the top directory
    for (int i = 0; i < topdirs.length; i++) {
      File d = new File(workarea, topdirs[i]);
      if (!d.exists()) {
        getLog().info("Creating directory " + d.getAbsolutePath());
        if (!d.mkdir()) {
          throw new MojoFailureException(
              "Unable to create directory " + d.getAbsolutePath());
        }
      }
    }
  }
  
  protected void writeSpecFile(String filename, Map<String,String> params, String[] requires) throws MojoExecutionException {
    File f = new File(workarea, "SPECS");
    File specf = new File(f, filename + ".spec");
    PrintWriter spec = null;
    try {
      getLog().info("Creating SPEC File: " + specf.getAbsolutePath());
      spec = new PrintWriter(new FileWriter(specf));
      this.writeSpecHeader(spec, params, requires);
      this.writeSpecBody(spec, params);
    }
    catch (Throwable t) {
      throw new MojoExecutionException("Unable to write "
          + specf.getAbsolutePath(), t);
    }
    finally {
      if (spec != null) {
        spec.close();
      }
    }
  }

  private void writeSpecHeader(PrintWriter spec, Map<String,String> params, String[] requires) {
    if (params.containsKey("defines")) {
      String definesKey = params.get("defines");
      String[] defines = definesKey.split(",");
      for (String define : defines) {
        spec.printf("%%define %s\n", define);
      }
    }

    spec.printf("Name: %s\n", params.get("name"));  // added automagically
    spec.printf("Version: %s\n", params.get("version"));  // added automagically
    spec.printf("Release: %s\n", params.get("release"));  // added automagically
    if (params.containsKey("Summary")) {
      spec.printf("Summary: %s\n", params.get("Summary"));
    }
    if (params.containsKey("License")) {
      spec.printf("License: %s\n", params.get("License"));
    }
    if (params.containsKey("Distribution")) {
      spec.printf("Distribution: %s\n", params.get("Distribution"));
    }
    if (params.containsKey("Vendor")) {
      spec.printf("Vendor: %s\n", params.get("Vendor"));
    }
    if (params.containsKey("URL")) {
      spec.printf("URL: %s\n", params.get("URL"));
    }
    if (params.containsKey("Group")) {
      spec.printf("Group: %s\n", params.get("Group"));
    }
    if (params.containsKey("Packager")) {
      spec.printf("Packager: %s\n", params.get("Packager"));
    }
    if (params.containsKey("Prefix")) {  // added automagically
      spec.printf("Prefix: %s\n", params.get("Prefix"));
    }
    if (params.containsKey("BuildRoot")) {  // added automagically
      spec.printf("BuildRoot: %s\n", params.get("BuildRoot"));
    }
    if (params.containsKey("BuildArch")) {
      spec.printf("BuildArch: %s\n", params.get("BuildArch"));
    }
    for (String req : requires) {
      spec.printf("Requires: %s\n", req);
    }

    spec.printf("Source: %%{name}.tgz\n");
    spec.printf("BuildRoot: %%{_tmppath}/%%{name}-%%{version}-%%{release}\n");

    if (params.containsKey("description")) {
      spec.printf("\n%%description\n%s\n", params.get("description"));
    }
  }

  private void writeSpecBody(PrintWriter spec, Map<String,String> params) {
    ScriptLoader loader = new ScriptLoader(getLog());
    loader.init(params);

    // PREP
    String component_name = RPMMojo.getComponentName(params);
    String install_dir = "/" + RPMMojo.getInstallDir(params);
    if (params.containsKey("skip-repack")) {
      spec.printf("\n%%define __spec_install_post /usr/lib/rpm/brp-compress /usr/lib/rpm/brp-strip\n");
    }
    spec.printf("%%define component_name %s\n", component_name);
    spec.printf("%%define install_dir %s\n", install_dir);
    spec.printf("%%define pom_buildroot %s\n", RPMMojo.getPOMBR());
    spec.printf("%%define _prefix %s\n\n", params.get("prefix"));

    spec.printf("%s\n\n", loader.getPreInstall());
    spec.printf("%s\n\n", loader.getInstall());
    spec.printf("%s\n\n", loader.getPostInstall());
    spec.printf("%s\n\n", loader.getPreRemove());
    spec.printf("%s\n\n", loader.getPostRemove());
    spec.printf("%s\n\n", loader.getVerify());
    spec.printf("%s\n\n", loader.getClean());
  }

  /**
   * Run the external command to build the package.
   * @throws MojoExecutionException if an error occurs
   */
  protected void execute(String name, boolean needarch) throws MojoExecutionException {
    File f = new File( workarea, "SPECS" );

    Commandline cl = new Commandline();
    cl.setExecutable( "rpmbuild" );
    cl.setWorkingDirectory( f.getAbsolutePath() );
    cl.createArgument().setValue( "-ba" );
    cl.createArgument().setValue( "--define" );
    cl.createArgument().setValue( "_topdir " + workarea.getAbsolutePath() );
    if ( !needarch ) {
      cl.createArgument().setValue( "--target" );
      cl.createArgument().setValue( "noarch" );
    }
    cl.createArgument().setValue( name + ".spec" );

    StreamConsumer stdout = new StdoutConsumer( getLog() );
    StreamConsumer stderr = new StderrConsumer( getLog() );
    try {
      getLog().info("RPMCMD: " + cl.toString());
      int result = CommandLineUtils.executeCommandLine( cl, stdout, stderr );
      if ( result != 0 ) {
        throw new MojoExecutionException( "RPM build execution returned: \'" + result + "\'." );
      }
    }
    catch ( CommandLineException e ) {
      throw new MojoExecutionException( "Unable to build the RPM", e );
    }
  }

  /**
   * Consumer to receive lines sent to stdout.  The lines are logged
   * as info.
   */
  private class StdoutConsumer  implements StreamConsumer {
    /** Logger to receive the lines. */
    private Log logger;

    /**
     * Constructor.
     * @param log The logger to receive the lines
     */
    public StdoutConsumer( Log log ) {
      logger = log;
    }

    /**
     * Consume a line.
     * @param string The line to consume
     */
    public void consumeLine( String string ) {
      logger.info( string );
    }
  }

  /**
   * Consumer to receive lines sent to stderr.  The lines are logged
   * as warnings.
   */
  private class StderrConsumer  implements StreamConsumer {
    /** Logger to receive the lines. */
    private Log logger;

    /**
     * Constructor.
     * @param log The logger to receive the lines
     */
    public StderrConsumer( Log log ) {
      logger = log;
    }

    /**
     * Consume a line.
     * @param string The line to consume
     */
    public void consumeLine( String string ) {
      logger.warn( string );
    }
  }
}
