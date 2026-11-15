# ============================================================
# STAGE 1: BUILD
# We use the full JDK here because compiling code needs javac,
# the compiler, which the smaller JRE image doesn't have.
# We name this stage "build" so we can copy files out of it later.
# ============================================================
FROM eclipse-temurin:21-jdk AS build

# All commands from here run inside /app inside the container
WORKDIR /app

# MODULE tells this Dockerfile WHICH service to build.
# It's passed in from compose.yml as: args: MODULE: identity-service (or wallet-service)
# This lets ONE Dockerfile build BOTH services, just with a different value.
ARG MODULE

# Copy the Maven wrapper folder and files first.
# This lets us run Maven without installing it separately in the image.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy ONLY the pom.xml (not the code yet) from both modules.
# We do this separately from copying the source code (below) so that
# Docker can cache the "download dependencies" step. If your source code
# changes but your pom.xml doesn't, Docker reuses the cached dependencies
# instead of re-downloading everything - much faster rebuilds.
COPY identity-service/pom.xml identity-service/pom.xml
COPY wallet-service/pom.xml wallet-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Make the Maven wrapper script executable (Linux permission, not needed on Windows,
# but required inside the Linux container).
RUN chmod +x mvnw

# Download all dependencies for MODULE and its dependencies (-am = "also make",
# i.e. also resolve modules it depends on, like the shared parent pom).
# -pl = "project list", meaning "only care about this specific module".
RUN ./mvnw -pl ${MODULE} -am dependency:go-offline

# NOW copy the actual source code for both modules.
# (Both are copied because the parent pom + child modules reference each other,
# even though we'll only ever package the one specified in MODULE.)
COPY identity-service ./identity-service
COPY wallet-service ./wallet-service
COPY payment-service ./payment-service
COPY notification-service ./notification-service

# Actually compile and package MODULE into a runnable .jar file.
# -DskipTests speeds up the Docker build by not running unit tests here
# (tests should run in CI before this step, not during image build).
RUN ./mvnw -pl ${MODULE} -am clean package -DskipTests


# ============================================================
# STAGE 2: RUN
# We don't need the JDK (compiler, build tools) to just RUN the app -
# only the JRE (Java Runtime) is needed. This makes the final image
# much smaller and more secure (smaller attack surface).
# ============================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# We need MODULE again here to know which .jar to copy from the build stage,
# since each service produces target/<module-name>.jar inside its own folder.
ARG MODULE

# Copy ONLY the final .jar file from the "build" stage above.
# None of the source code, Maven cache, or JDK come along - keeps this image lean.
COPY --from=build /app/${MODULE}/target/*.jar app.jar

# Documents that this container listens on port 8080.
# (Doesn't actually open the port - that's done by "ports:" in compose.yml)
EXPOSE 8080

# The command that runs when the container starts:
# start the Java runtime and run our packaged app.
ENTRYPOINT ["java", "-jar", "app.jar"]