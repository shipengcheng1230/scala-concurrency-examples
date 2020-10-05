name := "scala-concurrency-examples"

version := "0.1"

scalaVersion := "2.13.3"

fork := false

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies += "commons-io" % "commons-io" % "2.8.0"

scalacOptions := Seq("-unchecked", "-deprecation")