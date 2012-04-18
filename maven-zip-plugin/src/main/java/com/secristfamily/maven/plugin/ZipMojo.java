package com.secristfamily.maven.plugin;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;

/**
 * @goal zip
 * @requiresDependencyResolution runtime
 * @author Randy K. Secrist
 */
public class ZipMojo extends AbstractMojo {

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
   * Directory containing the generated ZIP. (target/)
   * 
   * @parameter expression="${project.build.directory}"
   * @required
   */
  private File outputDirectory;

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
   * even if explicitly declared to be included in the include parameter.
   * 
   * @parameter
   */
  private List<String> excludeRegexList;

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
   * The Jar archiver.
   * 
   * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#jar}"
   * @required
   */
  private JarArchiver jarArchiver;

  /**
   * The maven archiver to use.
   * 
   * @parameter
   */
  private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

  private static final String[] DEFAULT_EXCLUDES = new String[] { "**/package.html" };

  private static final String[] DEFAULT_INCLUDES = new String[] { "**/**" };

  protected final MavenProject getProject() {
    return project;
  }

  protected String getClassifier() {
    return classifier;
  }

  /**
   * Executes this POM plugin.
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info("Creating ZIP...");

    File zipFile = createArchive();

    String classifier = getClassifier();
    if (classifier != null)
      projectHelper.attachArtifact(getProject(), "zip", classifier, zipFile);
    else
      getProject().getArtifact().setFile(zipFile);
  }

  protected static File getJarFile(File basedir, String finalName, String classifier) {
    return getFile(basedir, finalName, classifier, ".jar");
  }

  protected static File getZipFile(File basedir, String finalName, String classifier) {
    return getFile(basedir, finalName, classifier, ".zip");
  }

  protected static File getFile(File basedir, String finalName, String classifier, String extension) {
    if (classifier == null)
      classifier = "";
    else if (classifier.trim().length() > 0 && !classifier.startsWith("-"))
      classifier = "-" + classifier;
    return new File(basedir, finalName + classifier + extension);
  }

  public Archiver getArchiver() throws MojoExecutionException {
    try {
      return this.archiverManager.getArchiver("zip");
    }
    catch (NoSuchArchiverException e) {
      throw new MojoExecutionException("Could not locate archiver: ", e);
    }
  }

  /**
   * Generates the ZIP.
   * 
   * @todo Add license files in META-INF directory.
   */
  public File createArchive() throws MojoExecutionException, MojoFailureException {
    File destFile = getZipFile(outputDirectory, finalName, getClassifier());

    Archiver archive = this.getArchiver();
    archive.setDestFile(destFile);

    // Create Workspace
    File tempDir = new File(outputDirectory, "temp");
    if (!tempDir.exists())
      tempDir.mkdirs();
    boolean addTempDir = false;

    // Generates a primary artifact (from the current project) and adds it to the ZIP.
    // Only compatibile with JAR artifacts, (Utilizes the JAR plugin)
    if (generatePrimaryArtifact == true) {
      addTempDir = true;
      File jarFile = getJarFile(tempDir, finalName, getClassifier());
      this.createMainJarArchive(jarFile, classesDirectory);
    }

    // Copy artifacts to temp/lib directory (primaryDependency to temp)
    if (addDependencies) {
      Set<Artifact> dependencies = this.getDependencies();
      if (dependencies != null && dependencies.size() > 0) {
        addTempDir = true;
        File lib = new File(tempDir, "lib");
        if (!lib.exists())
          lib.mkdirs();
        for (Artifact a : dependencies) {
          if (primaryArtifactId != null && a.getArtifactId().equals(primaryArtifactId)) {
            // add runtime dependencies not declared in primaryArtifact due to circular
            // nature
            if (this.getProject().getDependencies().size() > 1) {
              addLocalDependencies(a.getFile(), tempDir);
            }
            FileUtils.copyFile(a.getFile(), new File(lib.getParentFile(), a.getFile().getName()));
          }
          else if (!a.getScope().equals("test")) { // only adds non test dependencies.
            FileUtils.copyFile(a.getFile(), new File(lib, a.getFile().getName()));
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

    try {
      // Add Dependencies to ZIP
      if (addTempDir)
        archive.addDirectory(tempDir);

      // Write File
      archive.createArchive();
    }
    catch (Throwable e) {
      throw new MojoExecutionException("Problem creating archive: ", e);
    }
    finally {
      FileUtils.deleteDirectory(tempDir);
    }

    return destFile;
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
    if (excludeDelegate.exclude(fName)) {
      return;
    }

    if (f.isDirectory())
      FileUtils.copyDirectory(f, new File(tempDir, fName), excludeDelegate);
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
      createMainJarArchive(primaryArtifact, tmp);
    }
    catch (Throwable e) {
      getLog().error(e);
    }
    finally {
      FileUtils.deleteDirectory(tmp);
    }
  }

  /**
   * Creates a JarArchive where the contents are the specified directory.
   * 
   * @param jarFile
   *            The JAR file to create.
   * @param directory
   *            The directory to include in the JAR file.
   * @throws MojoExecutionException
   *             If any problems occur during the creation.
   */
  public void createMainJarArchive(File jarFile, File directory) throws MojoExecutionException {
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
    catch (Throwable e) {
      throw new MojoExecutionException("Problem creating executable jar: ", e);
    }
    return;
  }
      
  /**
   * Retrieves all artifact dependencies.
   * 
   * @return A HashSet of artifacts
   */
  protected Set<Artifact> getDependencies() {
    MavenProject project = getProject();

    Set<Artifact> dependenciesSet = new HashSet<Artifact>();

    if (project.getArtifact() != null && project.getArtifact().getFile() != null)
      dependenciesSet.add(project.getArtifact());

    // As of version 1.5, project dependencies require runtime resolution:
    // see requiresDependencyResolution definition at top of class.
    Set projectArtifacts = project.getArtifacts();
    if (projectArtifacts != null)
      dependenciesSet.addAll(projectArtifacts);

    this.filterArtifacts(dependenciesSet);
    return dependenciesSet;
  }

  protected void filterArtifacts(Set<Artifact> artifacts) {
    for (Iterator i = artifacts.iterator(); i.hasNext();) {
      Artifact a = (Artifact)i.next();
      String fname = a.getFile().getName();
      String ext = fname.substring(fname.length() - 3, fname.length());
      if (!"jar".equalsIgnoreCase(ext))
        i.remove();
    }
  }

  private Exclusion excludeDelegate = new Exclusion();

  public class Exclusion {
    public boolean exclude(String candidate) {
      if (ZipMojo.this.excludeRegexList != null) {
        for (String regex : ZipMojo.this.excludeRegexList) {
          if (candidate.matches(regex)) {
            return true;
          }
        }
      }
      return false;
    }
  }
  
}
