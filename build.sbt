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
  .enablePlugins(AshScriptPlugin)
  .settings(
    dockerBaseImage := "openjdk:8u201-jre-alpine3.9",
    makeBatScripts := Seq(),
    dockerUpdateLatest := true,
    dockerRepository := Some("localhost:5000"),
    defaultLinuxInstallLocation in Docker := "/app",
    dockerExposedPorts := Seq(8081),
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
    )

  )
