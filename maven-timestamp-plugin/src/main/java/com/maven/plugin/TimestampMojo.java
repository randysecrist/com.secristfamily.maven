package com.secristfamily.maven.plugin;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Creates a timestamp and sticks it into the project properties.
 * 
 * @phase generate-sources
 * @goal create
 */
public class TimestampMojo extends AbstractMojo {

    /**
     * Project instance, used to add new source directory to the build.
     * 
     * @parameter default-value="${project}"
     * @required
     */
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MMM-dd'T'HHmmssZ");
        if (project == null) {
            throw new MojoExecutionException("project is null");
        }
        String timestamp = format.format(new Date());
        getLog().info("setting property [timestamp] to [" + timestamp + "]");
        project.getProperties().setProperty("timestamp", timestamp);
    }
}
