# 蓝鲸 MySQL 8.4 自定义镜像
# 基础镜像可通过构建参数替换为国内镜像源
ARG MYSQL_BASE_IMAGE=mysql:8.4
FROM ${MYSQL_BASE_IMAGE}

# 覆盖层: init 脚本由 docker-compose 通过 volume 挂载
# 注意: Windows 绑定挂载的文件权限为 0777，MySQL 会忽略 world-writable 的配置文件，
#       因此 my.cnf 直接烘进镜像（修改后需重新 build）
COPY conf/my.cnf /etc/mysql/conf.d/my.cnf
RUN chmod 0644 /etc/mysql/conf.d/my.cnf

EXPOSE 3306
