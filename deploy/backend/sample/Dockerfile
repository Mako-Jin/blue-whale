# ============================================================
# 蓝鲸后端业务服务通用 Dockerfile 模板（参数化，含网关/业务/APP 均适用）
# 构建上下文：仓库根目录
#   docker build -f deploy/backend/sample/Dockerfile \
#     --build-arg MODULE_PATH=whale-services/whale-service-auth/whale-service-auth-starter \
#     --build-arg SERVICE_PORT=8081 -t bluewhale/whale-service-auth .
# 说明：MODULE_PATH 为相对 whale-backend/ 的模块路径（Maven -pl 坐标）
# ============================================================

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ---- Stage 0: 运行时素材（仅 NEED_FACT_ASSETS=true 的模块，如 whale-agent） ----
# whale-agent 的 FactCollector / RepositoryScanner 在运行时按“仓库根布局”读取
# whale-backend/**/*.java|*.sql|pom.xml|*.yml|*.properties、whale-frontend/**/package.json 与前端源码、docs/**/*.md。
# 运行时镜像默认只含 app.jar，会导致结构化事实与文本检索通道为空（V-16）。
# 这里在构建期把所需文件裁剪成一份只读素材包，默认构建为空目录、不增加体积。
FROM alpine:3.20 AS fact-assets

ARG NEED_FACT_ASSETS=false

COPY whale-backend /src/whale-backend
COPY whale-frontend /src/whale-frontend
COPY docs /src/docs

RUN set -eu; \
    mkdir -p /repo; \
    if [ "${NEED_FACT_ASSETS}" != "true" ]; then exit 0; fi; \
    cp -a /src/whale-backend /repo/whale-backend; \
    cp -a /src/whale-frontend /repo/whale-frontend; \
    cp -a /src/docs /repo/docs; \
    find /repo/whale-backend -type f ! \( -name '*.java' -o -name '*.sql' -o -name 'pom.xml' \
        -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' \) -delete; \
    find /repo/whale-frontend -type f ! \( -name 'package.json' -o -name '*.ts' -o -name '*.tsx' \
        -o -name '*.js' -o -name '*.jsx' -o -name '*.vue' \) -delete; \
    find /repo/docs -type f ! -name '*.md' -delete; \
    find /repo -mindepth 1 -type d -empty -delete; \
    echo "fact assets files: $(find /repo -type f | wc -l)"

# ---- Stage 1: Maven Build ----
FROM ${MAVEN_IMAGE} AS builder

# 构建期参数（FROM 之后需重新声明才能用于 RUN）
ARG MODULE_PATH=whale-services/whale-service-auth/whale-service-auth-starter

WORKDIR /build

COPY .mvn .mvn
COPY config/checkstyle config/checkstyle
COPY whale-backend whale-backend

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
            -f whale-backend/pom.xml -pl "${MODULE_PATH}" -am package; then \
            ok=1; break; \
        fi; \
        echo "--- mvn attempt ${i} failed, retrying in 20s ---"; \
        sleep 20; \
    done; \
    [ "${ok}" = "1" ]; \
    mkdir -p /out; \
    jar=$(find "whale-backend/${MODULE_PATH}/target" -maxdepth 1 -type f -name '*-exec.jar' | head -n 1); \
    if [ -z "${jar}" ]; then \
        jar=$(find "whale-backend/${MODULE_PATH}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1); \
    fi; \
    test -n "${jar}"; \
    cp "${jar}" /out/app.jar; \
    ls -l /out/app.jar

# ---- Stage 2: Runtime ----
FROM ${JRE_IMAGE} AS runner

ARG SERVICE_PORT=8081

LABEL org.opencontainers.image.title="bluewhale-service"
LABEL org.opencontainers.image.description="蓝鲸后端业务服务（通用模板）"
LABEL org.opencontainers.image.vendor="BlueWhale"

# WHALE_AGENT_REPO_ROOT / WHALE_AGENT_INDEX_DIR 仅 whale-agent 使用，其余服务忽略；
# 素材包为空目录时 agent 的采集器只会得到空结果，不会因路径缺失而报错。
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport" \
    WHALE_AGENT_REPO_ROOT=/opt/whale-repo \
    WHALE_AGENT_INDEX_DIR=/app/data/agent-index

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && addgroup -S whale \
    && adduser -S -G whale whale

WORKDIR /app

# 运行时事实/检索素材（NEED_FACT_ASSETS=false 时为空目录，仅几字节）
COPY --from=fact-assets /repo /opt/whale-repo
COPY --from=builder /out/app.jar app.jar
RUN mkdir -p /app/data/agent-index \
    && chown -R whale:whale /app

USER whale

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
