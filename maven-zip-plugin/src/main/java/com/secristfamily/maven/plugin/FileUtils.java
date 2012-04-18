package com.secristfamily.maven.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.maven.plugin.MojoExecutionException;

import com.secristfamily.maven.plugin.ZipMojo.Exclusion;

public class FileUtils {

  /**
   * Copies one input stream to another.
   * 
   * @param in The input stream.
   * @param out The output stream.
   * @throws IOException if there is a problem during the transfer.
   */
  public static final void copyInputStream(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[(1 << 10) * 8];
    int count = 0;
    do {
      out.write(buffer, 0, count);
      count = in.read(buffer, 0, buffer.length);
    }
    while (count != -1);
  }
  
  public static final void copyFile(File in, File out) throws MojoExecutionException {
    FileInputStream fis = null;
    FileOutputStream fos = null;
    try {
      fis = new FileInputStream(in);
      fos = new FileOutputStream(out);
      FileUtils.copyInputStream(fis, fos);
    }
    catch (Throwable e) {
      throw new MojoExecutionException("Failed to copy file " + in.getName(), e);
    }
    finally {
      try { if (fis != null) { fis.close(); fis = null; } } catch (Throwable e) { ; }
      try { if (fos != null) { fos.close(); fos = null; } } catch (Throwable e) { ; }
    }
  }
  
  public static void copyDirectory(File src, File dest, Exclusion excludeVisitor) throws MojoExecutionException {
    if (!dest.exists())
      dest.mkdirs();
    File[] files = src.listFiles();
    for(File f : files) {
      String fileName = f.getName();
      if (f.isDirectory())
        copyDirectory(f, new File(dest, fileName), excludeVisitor);
      else {
        if (excludeVisitor.exclude(fileName)) {
          continue;
        }
        copyFile(f, new File(dest, fileName));
      }
    }
  }
  
  public static void deleteDirectory(File directory) {
    File[] files = directory.listFiles();
    for (File f : files) {
      if (f.isDirectory())
        deleteDirectory(f);
      else
        f.delete();
    }
    directory.delete();
  }
  
  /**
   * Recursivly finds all files within a directory.
   * @param directory The directory entry point.
   */
  public static Set<File> recurseFS(File directory) {
    return recurseFS(false, directory, new HashSet<File>());
  }
  
  /**
   * Recursivly finds all files and subdirectories within a directory.
   * @param directory The directory entry point.
   */
  public static Set<File> recurseFS(boolean includeDirectories, File directory, Set<File> set) {
    File dirList[] = directory.listFiles();
    for (int i = 0; i < dirList.length; i++) {
      if (dirList[i].isDirectory()) {
        Set<File> subDir = recurseFS(dirList[i]);
        set.addAll(subDir);
        if (includeDirectories)
          set.add(dirList[i]);
        }
      else
        set.add(dirList[i]);
    }
    return set;
  }
  
  /**
   * Explodes a ZIP file (or JAR) into the specified directory.
   * @param zipped The zip file.
   * @param directory The specified directory.
   * @throws MojoExecutionException If any problems occur during the expansion.
   */
  public static void explodeZip(File zipped, File directory) throws MojoExecutionException {
    // Explode ZIP into a directory
    ZipFile parent = null;
    BufferedOutputStream bos = null;
    try {
      if (!directory.exists())
        directory.mkdirs();
      // Open saved archive & save each file:
      parent = new ZipFile(zipped);
      Enumeration entries = parent.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) entries.nextElement();
        File child = new File(directory, entry.getName());
        if (entry.isDirectory())
          continue;
        else
          child.getParentFile().mkdirs();
        bos = new BufferedOutputStream(new FileOutputStream(child));
        FileUtils.copyInputStream(parent.getInputStream(entry), bos);
        bos.close();
        bos = null;
      }
      parent.close();
      parent = null;
    }
    catch (ZipException e) {
      throw new MojoExecutionException("Exception expanding bytes into zip file: " + zipped.getName() + " - is it really ZIP file?", e);
    }
    catch (IOException e) {
      throw new MojoExecutionException("Exception creating contents of zip file: " + zipped.getName(), e);
    }
    finally {
      if (parent != null) {
        try {
          parent.close();
          parent = null;
        }
        catch (IOException e) { ; }
      }
      if (bos != null) {
        try {
          bos.close();
          bos = null;
        }
        catch (IOException e) { ; }
      }
    }
  }

}
