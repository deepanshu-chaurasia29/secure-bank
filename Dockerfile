# ---------- Stage 1: build the jar with Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy only pom.xml first: Docker can cache the downloaded libraries when only your code changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests are skipped here because the integration tests need a MySQL database.
# They run in the CI workflow instead (see .github/workflows/ci.yml).
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: a small image that only RUNS the jar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Do not run the app as root
RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080

# The app's clock. All dates are stored and shown in this timezone, and the daily transfer limit
# resets at midnight in this timezone. Change it with the APP_TZ environment variable.
ENV APP_TZ=Asia/Kolkata

# Java settings for a SMALL machine (the free Render instance has 512 MB of memory):
#   MaxRAMPercentage=65  -> the heap may use 65% of the container memory (the rest is for Java itself)
#   UseSerialGC          -> a light garbage collector, good for one small CPU
#   TieredStopAtLevel=1  -> faster start and less memory on a slow CPU
#   Xss512k              -> smaller stack per thread
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

# In a container the app must refuse to start with the local-only default JWT secret
ENV REQUIRE_CUSTOM_JWT_SECRET=true

# "sh -c" lets ${APP_TZ} and ${JAVA_OPTS} be read. "exec" makes Java the main process (clean shutdown).
ENTRYPOINT ["sh", "-c", "exec java -Duser.timezone=${APP_TZ} ${JAVA_OPTS} -jar app.jar"]
