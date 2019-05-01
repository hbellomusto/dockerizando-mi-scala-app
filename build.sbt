val Http4sVersion = "0.20.0"
val CirceVersion = "0.11.1"
val LogbackVersion = "1.2.3"
val pureConfigVersion = "0.10.2"

lazy val root = (project in file("."))
  .settings(
    name := "mi-scala-app",
    scalaVersion := "2.12.7",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"            %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"            %% "http4s-circe"        % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"          % Http4sVersion,
      "io.circe"              %% "circe-generic"       % CirceVersion,
      "ch.qos.logback"        %  "logback-classic"     % LogbackVersion,
      "com.github.pureconfig" %% "pureconfig"          % pureConfigVersion
    )
  )
  .enablePlugins(JavaAppPackaging)

