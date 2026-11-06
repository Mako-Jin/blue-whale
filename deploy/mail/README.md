# deploy/mail —— DMZ 邮件主机部署物

这里是 **Mailcow 侧**的部署件，不属于 `infrastructure/docker` 的主栈。
Mailcow 必须独立部署在 DMZ 主机（对公网开 25/443/465/587/993），
不能与应用服务同机，原因见 [docs/design/network-boundary.md](../../docs/design/network-boundary.md)。

完整步骤与运维口径：[docs/ops/mail-service-ops.md](../../docs/ops/mail-service-ops.md)

## 文件

| 文件 | 去处 | 作用 |
|---|---|---|
| `mailcow.conf.example` | `/opt/mailcow-dockerized/mailcow.conf` | 主机名、时区、端口发布、组件开关、安全策略 |
| `docker-compose.override.yml` | 与 mailcow 官方 compose 同目录 | 容器内存/CPU 上限 + 日志轮转（防 ClamAV 吃穿内存、postfix 写满磁盘） |
| `postfix-extra.cf` | `data/conf/postfix/extra.cf` | 出站 25 被封时走 465/587 中继（含三种组合，二选一） |
| `dns-records.example.zone` | 手工录入 DNS 服务商 | A / MX / SPF / DKIM / DMARC / 自动发现 六类记录模板 |

## 最短路径

```bash
git clone https://github.com/mailcow/mailcow-dockerized.git /opt/mailcow-dockerized
cd /opt/mailcow-dockerized
cp <repo>/deploy/mail/mailcow.conf.example ./mailcow.conf   # 改 MAILCOW_HOSTNAME
cp <repo>/deploy/mail/docker-compose.override.yml ./docker-compose.override.yml
docker compose config > /dev/null && docker compose pull && docker compose up -d
```

DNS 与 PTR 必须先就绪，否则第一封信就会被拒——
装完先跑 `scripts/ops/mail-deliverability-check.ps1 -Domain <你的域名>` 验一遍。

## 注意

- 本目录**不含任何真实口令**。`mailcow.conf` 由模板生成后只存在于 DMZ 主机，**不得入 Git**。
- 官方 `update.sh` 会 `git pull`，`docker-compose.override.yml` 与 `data/conf/` 属于未跟踪文件，
  升级不会被覆盖；但 `data/conf/nginx/site.conf` 是跟踪文件，改过会冲突，改前先备份。
