# Study 2.1 — 前端镜像（多阶段）
# 阶段1：Node 构建 pc-web + mobile-web（npm workspace）
FROM node:22-bookworm-slim AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json frontend/tsconfig.base.json ./
COPY frontend/packages/web-shared ./packages/web-shared
COPY frontend/pc-web ./pc-web
COPY frontend/mobile-web ./mobile-web
RUN npm ci --no-audit --no-fund
RUN npm run build -w pc-web
RUN npm run build -w mobile-web

# 阶段2：nginx 托管两个前端 + 反向代理两个后端
FROM nginx:1.27-alpine
# タイムゾーン（TZ=Asia/Tokyo）を効かせる。Alpine には tzdata が入っておらず、
# 入っていないと TZ を設定してもログの時刻が UTC のままになる。
RUN apk add --no-cache tzdata
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /app/pc-web/dist /usr/share/nginx/html
COPY --from=frontend-build /app/mobile-web/dist /usr/share/nginx/html/m
# Windows ホストからビルドした際、一部ファイルのパーミッションが 000 になるケースがある。
# 静的ファイルが nginx から 403 で返らないよう、全ファイルを読み取り可能にしておく。
RUN chmod -R a+r /usr/share/nginx/html /usr/share/nginx/html/m && \
    find /usr/share/nginx/html /usr/share/nginx/html/m -type d -exec chmod a+x {} +
EXPOSE 80 81
