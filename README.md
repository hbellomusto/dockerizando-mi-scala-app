# _dockerizando_ mi-scala-app

# Descripción de mi-scala-app
Es una aplicación sencilla que solo tiene un endpoint `/hello/${name}` que devuelve `Hello, <name>.`

El objetivo del proyecto es poder ejecutar la aplicación con un `docker run -p 8081:8081 mi-scala-app`. Para ello se muestran diferentes opciones. 

Cada opción está en un branch distinto basado en _master_ para que se pueda hacer un diff y ver que archivos se agregaron o modificaron: `git diff master..HEAD`

# Opciones para dockerizar
## sbt-assembly + Dockerfile (single stage)
> `git checkout assembly_docker_single_stage`

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
> `git checkout assembly_docker_single_stage_opt`


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

### Optimización 3: _distroless_
> `git checkout assembly_docker_single_stage_opt_distroless`

1. Construir una imagen [_distroless_](https://github.com/GoogleContainerTools/distroless/blob/master/examples/java/Dockerfile). Esto aporta mayor seguridad:
```dockerfile
FROM gcr.io/distroless/java:8
COPY ./target/scala-2.12/mi-scala-app-assembly-*.jar /app/
WORKDIR /app
CMD ["mi-scala-app-assembly-0.1.jar"]
```
Ventajas:
- Imagen más segura.

Desventajas:
- No se tiene acceso al _container_.
- Es necesario poner el nombre completo del jar, sin wildcards.

## sbt-assembly + Dockerfile (multi-stage)
> `git checkout assembly_docker_multi_stage`

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
- No se aprovecha la caché de _.ivy_. _sbt_ se toma su tiempo cuando no tiene _caches_

### Optimización 1: montar una cache de _.ivy2_ con _buildkit_
> `git checkout assembly_docker_multi_stage_opt1`

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

Ventajas:
- Reutiliza la cache de _.ivy2_.

Desventajas:
- ivy tiene el nefasto _.lock_ por lo que no deberíamos utilizar concurrentemente la cache.

# Optimización 2: usar _buildkit_ con _coursier_
> `git checkout assembly_docker_multi_stage_opt2`

1. Crear un _dockerfile_ multistage utilizando coursier para bajar las dependencias en paralelo:
```dockerfile
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
```
2. Ejecutar:
```bash
DOCKER_BUILDKIT=1 docker build . -t81 mi-scala-app
```

## sbt-assembly + sbt-docker
> `git checkout assembly_and_sbt_docker`

Requisitos:
- jdk
- sbt
- docker
Pasos:
1. Agregar los plugins:
```bash
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
```
2. Agregar la configuración de sbt-docker al `build.sbt:
```scala
lazy val root = (project in file("."))
//...
    .enablePlugins(DockerPlugin)
    .settings(
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"
    
      new Dockerfile {
        from("openjdk:8-jre")
        add(artifact, artifactTargetPath)
        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
)
```
3. Ejecutar `sbt docker`

Ventajas:
- Todo integrado dentro de _sbt_
- No tengo que ejecutar el comando `docker`

Desventajas:
- Necesito tener docker, sbt y jdk.
- Manejo de versiones de imagen integrado


### Mejora _necesaria_: Configurar las versiones de la imagen
> `git checkout assembly_and_sbt_docker_mejora`

En algún momento es necesario publicar la imagen. Para ello tenemos que agregar a la imagen el nombre del repo y el tag.
1. Agregar al `build.sbt`:
```scala
lazy val root = (project in file("."))
//...
  .settings(
    imageNames in docker := Seq(
      ImageName(
        registry = Some("localhost:5000"),
        //namespace = Some(organization.value),
        repository = name.value,
        tag = Some(version.value)
      ),
      ImageName(
        registry = Some("localhost:5000"),
        //namespace = Some(organization.value),
        repository = name.value,
        tag = Some("latest")
      )
  )
```

## sbt-native-packager
> `git checkout sbt_native_packager`

Requisitos:
- jdk
- sbt
- docker

1. Agregar el plugin:
```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")
```
2. Agregar al _build.sbt_:
```scala
lazy val root = (project in file("."))
//...
  .enablePlugins(JavaAppPackaging)
```
3. Ejecutar `sbt docker:publishLocal`

Ventajas:
- Simple, todo desde sbt. Poca configuración inicial.

Desventajas:
- La imagen que genera está basada en `openjdk:latest`, muy grande y `latest`

### Mejoras 1
> `git checkout sbt_native_packager_mejora1`

- Usar una imagen más pequeña. Es necesario habilitar el plugin `AshScriptPlugin` para que el script generado por _sbt-native-packager_ no requiera _bash_ sino _ash_. 
- No producir archivos _.bat_.
- Generar el tag _latest_
- Especificar el _registry_ privado para nuestras imágenes. Luego con `sbt docker:publish` podemos publicarla en el _registry_ (para levantar un registry local ejecutar: `docker run -p 5000:5000 registry:2 `).
- Cambiar el directorio base para nuestra _app_.
```scala
  .enablePlugins(AshScriptPlugin)
  .settings(
    dockerBaseImage := "openjdk:8u201-jre-alpine3.9",
    makeBatScripts := Seq(),
    dockerUpdateLatest := true,
    dockerRepository := Some("localhost:5000"),
    defaultLinuxInstallLocation in Docker := "/app",
    dockerExposedPorts := Seq(8081)
  )
```

### Mejoras 2: Configuración en `/app/conf/...`
> `git checkout sbt_native_packager_mejora2`

- Según [twelve factor app](https://12factor.net/config) conviene utilizar variables de entorno para la configuración.

1. Agregar al `build.sbt`:
```scala
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
    )
```

2. Para ejecutar se necesitará pasar las variables `HOST`y `PORT`:
```bash
docker run --rm -p 8081:8081 -e HOST=0.0.0.0 -e PORT=8081  localhost:5000/mi-scala-app:0.1
```
  - Como alternativa se puede montar un archivo `/tmp/application.conf`
  ```hocon
  mi-scala-app {
      server-config {
          host = "0.0.0.0"
          port = 8081
      }
  }
  ```
  Utilizando el siguiente comando:
  ```bash
  docker run --rm -p 8081:8081 -v /tmp/application.conf:/app/conf/application.conf:ro  localhost:5000/mi-scala-app:0.1
  ```