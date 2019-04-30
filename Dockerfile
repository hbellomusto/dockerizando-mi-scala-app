# syntax = docker/dockerfile:experimental

FROM hseeberger/scala-sbt:8u181_2.12.8_1.2.8 as building
COPY . /building
RUN  --mount=type=cache,id=ivy2,target=/root/.ivy2 \
    cd /building && sbt package

FROM hseeberger/scala-sbt:8u181_2.12.8_1.2.8 as building_dependencies
COPY project /building/project/
COPY build.sbt /building/
COPY version.sbt /building/
RUN  --mount=type=cache,id=ivy2,target=/root/.ivy2 \
     cd /building && sbt assemblyPackageDependency

FROM openjdk:8u201-jre-alpine3.9
RUN mkdir /app
COPY --from=building_dependencies /building/target/scala-2.12/mi-scala-app-assembly-*-deps.jar /app/lib/
COPY --from=building /building/target/scala-2.12/mi-scala-app_2.12*.jar /app/lib/
WORKDIR /app
ENTRYPOINT java -cp "lib/*" com.example.MainApp
