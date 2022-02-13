lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "play-scala-slick-partial-results",
    version := "2.8.x",
    scalaVersion := "2.13.8",
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings"
    ),
    libraryDependencies ++= Seq(
      guice,
      "com.typesafe.play" %% "play-slick" % "5.0.0",
      "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
      "com.h2database" % "h2" % "1.4.200",
      specs2 % Test
    ),
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
  )
  .settings(
    javaOptions in Test += "-Dslick.dbs.default.connectionTimeout=30 seconds"
  )
  // We use a slightly different database URL for running the slick applications and testing the slick applications.
  .settings(javaOptions in Test ++= Seq("-Dconfig.file=conf/test.conf"))
