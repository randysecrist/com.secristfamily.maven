package com.secristfamily.maven.plugin;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarArchiver.TarCompressionMethod;

/**
 * @goal rpm
 * @author Randy Secrist
 */
public class RPMMojo extends AbstractMojo {

  /**
   * The Maven project.
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  private MavenProject project;

  /**
   * @component
   */
  private MavenProjectHelper projectHelper;

  /**
   * To look up Archiver/UnArchiver implementations
   * 
   * @parameter expression="${component.org.codehaus.plexus.archiver.manager.ArchiverManager}"
   * @required
   */
  protected ArchiverManager archiverManager;
  
  /**
   * Directory containing the project.
   * 
   * @parameter expression="${project.basedir}"
   * @required
   */
  private static File baseDirectory;

  /**
   * Directory containing the generated RPM. (target/)
   * 
   * @parameter expression="${project.build.directory}"
   * @required
   */
  private File targetDirectory;

  /**
   * Whether to use the defined directories as the base or the root
   * 
   * @parameter
   */
  private boolean basedirectory;

  /**
   * The directories and files to include within the archive.
   * 
   * @parameter
   */
  private File[] include;

  /**
   * Any files or directories matching any one of these regular expressions will not be included,
   * even if explicitly decared to be included in the include parameter.
   * 
   * @parameter
   */
  private List<String> excludeRegexList;
  private static final String[] DEFAULT_EXCLUDES = new String[] { "**/package.html" };
  private static final String[] DEFAULT_INCLUDES = new String[] { "**/**" };

  /**
   * Directory containing the compiled classes. (target/classes)
   * 
   * @parameter expression="${project.build.outputDirectory}"
   * @required
   */
  private File classesDirectory;

  /**
   * Flag which indicates if the mojo should incorporate dependencies into the ZIP archive.
   * 
   * @parameter expression="true"
   */
  private boolean addDependencies;

  /**
   * Flag which indicates to the mojo if it should generate the primary artifact from the current
   * project.
   * 
   * @parameter expression="false"
   */
  private boolean generatePrimaryArtifact;

  /**
   * ArtifactId of the primary dependency
   * 
   * @parameter
   */
  private String primaryArtifactId;

  /**
   * Name of the generated ZIP.
   * 
   * @parameter alias="zipName" expression="${project.build.finalName}"
   * @required
   */
  private String finalName;

  /**
   * Classifier to add to the artifact generated. If given, the artifact will be an attachment
   * instead.
   * 
   * @parameter
   */
  private String classifier;

  /**
   * The JAR archiver.
   * 
   * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#jar}"
   * @required
   */
  private JarArchiver jarArchiver;

  /**
   * The TAR archiver.
   * 
   * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#tar}"
   * @required
   */
  private TarArchiver tarArchiver;

  /**
   * The maven archiver to use.
   * 
   * @parameter
   */
  private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();
  
  /**
   * The name portion of the output file name.
   * @parameter expression="${project.artifactId}"
   * @required
   */
  private String artifactId;
  
  /**
   * The version portion of the output file name.
   * @parameter expression="${project.version}"
   * @required
   */
  private String version;

  /**
   * RPM Parameters
   * @parameter
   */
  private Map<String,String> params;
  
  /**
   * RPM Requires
   * @parameter
   */
  private String[] requires;

  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info("Creating RPM...");
    
    File tempDir = createTempDir();
    File rpmFile = createPackage(tempDir);
    
