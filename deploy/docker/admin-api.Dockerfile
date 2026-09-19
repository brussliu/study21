# Study 2.1 — admin-api 镜像（多阶段）
# 阶段1：Maven 构建（含 common-core / common-security 依赖模块）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend ./backend
WORKDIR /build/backend
RUN mvn -B -pl admin-api -am clean package -Dmaven.test.skip=true

# 阶段2：JRE 运行环境
FROM eclipse-temurin:21-jre
WORKDIR /app

# ffmpeg / ffprobe: batL02（学習モニターの動画取込・スナップショット切出）が使う。
# 画像からスナップショットを切り出すのに ffmpeg、動画の長さを読むのに ffprobe が要る
# （docs/STUDY_MONITOR.md）。PATH が通っていない環境では
# STUDY21_STUDY_MONITOR_FFMPEG_COMMAND / _FFPROBE_COMMAND で絶対パスを指定する。
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/backend/admin-api/target/admin-api-0.1.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
