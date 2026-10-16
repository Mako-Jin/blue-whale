# 蓝鲸 Nginx 自定义镜像
FROM nginx:alpine

# 时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 自定义主配置已通过 volume 挂载: nginx.conf -> /etc/nginx/nginx.conf
# 站点配置: conf.d/* -> /etc/nginx/conf.d/

EXPOSE 80 443
