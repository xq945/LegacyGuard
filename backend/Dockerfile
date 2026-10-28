# ==========================================================
# LegacyGuard 后端镜像（多阶段构建）
#
# 构建：docker build -t legacyguard-backend .
# 注意：镜像中<b>不包含任何密钥</b>，JWT 密钥与主密钥均在运行时通过环境变量注入。
# ==========================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只复制 pom 并预下载依赖，这样改源码时不必重新下载依赖（利用层缓存）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="LegacyGuard Backend" \
      org.opencontainers.image.description="数字遗产与账号托管平台后端"

# tzdata：容器内时区数据；非 root 用户：最小权限原则
RUN apk add --no-cache tzdata \
    && addgroup -S app \
    && adduser -S app -G app

WORKDIR /app

COPY --from=build /build/target/*.jar /app/app.jar

RUN mkdir -p /app/logs && chown -R app:app /app
USER app

EXPOSE 8080

# -XX:MaxRAMPercentage 让 JVM 按容器内存限额分配堆，避免被 OOMKilled
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Duser.timezone=Asia/Shanghai", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "/app/app.jar"]
