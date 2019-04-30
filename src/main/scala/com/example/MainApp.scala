package com.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware.Logger
import pureconfig.generic.auto._

object MainApp extends IOApp {

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }.orNotFound


  final case class ServerConfig(host:String, port:Int)

  def run(args: List[String]): IO[ExitCode] = {
    println("iniciando")
    val serverConfig = pureconfig.loadConfigOrThrow[ServerConfig]("mi-scala-app.server-config")

    BlazeServerBuilder[IO]
      .bindHttp(serverConfig.port, serverConfig.host)
      .withHttpApp(Logger.httpApp(true,true)(helloWorldService))
      .withoutBanner
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  }
}
