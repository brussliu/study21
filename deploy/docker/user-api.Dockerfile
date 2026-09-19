# Study 2.1 — user-api 镜像（多阶段）
# 阶段1：Maven 构建（含 common-core / common-security 依赖模块）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend ./backend
WORKDIR /build/backend
RUN mvn -B -pl user-api -am clean package -Dmaven.test.skip=true

# 阶段2：JRE 运行环境
FROM eclipse-temurin:21-jre
WORKDIR /app
# 授業録音の「続録の結合」に ffmpeg / ffprobe を使う（一時停止・画面の開き直しで
# MediaRecorder を作り直すと、その回のタイムスタンプは 0 から始まる。コンテナのヘッダを
# 落として繋ぐだけでは時刻が重なるので、セッションごとに時刻を積み直して 1 本にする）。
# **無くても壊れない**: 無いときは結合せず、分塊を残したまま「結合できていない
# （再試行できる）」と画面に出す（壊れた 1 本を公開しない）。
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/backend/user-api/target/user-api-0.1.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
