# 邮件服务运维手册（whale-mail + Mailcow）

> 适用版本：Wave 5.89（M0 + M1 附件）
> 关联计划：[wave5.89-mail-service-m0.md](../plans/2026-11-06-wave5.89-mail-service-m0.md)、
> [2026-11-06-mail-server-and-map-service.md](../plans/2026-11-06-mail-server-and-map-service.md)
> 部署物：[deploy/mail/](../../deploy/mail/)
> 自检脚本：[mail-deliverability-check.ps1](../../scripts/ops/mail-deliverability-check.ps1)

---

## 一、两个组件，两条边界

| | `whale-mail`（业务服务） | Mailcow（MTA + 存储） |
|---|---|---|
| 部署位置 | 应用集群，与其它 Java 服务同网段 | **DMZ 独立主机**，对公网开 25/443/465/587/993 |
| 职责 | 账号绑定、文件夹、邮件索引、草稿、附件代理 | SMTP/IMAP 协议、邮件正文存储、反垃圾、DKIM 签名、Webmail |
| 数据 | `bluewhale_mail`（5 张表，只存索引与元信息） | 自己的 MySQL + vmail 卷（存真实信件） |
| 对外暴露 | 只经网关 `/mail/**`（443） | 25 / 443 / 465 / 587 / 993 |
| 谁联系谁 | 用 IMAP 993 / SMTP 587 **出站**连 Mailcow | 不需要知道 `whale-mail` 存在 |

两条红线：

1. **Mailcow 不能和应用服务同机**。它必须对公网开 25，而现有 [network-boundary.md](../design/network-boundary.md)
   的红线是「只有网关 443 暴露」。同机等于把整个应用集群放进 DMZ。
2. **`whale-mail` 不是 MTA**。它写库、做索引，但一封信的收发都不经它的进程直接落地——
   字节走 Mailcow，索引走自己。反过来，Mailcow 宕机时 `whale-mail` 只能读历史索引，发不出新信。

---

## 二、前置条件（缺一项就装不下去）

| # | 项 | 说明 | 卡点 |
|---|---|---|---|
| 1 | DMZ 主机 | 4C / 8G / 100G SSD 起（开 ClamAV 需 8G；关掉可 4G） | **硬阻塞**，没有就装不了 |
| 2 | 公网固定 IP | 需能申请 PTR 反解 | 家宽/动态 IP 不可用，反解改不了 |
| 3 | 域名 | 已完成 ICP 备案（大陆节点）；能改 MX/TXT | 备案未完成时 25 端口入站会被拦 |
| 4 | 出站 25 是否可用 | 阿里云/腾讯云默认封禁，需工单申请或走中继 | 见 §5 |
| 5 | 防火墙 | 入站 25/80/443/465/587/993；出站 53/25 或 465/587 | 出站 53 被拦会导致 Rspamd 的 DNSBL 查询全失败，评分失真 |
| 6 | 端口 80 | Let's Encrypt HTTP-01 用 | 被封则改 DNS-01 或挂外部证书，见 §3.4 |

> 25 端口是入站方向：云厂商封的是**出站** 25。入站 25 一般开放，但大陆节点要求域名已备案。

---

## 三、DNS：六项记录 + 一项反解

模板见 [deploy/mail/dns-records.example.zone](../../deploy/mail/dns-records.example.zone)。逐项说明口径。

### 3.1 先定一件事：员工邮箱用根域还是子域

| 现状 | 做法 | 后果 |
|---|---|---|
| 根域 MX **空闲** | 根域直接指向 Mailcow | 地址 `zhangsan@example.com`，最自然 |
| 根域 MX 已被企业邮箱占用 | **不要动根域**，用 `mail.corp.example.com` 承载 | 地址变成 `zhangsan@mail.corp.example.com`，难看但现有邮件不断 |
| 企业邮箱准备停用 | 迁 MX 前先做 **MX 双投**（两条 MX 不同优先级并存 2 周） | 直接切会丢切换窗口内的信 |

判断命令：

```bash
dig +short MX example.com
dig +short TXT example.com | grep spf
```

如果 MX 指向 `mxbiz1.qq.com` 之类，就是企业邮箱在用——先别动。

### 3.2 PTR 反解（最容易被拖到最后，却最致命）

PTR 必须在 **IP 所属云厂商**侧设置，DNS 服务商改不了。工单通常 1–3 个工作日。

