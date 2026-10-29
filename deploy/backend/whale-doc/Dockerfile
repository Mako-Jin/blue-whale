# ============================================================
# 蓝鲸在线文档 / Wiki 服务 whale-doc - Spring Boot 多阶段构建
# 构建上下文：仓库根目录
#   docker build -f deploy/backend/whale-doc/Dockerfile -t bluewhale/whale-doc .
# ============================================================

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ---- Stage 1: Maven Build ----
FROM ${MAVEN_IMAGE} AS builder

WORKDIR /build

COPY .mvn .mvn
COPY config/checkstyle config/checkstyle
COPY backend backend

RUN mvn -B -ntp -f backend/pom.xml -pl whale-services/whale-doc -am package -DskipTests \
    && mkdir -p /out \
    && cp backend/whale-services/whale-doc/target/whale-doc.jar /out/app.jar

# ---- Stage 2: Runtime ----
FROM ${JRE_IMAGE} AS runner

LABEL org.opencontainers.image.title="bluewhale-whale-doc"
LABEL org.opencontainers.image.description="蓝鲸在线文档 / Wiki 服务"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseContainerSupport"

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && addgroup -S whale \
    && adduser -S -G whale whale

WORKDIR /app

COPY --from=builder /out/app.jar app.jar
RUN chown -R whale:whale /app

USER whale

EXPOSE 8095

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
