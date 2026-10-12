# =====================================================================================
#  Stage 1 - build
#  Dependencies are resolved in their own layer, before the sources are copied, so that
#  editing a Java file does not invalidate the (slow) dependency download.
# =====================================================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml lombok.config ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# =====================================================================================
#  Stage 2 - runtime
#  A JRE-only base image, no build toolchain, and a non-root user.
# =====================================================================================
FROM eclipse-temurin:17-jre-alpine

RUN addgroup --system billing && adduser --system --ingroup billing billing

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R billing:billing /app

USER billing

EXPOSE 8080

# MaxRAMPercentage lets the JVM size its heap from the container's memory limit instead of
# the host's, which is what stops the kernel OOM-killing the container under load.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
