# 빌드 스테이지 — 전체 멀티모듈 소스를 gradle 이미지에서 빌드해 app의 bootJar만 뽑아낸다.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY common/build.gradle common/build.gradle
COPY user-service/build.gradle user-service/build.gradle
COPY course-service/build.gradle course-service/build.gradle
COPY enrollment-service/build.gradle enrollment-service/build.gradle
COPY ai-service/build.gradle ai-service/build.gradle
COPY app/build.gradle app/build.gradle
RUN chmod +x gradlew && ./gradlew --version

COPY common common
COPY user-service user-service
COPY course-service course-service
COPY enrollment-service enrollment-service
COPY ai-service ai-service
COPY app app

RUN ./gradlew :app:bootJar -x test --no-daemon

# 실행 스테이지 — JDK가 아닌 JRE만 담아 이미지 크기를 줄인다.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /workspace/app/build/libs/app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
