name := "fuzzyspark"
description := "A collection of fuzzy algorithms for Spark."
organization := "com.github.antcc"
version := "1.0"
isSnapshot := true
scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
	"org.apache.spark" %% "spark-core" % "2.4.4" % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.4.4" % "provided"
)

licenses += "GPLv3" -> url("https://www.gnu.org/licenses/gpl-3.0.html")
