# enforcement-api

## Building and deploying the application

### Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```

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

This will start the API container exposing the application's port `4550`.

In order to test if the application is up, you can call its health endpoint:

```bash
  curl http://localhost:4550/health
```

You should get a response similar to this:

```
  {"status":"UP","components":{"diskSpace":{"status":"UP","details":{"total":125658222592,"free":59036033024,"threshold":10485760,"path":"/opt/app/.","exists":true}},"ping":{"status":"UP"},"ssl":{"status":"UP","details":{"validChains":[],"invalidChains":[]}}}}
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

### Running enforcement-api with local CCD

 ```bash
 ./gradlew bootWithCCD
 ```
Above command starts Enforcement API + CCD & all dependencies

Once successfully loaded open XUI at http://localhost:3000
See `CftlibConfig.java` for users and login details.

The next command launches specific tests against CCD using the Spring Boot Web framework.

```bash
./gradlew cftlibTest
```


---


## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

