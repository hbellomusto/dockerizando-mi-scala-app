FROM hseeberger/scala-sbt:8u181_2.12.8_1.2.8 as building
COPY . /building
RUN cd /building && sbt assembly

FROM openjdk:8u201-jre-alpine3.9
COPY --from=building /building/target/scala-2.12/mi-scala-app-assembly-*.jar /app/
WORKDIR /app
ENTRYPOINT java -jar mi-scala-app-assembly-*.jar