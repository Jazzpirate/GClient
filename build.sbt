name := "GClient"

version := "0.1"

scalaVersion := "2.13.0"

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.13.0"
libraryDependencies += "org.scala-lang" % "scala-library" % "2.13.0"
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"

libraryDependencies += "commons-io" % "commons-io" % "2.6"

libraryDependencies += "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

libraryDependencies += "com.google.api-client" % "google-api-client" % "1.30.2"
libraryDependencies += "com.google.oauth-client" % "google-oauth-client-jetty" % "1.30.1"
libraryDependencies += "com.google.apis" % "google-api-services-drive" % "v3-rev173-1.25.0"
// libraryDependencies += "com.google.photos.library" % "google-photos-library-client" % "1.4.0"

// libraryDependencies += "com.dorkbox" % "SystemTray" % "3.17"
// libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.0-M7"

resolvers += "bintray" at "https://jcenter.bintray.com"
libraryDependencies += "com.github.serceman" % "jnr-fuse" % "0.5.3"
