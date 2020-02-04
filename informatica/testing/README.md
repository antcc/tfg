# Examples of using the `fuzzyspark` package

Simple project to test our functions.

## Compiling and running

Since the `fuzzyspark` package is not yet published, there are two ways of packaging the testing application to run it with `spark-submit`:

1. Publishing the package into a local repository and adding it as a managed dependency in `build.sbt`:

```scala
libraryDependencies ++= Seq(
  ...,
  "com.github.antcc" %% "fuzzyspark" % "1.0"
```

2. Packaging it into a JAR file and adding it as an unmanaged dependency in a `lib` folder.

After adding it as a dependency either way, use [`sbt assembly`](https://github.com/sbt/sbt-assembly/) to generate a standalone JAR file for `spark-submit`.
