FROM gcr.io/distroless/java:8
COPY ./target/scala-2.12/mi-scala-app-assembly-*.jar /app/
WORKDIR /app
CMD ["mi-scala-app-assembly-0.1.jar"]
