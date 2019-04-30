# _dockerizando_ mi-scala-app

# Descripción de la mi-scala-app
Es una aplicación sencilla que solo tiene un endpoint `/hello/${name}` que devuelve `Hello, <name>.`
El objetivo del proyecto es poder ejecutar la aplicación con un `docker run -p 8080

# Opciones para dockerizar
## sbt-assembly + Dockerfile (single stage)
Requisitos:
- jdk
- sbt
- docker

Pasos:
1. Agregar `addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")`
2. Crear un archivo `Dockerfile` en la raiz de proyecto 
```dockerfile
FROM openjdk:8u201-jre-alpine3.9
RUN mkdir /app
COPY ./target/scala-2.12/mi-scala-app-assembly-*.jar /app/
WORKDIR /app
ENTRYPOINT java -jar mi-scala-app-assembly-*.jar
```
3. Ejecutar `sbt assembly`
4. Ejecutar `docker build . -t mi-scala-app`
5. _Enjoy_ `docker run -p 8081:8081 mi-scala-app`

Ventajas:
- Se aprovecha la cache de .ivy

Desventajas:
- Se requiere tener _jdk_ instalada y es tedioso si se necesita otra versión de _jdk_

### Optimización 1: _.dockerignore_
Se puede reducir la cantidad de archivos que se envían al _docker daemon_ agregando un `.dockerignore`:
```bash
*
!target/scala-2.12/mi-scala-app*.jar
```
### Optimización 2: dependencias en otro _layer_
1. Copiar las depdendencias por separado para aprovechar la _cache_ de _docker_:
```dockerfile
FROM openjdk:8u201-jre-alpine3.9
RUN mkdir /app
COPY ./target/scala-2.12/mi-scala-app-assembly-*-deps.jar /app/lib/
COPY ./target/scala-2.12/mi-scala-app_2.12-*.jar /app/lib/
WORKDIR /app
ENTRYPOINT java -cp "lib/*" com.example.MainApp
```
2. Ejecutar `sbt assemblyPackageDependency`
2. Ejecutar `sbt package`
3. Ejecutar `docker build . -t mi-scala-app`
4. _Enjoy_ `docker run -p 8081:8081 mi-scala-app` 

## sbt-assembly + Dockerfile (multi-stage)
Requisitos:
- docker

Pasos:
1. Agregar `addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")`
2. Crear un archivo `Dockerfile` en la raiz de proyecto 
```dockerfile
FROM hseeberger/scala-sbt:8u181_2.12.8_1.2.8 as building
RUN mkdir /building
WORKDIR /building
COPY . /building
RUN sbt assembly

FROM openjdk:8u201-jre-alpine3.9
RUN mkdir /app
COPY --from=building /building/target/scala-2.12/mi-scala-app-assembly-*.jar /app/
WORKDIR /app
ENTRYPOINT java -jar mi-scala-app-assembly-*.jar
```
3. _(optimización)_ Agregar un _.dockerignore_:
```bash
target
.idea
.git
**/target
``` 
4. Ejecutar `docker build . -t mi-scala-app`
5. _Enjoy_ `docker run -p 8081:8081 mi-scala-app`

Ventajas:
- Solo se necesita _docker_ para el _building_ de la imagen (útil para CI)

Desventajas:
- no se aprovecha la caché de _.ivy_. _sbt_ se toma su tiempo cuando no tiene _caches_

### Optimización 1: montar una cache de _.ivy2_ con _buildkit_
1. Crear un _Dockerfile_ _multistage_ y montar una cache de ivy2 para que reutilice las dependencias bajadas previamente:
```dockerfile
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
```
2. Ejecutar `docker build` habilitando _buildkit_:
```bash
DOCKER_BUILDKIT=1 docker build . -t mi-scala-app
```
## sbt-assembly + sbt-docker
Requisitos:
- jdk
- sbt
- docker
Pasos:
1. 