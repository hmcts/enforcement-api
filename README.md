# ENFORCEMENT API

## Purpose

This is a Spring Boot application

## Getting Started

### Prerequisites

Running the application requires the following tools to be installed in your environment:

* [yarn](https://yarnpkg.com/)
* [Docker](https://www.docker.com)
* [Java 21](https://adoptium.net/)
* [Gradle](https://gradle.org)
* [Helm](https://helm.sh)
* [Kubernetes](https://kubernetes.io)
* [Kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)

### Apple MacOS X

* If you are developing on Apple MacOS X you can install except for Java the above tools using [Homebrew](https://brew.sh/)
* For Java, we recommend using [SDKMAN](https://sdkman.io/)


## What's inside

The Build Gradle relies on the[HMCTS Java plugin](https://github.com/hmcts/gradle-java-plugin) set up with the following plugins:
 * docker setup
 * automatically publishes API documentation to [hmcts/cnp-api-docs](https://github.com/hmcts/cnp-api-docs)
 * code quality tools already set up
 * MIT license and contribution information
 * Helm chart using chart-java.

## Building and deploying the application

The application exposes health endpoint (http://localhost:4550/health) and metrics endpoint
(http://localhost:4550/metrics).


## Building and deploying the application

### Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```

To clean the project execute the following command:

```bash
  ./gradlew clean
```

This will remove the `build` directory with all its content. You will need to build the project again.

### Running the application

Create the image of the application by executing the following command:

```bash
  ./gradlew assemble
```

Note: Docker Compose V2 is highly recommended for building and running the application.
In the Compose V2 old `docker-compose` command is replaced with `docker compose`.

Create docker image:

```bash
  docker compose build
```

Run the distribution (created in `build/install/enforcement-api` directory)
by executing the following command:

```bash
  docker compose up
```

This will start the API container exposing the application's port
(set to `4550` in this enforcement-api app).

In order to test if the application is up, you can call its health endpoint:

```bash
  curl http://localhost:4550/health
```

You should get a response similar to this:

```
  {"status":"UP","diskSpace":{"status":"UP","total":249644974080,"free":137188298752,"threshold":10485760}}
```

### Alternative script to run application

To skip all the setting up and building, just execute the following command:

```bash
./bin/run-in-docker.sh
```

For more information:

```bash
./bin/run-in-docker.sh -h
```

Script includes bare minimum environment variables necessary to start api instance. Whenever any variable is changed or any other script regarding docker image/container build, the suggested way to ensure all is cleaned up properly is by this command:

```bash
docker compose rm
```

It clears stopped containers correctly. Might consider removing clutter of images too, especially the ones fiddled with:

```bash
docker images

docker image rm <image-id>
```

There is no need to remove postgres and java or similar core images.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## Plugins

The enforcement-api contains the following plugins:

* HMCTS Java plugin

  Applies code analysis tools with HMCTS default settings. See the [project repository](https://github.com/hmcts/gradle-java-plugin) for details.

  Analysis tools include:

  * checkstyle

    https://docs.gradle.org/current/userguide/checkstyle_plugin.html

    Performs code style checks on Java source files using Checkstyle and generates reports from these checks.
    The checks are included in gradle's *check* task (you can run them by executing `./gradlew check` command).

  * org.owasp.dependencycheck

    https://jeremylong.github.io/DependencyCheck/dependency-check-gradle/index.html

    Provides monitoring of the project's dependent libraries and creating a report
    of known vulnerable components that are included in the build. To run it
    execute `gradle dependencyCheck` command.

* jacoco

  https://docs.gradle.org/current/userguide/jacoco_plugin.html

  Provides code coverage metrics for Java code via integration with JaCoCo.
  You can create the report by running the following command:

  ```bash
    ./gradlew jacocoTestReport
  ```

  The report will be created in build/reports subdirectory in your project directory.

* io.spring.dependency-management

  https://github.com/spring-gradle-plugins/dependency-management-plugin

  Provides Maven-like dependency management. Allows you to declare dependency management
  using `dependency 'groupId:artifactId:version'`
  or `dependency group:'group', name:'name', version:version'`.

* org.springframework.boot

  http://projects.spring.io/spring-boot/

  Reduces the amount of work needed to create a Spring application


* com.github.ben-manes.versions

  https://github.com/ben-manes/gradle-versions-plugin

  Provides a task to determine which dependencies have updates. Usage:

  ```bash
    ./gradlew dependencyUpdates -Drevision=release
  ```

## Setup

Located in `./bin/init.sh`. Simply run and follow the explanation how to execute it.

The End.