- 目标：`203.0.113.10` → `mail.example.com`
- 要求：正解（A）与反解（PTR）**完全一致**
- 不一致的典型症状：Gmail 直接 550，Outlook 静默进垃圾箱，日志里看不出原因

```bash
dig +short -x 203.0.113.10          # 应为 mail.example.com.
dig +short A mail.example.com       # 应为 203.0.113.10
```

### 3.3 SPF / DKIM / DMARC 的取舍

| 记录 | 关键点 | 做错的典型后果 |
|---|---|---|
| SPF | **一个域名只能一条** `v=spf1`；走中继时只授权中继，别写自己 IP | 两条 SPF → permerror，等于没有 SPF |
| DKIM | Mailcow 生成密钥对，公钥发到 `{selector}._domainkey`；私钥留在服务端签名 | 未签名 + DMARC `p=reject` → 直接拒收 |
| DMARC | 先 `p=none` 观察 2 周，看 `rua` 报告里有哪些合法来源 | 直接上 `p=reject` 会把转发链路上的正常邮件全打回 |

DKIM 记录超 255 字符时**必须拆成多段字符串**，否则多数 DNS 服务商静默保存失败——
表现为「Mailcow 显示已签名，但收件方校验 dkim=fail」。这是最常见的假象。

DMARC 的 `rua` 会收到大量 XML 报告，别用个人邮箱收，建 `dmarc@example.com` 并做规则归档。

### 3.4 证书

优先 Let's Encrypt HTTP-01（Mailcow 内置 acme-mailcow 自动续期），前提是 80 端口公网可达。

两条降级路径：

- **80 被封**：改 DNS-01 验证（acme.sh 的 DNS 插件），或
- **挂外部证书**：把 `fullchain.pem` / `privkey.pem` 放到 `data/assets/ssl/cert.pem` 与 `key.pem`，
  并设 `SKIP_LETS_ENCRYPT=y`。此时续期由外部流程负责，**必须自建到期告警**，否则一年后全站证书过期。

证书主题必须与 `MAILCOW_HOSTNAME` 完全一致。用 IP 或别名访问会证书不匹配，
而 SOGo 的日历订阅（CalDAV/CardDAV）对证书尤其敏感。

### 3.5 一条命令验完

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ops/mail-deliverability-check.ps1 -Domain example.com
```

覆盖 MX / A / SPF / DKIM / DMARC / PTR / 端口 / 证书 8 类。加 `-SkipPorts` 只查 DNS（内网可用），
`-Server 8.8.8.8` 用公共解析器复核「公网是否可见」。

输出示例（`example.com` 是保留域名，故意全部失败）：

```
  FAIL  MX：example.com 设置了 null MX（0 .），明确声明不接收邮件
  FAIL  A 记录：mail.example.com 无法解析 A 记录
  PASS  SPF：v=spf1 -all
  FAIL  DKIM：dkim._domainkey.example.com 的公钥 p= 为空或过短，等同于撤销
  PASS  DMARC：p=reject
  WARN  DMARC 报告：未配置 rua=，拿不到聚合报告就无法定位伪造来源
```

**验收标准：全部 PASS，允许 DMARC 为 WARN（观察期）。出现任何 FAIL 都不要开始发信**——
IP 信誉是易碎品，前 100 封的失败率会长期跟着你。

---

## 四、部署步骤

```bash
# 1. 主机准备（DMZ，Ubuntu 22.04/24.04）
apt update && apt install -y git curl
# 关闭 systemd-resolved 占用的 53，避免与 Mailcow 内部解析冲突（若启用 unbound）
# 时区与 NTP：邮件时间戳偏差超过 5 分钟会被部分服务商判为可疑
timedatectl set-timezone Asia/Shanghai && timedatectl set-ntp true

# 2. 拉取并生成配置
git clone https://github.com/mailcow/mailcow-dockerized.git /opt/mailcow-dockerized
cd /opt/mailcow-dockerized
cp /path/to/blue-whale/deploy/mail/mailcow.conf.example ./mailcow.conf
vi mailcow.conf                 # 至少改 MAILCOW_HOSTNAME

# 3. 叠加资源与日志限制
cp /path/to/blue-whale/deploy/mail/docker-compose.override.yml ./docker-compose.override.yml
docker compose config > /dev/null && echo "compose OK"

# 4. 启动
docker compose pull
docker compose up -d
docker compose ps               # 全部 running/healthy 再继续

