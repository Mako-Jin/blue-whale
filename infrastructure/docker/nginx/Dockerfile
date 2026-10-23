# 蓝鲸 Nginx 自定义镜像
# 基础镜像可通过构建参数替换为国内镜像源，例如:
#   docker build --build-arg NGINX_BASE_IMAGE=docker.m.daocloud.io/library/nginx:alpine .
ARG NGINX_BASE_IMAGE=nginx:alpine
FROM ${NGINX_BASE_IMAGE}

# 时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 自定义主配置（compose 仍会以只读卷覆盖，镜像内置一份保证单独运行也可用）
COPY nginx.conf /etc/nginx/nginx.conf
COPY conf.d /etc/nginx/conf.d

EXPOSE 80 443
