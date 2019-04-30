# syntax = docker/dockerfile:experimental

FROM hseeberger/scala-sbt:8u181_2.12.8_1.2.8 as sbt_with_coursier
RUN  --mount=type=cache,id=coursier,target=/root/.cache/coursier/v1 \
    mkdir -p /root/.sbt/1.0/plugins/ && \
    echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M14")' >>  /root/.sbt/1.0/plugins/build.sbt && \
    sbt version && rm -f /root/.ivy2/.sbt.ivy.lock

FROM sbt_with_coursier as building
COPY . /building
RUN  --mount=type=cache,id=coursier,target=/root/.cache/coursier/v1 \
    cd /building && sbt package

FROM sbt_with_coursier as building_dependencies
COPY project /building/project/
COPY build.sbt /building/
COPY version.sbt /building/
RUN  --mount=type=cache,id=coursier,target=/root/.cache/coursier/v1 \
     cd /building && sbt assemblyPackageDependency

FROM openjdk:8u201-jre-alpine3.9 as final
RUN mkdir /app
COPY --from=building_dependencies /building/target/scala-2.12/mi-scala-app-assembly-*-deps.jar /app/lib/
COPY --from=building /building/target/scala-2.12/mi-scala-app_2.12*.jar /app/lib/
WORKDIR /app
ENTRYPOINT java -cp "lib/*" com.example.MainApp