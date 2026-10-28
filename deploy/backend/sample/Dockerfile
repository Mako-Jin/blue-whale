# ============================================================
# 蓝鲸后端业务服务通用 Dockerfile 模板（参数化，含网关/业务/APP 均适用）
# 构建上下文：仓库根目录
#   docker build -f deploy/backend/sample/Dockerfile \
#     --build-arg MODULE_PATH=whale-services/whale-service-auth/whale-service-auth-starter \
#     --build-arg SERVICE_PORT=8081 -t bluewhale/whale-service-auth .
# 说明：MODULE_PATH 为相对 backend/ 的模块路径（Maven -pl 坐标）
# ============================================================

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ---- Stage 1: Maven Build ----
FROM ${MAVEN_IMAGE} AS builder

# 构建期参数（FROM 之后需重新声明才能用于 RUN）
ARG MODULE_PATH=whale-services/whale-service-auth/whale-service-auth-starter

WORKDIR /build

COPY .mvn .mvn
COPY config/checkstyle config/checkstyle
COPY backend backend

# 镜像构建不执行单测：单测由 backend-ci 单独负责，镜像环境缺 .git/docs/MySQL 等，
# 跑测试会引入环境性失败。使用 maven.test.skip（同时跳过测试编译），
# 因为父 pom 一旦在 pluginManagement 里硬编码 skipTests，-DskipTests 会失效。
# 依赖下载对中央仓瞬时失败（429/断流）做整体重试，避免多 job 并发拉包时随机失败。
RUN set -eu; \
    ok=0; \
    for i in 1 2 3; do \
        if mvn -B -ntp \
            -Dmaven.test.skip=true \
            -Dmaven.wagon.http.retryHandler.count=5 \
            -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
            -f backend/pom.xml -pl "${MODULE_PATH}" -am package; then \
            ok=1; break; \
        fi; \
        echo "--- mvn attempt ${i} failed, retrying in 20s ---"; \
        sleep 20; \
    done; \
    [ "${ok}" = "1" ]; \
    mkdir -p /out; \
    jar=$(find "backend/${MODULE_PATH}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1); \
    test -n "${jar}"; \
    cp "${jar}" /out/app.jar; \
    ls -l /out/app.jar

# ---- Stage 2: Runtime ----
FROM ${JRE_IMAGE} AS runner

ARG SERVICE_PORT=8081

LABEL org.opencontainers.image.title="bluewhale-service"
LABEL org.opencontainers.image.description="蓝鲸后端业务服务（通用模板）"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && addgroup -S whale \
    && adduser -S -G whale whale

WORKDIR /app

COPY --from=builder /out/app.jar app.jar
RUN chown -R whale:whale /app

USER whale

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