# 5. 首次进入管理台
#    https://mail.example.com/admin   默认 admin / moohoo
#    **第一件事就是改密码**，Mailcow 首次启动不强制
```

中继配置（若出站 25 被封）：把 [deploy/mail/postfix-extra.cf](../../deploy/mail/postfix-extra.cf)
的方案 A 段落复制到 `data/conf/postfix/extra.cf`，改口令后 `docker compose restart postfix-mailcow`。

> 每次改动 `mailcow.conf` 都要 `docker compose down && docker compose up -d`——
> 部分变量只在容器创建时读取，`restart` 不生效。

---

## 五、出站 25 被封怎么办

云厂商默认封出站 25 是行业惯例，不是配置错误。三条路：

| 方案 | 适用 | 代价 |
|---|---|---|
| A. 工单解封 | 有备案、有正当业务说明 | 通常不批；批了也要自己养 IP 信誉 |
| B. 走 465/587 中继（推荐） | 绝大多数场景 | 信誉归中继，退信归因变难；中继商可能改写 From |
| C. 混合：系统信走中继，员工信直投 | 出站 25 可用 | 配置复杂度翻倍，收益有限 |

**推荐 B**，并把「IP 预热」这件事整体外包给中继商——这是走中继最大的隐藏收益。

走中继的三个坑：

1. **发件域必须在中继商侧验证**。腾讯企业邮 / 阿里云 DirectMail 都要求先验证域名所有权，
   否则会重写 `From` 为服务商地址，收件人看到的发件人不是你。
2. **SPF 要改成授权中继**（`include:spf.mail.qq.com` 之类），不能留自己的 IP。
3. **DKIM 仍在自己这里签**。签名在前、中继在后，只要中继不改正文，签名依然有效——
   所以中继选型时要确认它不会重写 HTML 正文。

---

## 六、与 `whale-mail` 的对接

### 6.1 协议参数

| 方向 | 参数 | 值 |
|---|---|---|
| 收信 | IMAP | `993`，SSL/TLS（`WHALE_MAIL_IMAP_HOST` / `WHALE_MAIL_IMAP_PORT` / `WHALE_MAIL_IMAP_SSL=true`） |
| 发信 | SMTP | `587`，STARTTLS（`WHALE_MAIL_SMTP_PORT` / `WHALE_MAIL_SMTP_SSL=false`） |

两者都写进 `application.yml` 的 `whale.mail.default-*`，业务侧绑账号时不必逐项填。

**为什么 IMAP 用 993 而不是 143**：143 明文，`whale-mail` 跑在应用网段，中间任何一跳都能读到
员工邮件正文。993 是隐式 TLS，连 STARTTLS 降级攻击的面都没有。

### 6.2 凭据怎么办（这是设计上最容易走偏的一处）

凭据有**两个来源，按序取用**（`MailCredentialResolver`）：

| 列 | 谁写 | 适用 |
|---|---|---|
| `secret_ref` | 管理员绑定时填配置键名 | 外部托管的邮箱（企业邮箱、客户自有邮箱），口令归对方管 |
| `secret_cipher` | 平台代开时写入 `ENC(...)` 密文 | 平台代开的邮箱，口令由服务端随机生成，密钥是 `WHALE_CONFIG_ENC_KEY` |

`secret_ref` **只存配置键名**（如 `mail.credential.u1024`），值由部署侧注入环境变量，
原因见计划 §10：密码入库意味着同时经过浏览器、后端日志、数据库三处，轮换一次要改三处。

外部邮箱的落地方式（按推荐顺序）：

1. **Mailcow 应用专用密码**（首选）。在邮箱设置里给 IMAP 客户端生成独立口令，
   与登录口令解耦；员工改登录密码不会打断同步，泄露了也能单独吊销。
   > 若邮箱开启了 TOTP 二次验证，IMAP/SMTP **必须**用应用专用密码——用登录口令一定认证失败，
   > 报错是 `AUTHENTICATIONFAILED`，很容易被误判成配置错。
2. **Nacos 配置中心加密项**。Key 放 `mail.credential.<userId>`，值用 `whale-config-crypto` 加密后写入。
3. 明文环境变量——只允许在本地开发用（`WHALE_MAIL_MOCK` 场景），不要进生产。

> `secret_cipher` 存的是密文而非明文，密钥只在进程环境。代开时若 `WHALE_CONFIG_ENC_KEY` 缺失，
> 服务**直接失败并报错**，不会退化成明文落库——那等于把口令写进备份包。

### 6.3 账号开通

Mailcow 提供 REST API（管理台 `Configuration → Access → API` 创建读写 Key，并限制来源 IP）：

```bash
curl -sS -X POST https://mail.example.com/api/v1/add/mailbox \
  -H "X-API-Key: ${MAILCOW_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
        "local_part": "zhangsan",
        "domain": "example.com",
        "name": "张三",
        "quota": "2048",
        "password": "<随机生成>",
        "password2": "<同上>",
        "active": "1",
        "force_pw_update": "0",
        "tls_enforce_in": "1",
        "tls_enforce_out": "1"
      }'
