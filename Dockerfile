FROM openjdk:8u201-jre-alpine3.9
RUN mkdir /app
COPY ./target/scala-2.12/mi-scala-app-assembly-*-deps.jar /app/lib/
COPY ./target/scala-2.12/mi-scala-app_2.12-*.jar /app/lib/
WORKDIR /app
ENTRYPOINT java -cp "lib/*" com.example.MainApp