        if (classifier != null)
            projectHelper.attachArtifact(project, "rpm", classifier, rpmFile);
        else
            project.getArtifact().setFile(rpmFile);
  }

  public File createTempDir() throws MojoExecutionException, MojoFailureException {
    // Create Workspace
    File tempDir = new File(targetDirectory, "temp");
    if (!tempDir.exists())
      tempDir.mkdirs();
    boolean addTempDir = false;

    // Generates a primary artifact (from the current project) and adds it to the RPM.
    // Only compatibile with JAR artifacts, (Utilizes the JAR plugin)
    if (generatePrimaryArtifact == true) {
      addTempDir = true;
      File jarFile = getJarFile(tempDir, finalName, classifier);
      this.createJARArchive(jarFile, classesDirectory);
    }

    // Copy artifacts to temp/lib directory (primaryDependency to temp)
    if (addDependencies) {
      Set<Artifact> dependencies = this.getDependencies();
      if (dependencies != null && dependencies.size() > 0) {
        addTempDir = true;
        for (Artifact a : dependencies) {
          if (primaryArtifactId != null && a.getArtifactId().equals(primaryArtifactId)) {
            // add runtime dependencies not declared in primaryArtifact due to circular nature
            if (project.getDependencies().size() > 1) {
              addLocalDependencies(a.getFile(), tempDir);
            }
            this.copyArtifact(a, tempDir); // copy primaryArtifact to tempDir
          }
          else if (!a.getScope().equals("test")) { // only adds non test dependencies.
            File lib = new File(tempDir, "lib");
            this.copyArtifact(a, lib); // copy dependencies into lib
          }
        }
      }
    }

    if (include != null) {
      addTempDir = true;
      if (basedirectory) {
        for (File f : include) {
          File[] files = f.listFiles();
          if (files != null) {
            for (File child : files) {
              copyToWorkspace(tempDir, child);
            }
          }
        }
      }
      else {
        for (File f : include) {
          copyToWorkspace(tempDir, f);
        }
      }
    }        
    return tempDir;
  }

  private void copyArtifact(Artifact a, File targetDir) throws MojoExecutionException {
    if (!targetDir.exists())
      targetDir.mkdirs();
    File artifactDestFile = new File(targetDir, a.getFile().getName());
    FileUtils.copyFile(a.getFile(), artifactDestFile);
  }

  /**
   * Generates the RPM.  Assumes tempDir has been setup properly.
   * 
   * @todo Add license files in META-INF directory.
   */
  public File createPackage(File tempDir) throws MojoExecutionException, MojoFailureException {
    File destFile = null;
    File workarea = new File(targetDirectory, "rpm");
    RPMBuild rpmbuild = new RPMBuild(workarea, getLog());
    try {
      // Build RPM Work Area
      rpmbuild.buildWorkArea();
        
      // Generate SPEC File
      String name = getComponentName(params, artifactId);
      String rpmVersion = null;

      // Check the version string
      if ( version.indexOf( "-" ) == -1 ) {
        rpmVersion = version;
      }
      else {
        rpmVersion = version.substring(0, version.indexOf( "-" ));
      }         
      params.put("name", name);
      params.put("version", rpmVersion);

      // Set Default Release #
      if (!params.containsKey("release")) {
        if (version.contains("SNAPSHOT"))
          params.put("release", "SNAPSHOT");
        else
          params.put("release", "1");
      }
        
      // Suggest Prefix Location
      if (!params.containsKey("prefix")) {
        params.put("prefix", "/opt/ge/hcit/ecis");
      }
        
      // Place RPM Sources
      String component_name = getComponentName(params, name);
      String install_dir = getComponentName(params, component_name);
      File sources = new File(workarea, "SOURCES/" + component_name + ".tgz");  // link name of gzip to .spec file
      this.createTARArchive(sources, tempDir, component_name + "/");
        
      params.put("pombr", baseDirectory.getPath());
      rpmbuild.writeSpecFile(component_name, params, requires);
        
      // Build Package
      rpmbuild.execute(component_name, false);
        
      // Move to Target
      String buildArch = params.get("BuildArch");
      String artifact = component_name + "-" + rpmVersion + "-" + params.get("release") + "." + buildArch + ".rpm";
      File rpmWork = new File(workarea, "RPMS/" + buildArch + "/" + artifact);
      File rpmDest = new File(targetDirectory, artifact);
      FileUtils.copyFile(rpmWork, rpmDest);
      destFile = rpmDest;
    }
    catch (Throwable e) {
      throw new MojoExecutionException("Problem creating archive: ", e);
    }
    finally {
      if (!params.containsKey("debug")) {
        FileUtils.deleteDirectory(tempDir);
        FileUtils.deleteDirectory(workarea);
      }
    }
    return destFile;
  }
  
  protected static File getPOMBR() {
    return new File(baseDirectory, "src/buildroot");
  }
  
  protected static String getInstallDir(Map<String,String> params) {
    return getInstallDir(params, null);
  }
  
  protected static String getInstallDir(Map<String,String> params, String def) {
    boolean exists = params.containsKey("InstallDir");
    if (def == null && !exists) {
      throw new RuntimeException("InstallDir Param Required!");
    }
    if (!exists) {
      params.put("InstallDir", def);
    }
    return params.get("InstallDir");
  }
  
  protected static String getComponentName(Map<String,String> params) {
    return getComponentName(params, null);
  }
  
  protected static String getComponentName(Map<String,String> params, String def) {
    boolean exists = params.containsKey("ComponentName");
    if (def == null && !exists) {
      throw new RuntimeException("ComponentName Param Required!");
    }
    if (!exists) {
      params.put("ComponentName", def);
    }
    return params.get("ComponentName");
  }
      
  protected static File getJarFile(File basedir, String finalName, String classifier) {
    return getFile(basedir, finalName, classifier, ".jar");
  }

  protected static File getFile(File basedir, String finalName, String classifier, String extension) {
    if (classifier == null)
      classifier = "";
    else if (classifier.trim().length() > 0 && !classifier.startsWith("-"))
      classifier = "-" + classifier;
    return new File(basedir, finalName + classifier + extension);
  }
  
  /**
   * Creates a JarArchive where the contents are the specified directory.
   * 
   * @param jarFile The JAR file to create.
   * @param directory The directory to include in the JAR file.
   * @throws MojoExecutionException If any problems occur during the creation.
   */
  public void createJARArchive(File jarFile, File directory) throws MojoExecutionException {
    try {
      MavenArchiver archiver = new MavenArchiver();
      archiver.setArchiver(jarArchiver);
      archiver.setOutputFile(jarFile);
      if (!directory.exists()) {
        getLog().warn("JAR will be empty - no content was marked for inclusion!");
      }
      else {
        archiver.getArchiver().addDirectory(directory, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);
      }
      archiver.createArchive(project, archive);
    }
    catch (Throwable t) {
      throw new MojoExecutionException("Problem creating JAR archive: ", t);
    }
    return;
  }
  
  public void createTARArchive(File tarFile, File directory, String prefix) throws MojoExecutionException {
    try {
      TarCompressionMethod tcm = new org.codehaus.plexus.archiver.tar.TarArchiver.TarCompressionMethod();
      tcm.setValue("gzip");
      tarArchiver.setDestFile(tarFile);
      tarArchiver.setCompression(tcm);
      tarArchiver.addDirectory(directory, prefix);  // prefix == directory @ root of GZIP
      tarArchiver.createArchive();
    }
    catch (Throwable t) {
      throw new MojoExecutionException("Problem creating TAR archive: ", t);
    }
    return;
  }
  
  /**
   * Retrieves all artifact dependencies.
   * 
   * @return A HashSet of artifacts
   */
  protected Set<Artifact> getDependencies() {
    Set<Artifact> dependenciesSet = new HashSet<Artifact>();

    if (project.getArtifact() != null && project.getArtifact().getFile() != null)
      dependenciesSet.add(project.getArtifact());

    Set projectArtifacts = project.getArtifacts();
    if (projectArtifacts != null)
      dependenciesSet.addAll(projectArtifacts);

    this.filterArtifacts(dependenciesSet);
    return dependenciesSet;
  }
  
  protected void filterArtifacts(Set<Artifact> artifacts) {
    for (Iterator<Artifact> i = artifacts.iterator(); i.hasNext();) {
      Artifact a = i.next();
      String fname = a.getFile().getName();
      String ext = fname.substring(fname.length() - 3, fname.length());
      if (!(
        "jar".equalsIgnoreCase(ext) ||
        "war".equalsIgnoreCase(ext) ||
        "ear".equalsIgnoreCase(ext))) {
        i.remove();
      }
    }
  }
  
  /**
   * Copies the file into the workspace directory only if it does not match the exclude regular
   * expression, if one is given.
   * 
   * @param tempDir -
   *            the workspace directory
   * @param f -
   *            the file to copy
   * @throws MojoExecutionException
   */
  private void copyToWorkspace(File tempDir, File f) throws MojoExecutionException {
    String fName = f.getName();
    if (f.isDirectory())
      FileUtils.copyDirectory(f, new File(tempDir, fName));
    else if (f.isFile())
      FileUtils.copyFile(f, new File(tempDir, fName));
  }
  
  /**
   * Adds the dependencies of this project to a primary artifact. (Essentially combines two jar
   * files).
   * 
   * @param primaryArtifact
   *            The primary artifact to modify.
   * @param tempDir
   *            The temporary directory space to operate within.
   * @throws MojoExecutionException
   *             If any problems occur during the merge.
   */
  protected void addLocalDependencies(File primaryArtifact, File tempDir) throws MojoExecutionException {
    File tmp = new File(tempDir, "tmp");
    FileUtils.explodeZip(primaryArtifact, tmp);
    FileUtils.deleteDirectory(new File(tmp, "META-INF"));
    try {
      createJARArchive(primaryArtifact, tmp);
    }
    catch (Throwable e) {
      getLog().error(e);
    }
    finally {
      FileUtils.deleteDirectory(tmp);
    }
  }

}