```

注意 `quota` 单位是 **MiB**（`2048` 表示 2 GiB，`0` 表示不限）。

### 6.4 自动开通（M2 已落地）

两条路并存，按部署形态选：

| 方式 | 触发 | 适用 |
|---|---|---|
| 手工绑定 | 管理员在「邮箱 → 账号设置」录入地址与凭据引用 | 邮箱已存在（企业邮箱迁移期）、或只想接入部分人 |
| **自动开通** | `whale-auth` 建用户 → 领域事件 `CREATED` → 异步调 `whale-mail` → 调 Mailcow API | 新人入职即开，无需人工介入 |

链路：

```
whale-auth UserAppService.create
  └─ 事务提交后 UserDomainEvent(CREATED)
       └─ MailProvisionListener（AFTER_COMMIT，异步线程池 mailProvisionExecutor）
            └─ POST http://whale-mail:8099/mail/internal/provision
                 └─ MailProvisionServiceImpl：查重 → 生成地址与口令 → Mailcow add/mailbox → 落 mail_account
```

**两边都要开，缺一边不会开通**：

| 变量 | 作用 |
|---|---|
| `WHALE_AUTH_MAIL_PROVISION_ENABLED=true` | auth 侧是否发出回调 |
| `WHALE_MAIL_MAILCOW_ENABLED=true` | mail 侧是否真的调 Mailcow |
| `WHALE_MAIL_MAILCOW_BASE_URL` / `_API_KEY` | Mailcow 地址与全权限 Key |
| `WHALE_MAIL_DOMAIN` / `_IMAP_HOST` / `_SMTP_HOST` | 生成地址与协议端点，缺任一项返回 `DISABLED` |
| `WHALE_CONFIG_ENC_KEY` | 代开口令落库的加密密钥，**缺了代开直接失败**（不降级明文） |

返回状态含义（`state` 字段，全部可安全重试）：

| state | 含义 | 处理 |
|---|---|---|
| `CREATED` | 本次开通成功 | 无需处理 |
| `EXISTS` | 该用户已有邮箱 | 无需处理（幂等） |
| `DISABLED` | 配置缺失或开关关闭 | 按 `message` 补配置 |
| `CONFLICT` | 地址被占用（平台内或 Mailcow 内） | **不会自动改别人口令**，需人工在 Mailcow 删除或改用手工绑定 |
| `FAILED` | Mailcow 或落库异常 | 已回滚 Mailcow 侧邮箱，可直接重试 |

补开单个用户：

```bash
curl -sS -X POST http://127.0.0.1:8099/mail/internal/provision \
  -H "Content-Type: application/json" \
  -d '{"tenantId":1,"userId":1024,"username":"zhangsan","displayName":"张三"}'
