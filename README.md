# Secrist Maven Plugins

This project contains a number of maven plugins that will assist with
with the packaging and deployment of java applications and libraries.

Below is an inventory of what can be found within: 

1.  [Timestamp Plugin](https://github.com/randysecrist/com.secristfamily.maven/tree/master/maven-timestamp-plugin)
  Tags jar files with version information so [getSpecificationVersion](http://docs.oracle.com/javase/6/docs/api/) and friends will work.
2.  [Simple Zip Plugin](https://github.com/randysecrist/com.secristfamily.maven/tree/master/maven-zip-plugin)
  Builds a standard zip distribution that contains a java executable jar file.  Supports a lib directory and a matching MANIFEST file.
3.  [Simple RPM Plugin](https://github.com/randysecrist/com.secristfamily.maven/tree/master/maven-rpm-plugin)
  Same as the Zip distribution, but more advanced RPM scripting hooks with less configuration than other RPM plugins in the wild.

## Installation

Each of these is inserted into a maven pom.xml file under the plugins
section.  See each individual plugin for more details on the various
options.

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Added some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request
