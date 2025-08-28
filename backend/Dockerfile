FROM cgr.dev/chainguard/gradle:latest AS builder

WORKDIR /app

COPY build.gradle settings.gradle ./

RUN gradle dependencies --no-daemon

COPY src ./src

RUN gradle bootJar --no-daemon

FROM cgr.dev/chainguard/gradle:latest

WORKDIR /app

COPY --from=builder --chown=nonroot:nonroot /app/build/libs/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD java -cp app.jar -Dloader.main=org.springframework.boot.loader.jarmode.JarModeLauncher org.springframework.boot.loader.jarmode.JarModeLauncher listLayers > /dev/null 2>&1 || exit 1

ENTRYPOINT []
CMD ["/bin/sh", "-c", "java $JAVA_OPTS -jar app.jar"] 