```

**三个必须知道的约束**：

1. **失败不影响建用户**。邮箱没开成只在 auth 日志留一条 ERROR（`建用户后自动开邮箱失败，需人工补开: userId=...`），
   用户照常创建。取舍很明确：少个邮箱是功能缺失，建不出用户是事故。
2. **不强制首次登录改密**（`force_pw_update=0`）。平台自己要用这个口令登录 IMAP，
   员工在 Webmail 一改，平台侧密文立即失效、同步全断。
3. **初始口令不下发，也不需要下发**。接口不返回口令，解密只发生在服务端连 IMAP 的那一刻。
   一体化平台下员工的唯一入口是平台内的 `/mail` 子应用：它用平台登录态鉴权、由服务端代连 IMAP，
   员工全程不接触邮箱口令。SOGo 的 Webmail 界面**默认不对员工开放**（见 §十三）。
   例外场景（手机原生邮件客户端、日历/通讯录）由管理员在 Mailcow 侧重置口令后单独交付，
   这是按需例外，**不做成产品化的「本人自取」**——那等于把第二套凭据写进产品特性，与一体化方向相反。

### 6.5 文件夹对齐（M1）

IMAP 是双向的：用户在手机客户端或 SOGo 里新建、改名、删除的文件夹，平台侧必须跟上，
否则界面上永远只有五个系统文件夹，用户会以为「搬家搬丢了」。

| 时点 | 动作 | 成本 |
|---|---|---|
| 每轮同步开始前 | 自动探测一次（IMAP `LIST`） | 一次往返，远低于拉邮件 |
| 用户点「刷新」 | `POST /mail/folder/refresh` | 同上，不必等定时任务 |
| 探测失败 | 记 WARN，按本地位点继续同步 | 不阻断，也不下线任何文件夹 |

三条规则：

1. **系统文件夹按特殊用途纠名**。各服务商命名不同（`Sent` / `Sent Items` / `已发送`），
   写死一个默认名会让「已发送」永远同步不到。识别只看层级末段，且默认名优先于别名——
   用户自建一个叫 `Spam` 的普通文件夹不该被认成垃圾箱。
2. **纠名之后位点必须归零**。不同文件夹的 UID 空间互不相干，沿用旧位点会让新映射的文件夹
   从中途开始同步，历史邮件永久缺失。这一步做错的典型现象是「换了服务商之后老邮件全没了」。
3. **下线只针对自定义文件夹**。服务器短时不可用时探测结果可能为空，若连系统文件夹一起下线，
   用户看到的是「收件箱不见了」——比同步失败严重得多。下线时会把 `imap_name` 改名再置删除标记，
   释放唯一键，否则用户重建同名文件夹会插入失败。

自定义文件夹的同步额度是**收着来**的：单账号最多 20 个（按排序号），每个一次最多拉 50 封。
归档目录动辄几十个、每个上万封，全量纳入会把一轮同步从几十秒拖到一个多小时。
界面上仍能看到全部文件夹，点开时按需回源。

排查口径：新建的目录不出现 → 先点「刷新」；仍不出现 → 查 `whale-mail` 日志里的
`文件夹探测失败`，通常是 IMAP 认证过期或端口被改。

### 6.6 全文检索（M1）

搜索走 `mail_message` 的 ngram 全文索引（`ft_mail_message`，V3 迁移建立），
覆盖主题、发件人地址与显示名、摘要、正文。两个引擎按 `whale.mail.search.engine` 切换：

| 引擎 | 适用 | 代价 |
|---|---|---|
| `mysql`（默认） | 单库、十万级以内、要零运维 | 排序只有 BM25 一档，权重调不动 |
| `lucene` | 数据量大、需要跨字段加权排序 | 索引目录要落持久卷；多实例必须共享同一目录，否则各实例只搜得到自己写的部分 |

四条必须知道的口径：

1. **`ngram_token_size` 默认 2**，单个汉字切不出词项。单字查询会自动回退 `LIKE`，
   所以「搜一个字能搜到但很慢」是预期行为，不是索引坏了。
2. **正文可搜的前提是正文已回填**。正文是打开详情才从 IMAP 拉的（懒加载），
   没打开过的邮件只有头部可搜。Lucene 引擎会在回填后补写索引；MySQL 引擎由表自己维护。
3. **排序按相关度，不按时间**。搜关键词时用户要的是「最匹配的那封」，不是「最近的那封」；
   没有关键词时仍是时间倒序。
4. **迁移会锁表**。`ADD FULLTEXT` 在已有表上不允许并发写，十万级以上的表请在维护窗口执行，
   或临时关掉 Flyway 手工跑 `V3__search_and_bounce.sql`。

排障：`GET /mail/search/status` 回显当前引擎与是否启用 LIKE 兜底；Lucene 引擎怀疑索引漂移时
`POST /mail/search/reindex` 重建（MySQL 引擎返回 0，因为没有独立索引可重建）。

### 6.7 退信回执（M1）

平台发信成功只代表**下一跳收下了**，不代表对方最终收到。异步退信（DSN）是唯一的失败信号，
因此同步阶段会识别 `multipart/report` 邮件并解析入库：

- 落 `mail_delivery_report`，一行对应「一个收件人 + 一条结果」
- 类型判定：状态码 `5.x.x` → `HARD`（永久，重试无用）；`4.x.x` 或 Action=`delayed` → `SOFT`（临时，可稍后重发）
- 关联原邮件：按 DSN 里的 Message-ID 反查，带尖括号与不带两种写法都试
- 展示：`GET /mail/delivery/report/page` 分页查询；打开退信邮件时详情里的 `bounces`
  会在正文上方给出「退给谁、什么类型、远端怎么回」

**一个必须说清的边界**：DSN 只会投给**原发件人**。如果 Mailcow 把退信统一投到 `postmaster@`
（`postmaster_address` 配置），那些退信就不属于任何员工账号，平台内看不到。要让员工看到自己的退信，
需保证 DSN 回到发信人信箱，而不是全部落到 postmaster。验证方式：给一个不存在的地址发一封信，
看退信进的是谁的收件箱。

> 注意：**SMTP 当场拒绝（5xx 同步返回）不会产生 DSN**——那种情况在发信接口就直接失败了，
> 不会走到这里。这里接的是「先接受、后失败」的异步退信。

**存量退信不回溯**：退信识别只在邮件**首次入库**时触发。V3 上线前已经同步进来的退信邮件
不会被补解析——那些邮件的正文仍在服务器上，重同步也不会重新判定。需要历史数据时，
把账号解绑重绑（会重建索引），代价是重新拉一遍头部。

---

## 七、监控与告警

`whale-mail` 侧用现有 Prometheus（`deploy/monitoring/prometheus.yml` 已抓 `127.0.0.1:8099`）。
Mailcow 侧没有原生 Prometheus 端点，用下面四条兜底：

| 指标 | 采集方式 | 告警阈值 | 为什么重要 |
|---|---|---|---|
| 队列积压 | `docker compose exec postfix-mailcow postqueue -p \| tail -1`，cron 写 node_exporter textfile | > 50 持续 10 分钟 | 积压意味着对端在拒收或出站被封 |
| 磁盘使用率 | node_exporter | > 80% | 邮件主机磁盘满 = 全站退信，且 MySQL 可能损坏 |
| 证书剩余天数 | `blackbox_exporter` 的 `ssl_earliest_cert_expiry` | < 15 天 | 挂外部证书时 ACME 不接管，没有告警就一定会过期 |
| `whale-mail` 同步失败 | 应用日志 `邮件同步失败` 计数 | 5 分钟内 > 3 次 | IMAP 认证过期或端口被改 |

人工巡检命令：

```bash
cd /opt/mailcow-dockerized
docker compose ps                              # 容器健康
docker compose exec postfix-mailcow postqueue -p   # 队列
docker compose exec postfix-mailcow postconf -n | grep relayhost  # 中继是否生效
docker compose logs --tail=200 postfix-mailcow | grep -i "reject\|defer"
docker stats --no-stream                       # 内存是否顶到 override 里的上限
```

---

## 八、备份与恢复

**Mailcow 的备份必须包含四类东西**，少一类就恢复不全：

| 内容 | 位置 | 备份脚本是否覆盖 |
|---|---|---|
| 邮件正文 | Docker 卷 `vmail` | ✅ |
| 账号/别名/配额/元数据 | MySQL `mailcow` 库 | ✅ |
| 会话与反垃圾缓存 | Redis | ✅（可丢，但丢后需重算） |
| 自定义配置 | `mailcow.conf`、`data/conf/`（中继、nginx 片段） | ❌ **必须手工加** |

```bash
cd /opt/mailcow-dockerized
# 官方脚本（版本差异：老版用 `backup all`，新版交互式选择；先 --help 确认）
./helper-scripts/backup_and_restore.sh backup all
# 补配置
tar czf /opt/mailcow-backup/conf-$(date +%F).tar.gz mailcow.conf data/conf
```

恢复顺序固定：**先停栈 → 恢复 MySQL → 恢复 vmail → 恢复 Redis → 起栈 → 验证登录与收发**。
顺序颠倒会出现「邮件在但账号表是空的」——索引指向不存在的邮箱。

恢复后必验三项：

```bash
docker compose exec dovecot-mailcow doveadm mailbox status -u zhangsan@example.com messages INBOX
docker compose exec postfix-mailcow postqueue -p
curl -sS -o /dev/null -w "%{http_code}\n" https://mail.example.com/
```

---

## 九、IP 预热（只在直投时需要）

走中继可整节跳过——这是推荐中继的第二个理由。

新 IP 突然发大量邮件，接收方会按「可疑新源」直接限流甚至拉黑。爬坡节奏：

| 周 | 日发信量上限 | 观察点 |
|---|---|---|
| 第 1 周 | 200 | 4xx 临时拒收率 < 5% |
| 第 2 周 | 1,000 | 回信/投诉率 < 0.1% |
| 第 3 周 | 5,000 | 主要收件方（Gmail/Outlook/QQ）是否仍进垃圾箱 |
| 第 4 周起 | 10,000+ | 接入 Google Postmaster Tools 看域名声誉 |

预热期间**不要发营销类内容**，只用真实业务通知。一封被大量标记为垃圾的邮件能把两周爬坡作废。
预热不可压缩，这段时间任何「加快一下」的想法都要压住。

---

## 十、故障排查

### 10.1 发出去的邮件进垃圾箱

按顺序排查，前面的不修，后面白查：

1. `mail-deliverability-check.ps1` 是否全 PASS
2. 收件方原始邮件头里 `Authentication-Results`：
   - `spf=pass` / `dkim=pass` / `dmarc=pass` 三项都要过
   - `dkim=fail` 但 Mailcow 显示已签名 → DKIM **公钥记录没发布成功**（多为超 255 字符被截断）
3. PTR 是否与 A 一致
4. 正文是否含短链、纯图片、`<img>` 指向不可达域名——都会拉高评分

### 10.2 常见退信码

| 码 | 含义 | 处理 |
|---|---|---|
| `550 5.7.1 ... blocked` | IP/域名信誉或 SPF 不匹配 | 查 SPF 与 PTR，别急着换 IP |
| `421 4.7.0 ... try later` | 对端限流 | 降速，这是预热期的正常反应 |
| `550 5.1.1 user unknown` | 收件人不存在 | 正常，但要清理地址簿，反复发会伤信誉 |
| `554 5.7.1 rejected` | 内容被判垃圾 | 查 Rspamd 打分 `docker compose exec rspamd-mailcow rspamc stat`，看命中哪条规则 |
| `451 4.4.1` 队列超时 | 出站 25 不通 | `postconf -n \| grep relayhost` 确认中继生效 |

### 10.3 IMAP 认证失败（`AUTHENTICATIONFAILED`）

按概率排序：

1. 邮箱开了 TOTP 二次验证，却用了登录口令 → 改用**应用专用密码**
2. `secret_ref` 指向的环境变量不存在或为空 → 看日志里 `凭据解析失败`
3. 密码里有 `$`、`!` 等字符，写进 shell 环境变量时被展开 → 用单引号或配置中心
4. `tls_enforce_in=1` 而客户端用了明文 143 → 换 993

### 10.4 Webmail 能开但极慢

先看 `docker stats`：ClamAV 顶到内存上限会疯狂 swap，拖慢整个 Docker 网络。
4G 内存的机器建议 `SKIP_CLAMD=y`，反垃圾靠 Rspamd 规则兜底，代价是收不到含宏病毒的附件告警。

---

## 十一、安全硬化

| 项 | 做法 |
|---|---|
| 管理台不暴露公网 | `/admin` 与 `/api` 加 IP 白名单（改 `data/conf/nginx/site.conf` 的 server 块，或前置一层自建 nginx 做 `allow/deny`）。**升级 mailcow 时该文件可能冲突，改前先备份** |
| Webmail 不接 qiankun | SOGo 独立域名访问。接进基座会让邮件页与平台共享 Cookie 与同源上下文，一封 XSS 邮件就能读平台 token。**且 SOGo 界面默认不对员工开放**：日常入口是平台内 `/mail` 子应用，SOGo 只用于运维验收与例外场景 |
| 明文端口关闭 | 143/110/4190 绑 `127.0.0.1`，只留 993/465/587 给公网 |
| 禁止开放中继 | `ACL_ANYONE=disallowed`，这一项被改成 `all` 会在几小时内被扫成开放中继并拉黑 |
| API Key 限来源 | Mailcow API Key 创建时填允许的 IP，泄漏后损失可控 |
| 邮箱配额 | 默认 2 GiB；不限配额让单个邮箱能把磁盘吃满 |
| 日志脱敏 | `whale-mail` 不打正文与口令；Mailcow 日志会带收件人地址，落盘前做保留期（30 天） |

---

## 十二、验收清单

| # | 项 | 期望 |
|---|---|---|
| 1 | `docker compose ps` | 全部 running（watchdog / netfilter 正常） |
| 2 | `mail-deliverability-check.ps1` | 无 FAIL |
| 3 | 管理台登录 | HTTPS 正常、证书主题正确、默认密码已改 |
| 4 | 建一个测试邮箱 | 在平台 `/mail` 能同步出收件箱（SOGo 登录只作协议自检，不作为员工入口） |
| 5 | 内部互发 | 秒级到达，正文与附件完整 |
| 6 | 发到 Gmail | 进收件箱；原始头三项 auth 全 pass |
| 7 | 发到 QQ 邮箱 | 同上 |
| 8 | 收外部信（Gmail → 自己） | 到达 INBOX，不被灰名单长期卡住 |
| 9 | `whale-mail` 绑账号并同步 | 列表出现邮件、详情能回源正文、附件能下载 |
| 10 | 从 `whale-mail` 发一封带附件的信 | 外部收件方能看到附件 |
| 11 | 停 Mailcow 后访问 `/mail` | 历史列表仍可读，新邮件报错可解释（不是 500 白屏） |
| 12 | 备份并恢复一次 | 恢复后邮件、账号、别名齐全 |
| 13 | 在 `/mail` 搜一个正文里的词 | 能命中（前提是那封邮件已打开过、正文已回填），结果按相关度排序 |
| 14 | 搜一个单字 | 仍能命中（走 LIKE 兜底），只是慢一些 |
| 15 | 给不存在的外部地址发信 | 收到退信，`/mail` 打开该退信时正文上方显示收件人、`HARD`、远端应答 |

第 6/7 项建议用 [mail-tester.com](https://www.mail-tester.com/) 交叉验证得分（≥ 9/10 才算可上线）。

---

## 十三、员工入口边界（认证口径）

这一节定死口径，避免以后再反复讨论「要不要给员工发邮箱口令」。

| 场景 | 入口 | 凭据 | 说明 |
|---|---|---|---|
| 日常收发 | 平台内 `/mail` 子应用 | 平台登录态 | 服务端代连 IMAP，员工不接触邮箱口令 |
| 手机原生邮件客户端 | IMAP/SMTP 直连 | 邮箱口令 | OIDC 帮不上：原生客户端不走授权码流程，只能给口令 |
| 日历 / 通讯录 | SOGo（CalDAV/CardDAV） | 邮箱口令 | 平台内暂无替代，属例外 |
| 运维排查 | SOGo Webmail / Mailcow 管理台 | 管理员账号 | 仅运维侧使用 |

据此的三条取舍：

1. **不做初始口令自取接口**。它唯一的作用是把口令交给要登 SOGo 的人，而这个人不在设计里。
   做了它等于承认「两套凭据」是常态，与一体化方向相反。
2. **当前不做 SOGo 的 OIDC 单点登录**。它只能消灭「浏览器登网页版」，而网页版入口本身是冗余的；
   手机原生客户端照样要口令。触发条件很明确：出现「必须用 SOGo 网页版」的硬需求时再评估，
   届时的成本是 auth 侧补授权服务器 + SOGo 挂自定义 `sogo.conf`。
3. **例外留人工后门，但不产品化**。要用手机客户端的人，管理员在 Mailcow 重置口令后单独交付，
   同时在平台侧同步 `secret_cipher`（见附录第一条）。

判断依据只有一句话：**口令是机器凭据，不是员工凭据。** 谁要把它变成员工凭据，先问清那个人为什么绕开平台。

---

## 附：仍未完成（不是遗漏）

- **邮箱口令重置入口**：管理员在 Mailcow 侧重置口令后，平台侧 `secret_cipher` 仍是旧值，
  同步与发信会一起失败。目前只能手工改库，应补平台侧重置接口把两边闭环
  （员工自行在 Webmail 改密也会触发同样问题，故 `force_pw_update=0`）
- **邮件规则**：按发件人/主题自动归档到指定文件夹。文件夹已稳定（§6.5），这条可以做了。
  全文检索与退信回执已在 §6.6 / §6.7 落地，不再列为缺口

## 附二：明确的非目标（不是缺口）

- **初始口令交付 / 本人一次性自取**：不需要。员工入口是平台内子应用，服务端代连 IMAP，口令无人可见（§6.4、§十三）
- **SOGo 与平台 OIDC 单点登录**：当前不做。它只能解决浏览器登网页版，而该入口冗余；手机端仍要口令（§十三）
