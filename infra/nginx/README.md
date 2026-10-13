# infra/nginx — 网关：上报限流 + 反向代理

放两件事：**两条客户端上报通道的限流**，以及**到后端的反向代理**。

决策与论证见 `docs/decisions/0027`。这里只说怎么用、以及几条不能想当然的地方。

## 一、为什么限流在这一层，而不是在后端

后端**刻意不做限流**。三条既有约定同时指向这个结论：

| 出处 | 原文 |
| --- | --- |
| `infra/README.md` 约定 4 | 「网关是统一入口。鉴权、限流、跨域在 `nginx/` 或后端网关层解决，**不要散落在各应用里**」 |
| `docs/decisions/0003` | 「限流阈值」归**部署级配置** → 成熟组件（Nacos / Apollo）→ **运维** |
| 后端 **5 处**注释 | `X-Forwarded-For` 可被伪造，**不可作为鉴权或风控依据** |

最后一条是关键。应用层**拿不到**可信的客户端 IP —— 它无法判断自己前面有没有代理、
该信几跳。在那里按 IP 限流，伪造一个头就能绕过，得到一个"看起来有防护"的假开关。

而 nginx **知道**答案是几跳，因为它就是那个代理。`set_real_ip_from` 配好后，
`$binary_remote_addr` 从"最后一跳的地址"变成真实客户端 IP ——
**限流只有在这一层才可能做对。**

## 二、文件

```
infra/nginx/
├── .env.example                       变量模板（真实值不入库）
├── conf.d/
│   └── bluewhale.conf.template        ← 模板，${...} 待渲染
└── snippets/
    ├── bluewhale-real-ip.conf         真实 IP 处理（**默认全注释**）
    └── bluewhale-proxy.conf           转发到后端的公共指令
```

## 三、部署

### 1. 放到 conf.d，不要放成独立 nginx.conf

`limit_req_zone` / `limit_conn_zone` / `map` / `upstream` 只能定义在 **`http` 上下文**。
Docker 官方 nginx 镜像与 Debian / Ubuntu 的 `nginx.conf` 里都已有
`include /etc/nginx/conf.d/*.conf;`，**且它就在 `http` 里** —— 所以 conf.d 是唯一
不用改主配置就能落脚的地方。

### 2. 渲染模板

**推荐用仓库里的渲染器**（它只替换已知的 `${变量}`，因此**不可能**把 nginx 自己的
`$remote_addr` 吃掉 —— 那个陷阱是手工渲染最容易踩、且踩了之后配置仍然"语法合法"的）：

```bash
# 渲染到暂存目录，检查无误后再拷到 /etc/nginx
node scripts/verify-nginx-config.mjs --emit /tmp/bw-nginx-deploy

nginx -t -p /tmp/bw-nginx-deploy -c /tmp/bw-nginx-deploy/nginx.conf   # 先在这棵树里验
cp /tmp/bw-nginx-deploy/conf.d/*.conf  /etc/nginx/conf.d/
cp /tmp/bw-nginx-deploy/snippets/*.conf /etc/nginx/snippets/
nginx -t                                                              # 再在部署位置验
```

<details>
<summary>手工用 <code>envsubst</code> 的话（⚠️ 必须带白名单）</summary>

```bash
# 不带白名单的 envsubst 会把 nginx 自己的 $remote_addr / $request_method /
# $proxy_add_x_forwarded_for 一并替换掉，配置立刻报废。
set -a; . infra/nginx/.env.example; set +a

envsubst '${HTTP_PORT} ${SERVER_NAME} ${BACKEND_UPSTREAM} ${STATIC_ROOT} ${MAX_BODY_SIZE} \
          ${REPORT_RATE} ${REPORT_BURST} ${REPORT_CONN} \
          ${REPORT_GLOBAL_RATE} ${REPORT_GLOBAL_BURST} \
          ${API_RATE} ${API_BURST} ${API_CONN}' \
  < infra/nginx/conf.d/bluewhale.conf.template \
  > /etc/nginx/conf.d/bluewhale.conf

cp infra/nginx/snippets/*.conf /etc/nginx/snippets/     # 两个 include 依赖它们
```

漏掉白名单里的任何一个变量名，渲染后就会留下一个字面 `${...}` —— 这次改动新增的两个
`${REPORT_GLOBAL_*}` 是最新的两个。`node scripts/verify-nginx-config.mjs` 会检查
模板用到的每个变量都在 `.env.example` 里。

</details>

### 3. 先确定"真实 IP"这一段，再上线

打开 `snippets/bluewhale-real-ip.conf`，按文件里的三段（**什么时候必须打开 /
什么时候绝不能打开 / 启用前逐条确认**）决策。**默认全注释**，即"本机就是边缘"。

配错的两种表现都很难当场看出问题：

| 配错方向 | 表现 |
| --- | --- |
| 前方有代理却没启用 `set_real_ip_from` | 所有客户端共用一个 IP → 限流退化成"全局一个桶" → **人一多就整片 429**，真攻击者毫发无损 |
| 本机是边缘却启用了 `real_ip_header X-Forwarded-For` | 用**可伪造的**头覆盖了真实 IP → **伪造一下即绕过** |

### 4. 验证

```bash
node scripts/verify-nginx-config.mjs                          # 结构性自检（CI 可跑，不需要 nginx）
node scripts/verify-nginx-config.mjs --nginx /usr/sbin/nginx   # 顺带真跑一次 nginx -t
nginx -t                                                      # 到了部署位置再跑一次
```

`scripts/verify-nginx-config.mjs` 查的是 **`nginx -t` 查不到**的那一类错：渲染后残留
`${...}`、白名单没覆盖全导致 nginx 自己的 `$request_method` 被吃掉、上报路径漏挂某一层桶、
**全局桶的 key 被写成 `$binary_remote_addr`（退化成第二个按 IP 桶）**、
真实 IP 网段被写成 `0.0.0.0/0`。它不需要装 nginx，因此能在 CI 里跑。

> **一处实测修正**：`0027` 把"`limit_req zone=xxx` 引用了不存在的 zone"列为
> "`nginx -t` 只在启动时才发现"。在 nginx 1.20.1 上实测，`nginx -t` **当场就会报**
> `[emerg] zero size shared memory zone "xxx"`；脚本的价值在于报得更早、且直指缺哪个 zone。
> 记录见 `docs/decisions/0028`。

## 四、几条不能想当然的地方

| 点 | 说明 |
| --- | --- |
| **上报与查询共用 URI** | `POST /api/logs/client` 上报、`GET /api/logs/client` 查询是**同一个路径**。限流只能靠 `map $request_method` 区分：把非 POST 的 key 置空，nginx 对空 key 直接 `NGX_DECLINED`（不计入）。否则查询会被上报的配额挤掉 —— 那是个已鉴权的接口，被误限比不限更糟 |
| **`42900` 由网关发，不由后端发** | 后端 `ResultCode.TOO_MANY_REQUESTS(42900)` 早在枚举里备着，但**后端从不产生它** —— 产生它的是这里的 `error_page 429`。`server/README.md` 的错误码表"来源"列已据此改 |
| **`limit_req_status` 默认是 `503`，不是 `429`** | 两行 `limit_*_status 429;` **不是装饰**：没有它们 `error_page 429` 永远不触发，超限会以"服务不可用"的形态出现，与真实宕机混进同一个告警通道 |
| **429 响应体不带 `timestamp`** | nginx 拿不到 epoch 毫秒（`$msec` 是秒 + 三位小数）。"格式对、数值错"比没有更坏。前端也不依赖它：`common-tools/http` 的 `readBody()` 对非 JSON 体原样返回，429 走 **HTTP 状态码**分类（`retryable === true`），上报通道随即指数退避 |
| **静态资源刻意不猜** | 本配置管 `/api` 入口。静态资源同机目录 / 对象存储 / 独立域名属部署形态，`location /` 默认转发给后端。猜错的表现是"页面 403 但 API 正常"这种一眼看不出原因的组合 |
| **阈值是部署级参数** | 全部走 `.env.example`（`infra/README.md` 约定 1）。不同环境的合理值不同 —— 这正是它们不硬编码的原因 |
| **上报限流是两层，标定规则不同** | ① 按 IP = **隔离**参数，按"最大的 NAT 出口"算：共用出口的 N 个客户端 ⇒ 该 IP 是 `N/3 r/s`（每客户端 3 秒 1 个请求），**不是**"一个客户端 1 个请求"；② 全局 = **容量**参数，与人数无关，按后端能吸收的写入速率定。把两者混着调，最容易调出"看起来有防护、实际拦不住"或"正常用户被误伤" |
| **429 是背压，不是丢弃** | 客户端拿到 429 会把这一批**退回队首 + 指数退避重试**（`common-tools` 的 `BatchChannel`，封顶 30s）。真正丢数据的只有一处：持续超限把客户端缓冲（`maxBuffer 500`）撑爆，那时丢的是**最旧**的一批。实测：默认值下 450 个连续 POST → `200 × 216 / 429 × 234`；等 2.2 秒后再打 5 个 → 又放行 3 个（桶会回填） |
| **上报与查询共用 URI 那一段是实测过的** | 同一 URI 连打 15 个 GET 全部 200、15 个 POST 得到 `200×3 / 429×12` —— `map $request_method` 的分流真的把查询摘在外面了，不是"看起来应该能行" |

## 五、当前状态：**已在 nginx 1.20.1 上验证过**

`0027` 初版写的是"未在真实 nginx 上验证过"。**这一条现在不成立了** —— 本机有
`D:/tools/installer/nginx/nginx-1.20.1/nginx.exe`，用它把配置真跑了一遍
（**只读安装包**：拷到临时目录里跑，不动安装目录、不装到系统）。

| 验的是什么 | 结果 |
| --- | --- |
| `nginx -t`（部署形态的那棵树） | **通过** |
| 15 个连续 POST，阈值压到 2r/s / burst 2 | `200 × 3 / 429 × 12`，响应体 `{"code":42900,"message":"请求过于频繁","data":null}` |
| **同一 URI** 的 15 个连续 GET | `200 × 15` —— 查询**没有**被上报配额挤掉 |
| 等 2.2 秒后再打 5 个 POST | `200 × 3 / 429 × 2` —— 桶会回填 |
| 默认值下 450 个连续 POST | `200 × 216 / 429 × 234`，`error_log` 命中 `bluewhale_report_per_ip` |
| 只把**全局**阈值压到 2r/s（按 IP 放到 1000r/s） | 全部 429，`error_log` 命中 `bluewhale_report_global` |
| 反例：把某个 `zone=` 引用的名字改错 | `nginx -t` 报 `[emerg] zero size shared memory zone`（见 §4 的修正） |
| 反例：把 `set_real_ip_from` 写成 `0.0.0.0/0` | 被 `verify-nginx-config.mjs` 拦下 |

复现（把 nginx 换成你自己的路径）：

```bash
node scripts/verify-nginx-config.mjs --emit /tmp/bw-nginx-deploy
nginx -t -p /tmp/bw-nginx-deploy -c /tmp/bw-nginx-deploy/nginx.conf
# 压测：需要一个活的后端在 :8080（否则每请求会多等 ~2s 才拿到 502，
#      反而把请求间隔拉开、压不出 429 —— 这个坑我踩过一次）
```

**仍未验证的**（不要把上表当成"全都验过了"）：

- **阈值定得合不合适**：上表验的是"能不能拦住"，不是"该定多少"。上线后要看
  `error_log` 里 `limiting requests` 出现的频率，再按 `.env.example` 的两条公式回标。
- **多实例**：`limit_req_zone` 是**单机内存**的，多台 nginx 各自计数，实际上限 = 配置值 × 实例数。
- **Linux 构建**：本次用的是 Windows 构建。配置是同一份，但严格来说 `-t` 只覆盖了这个构建。

**首次部署前仍必须先 `nginx -t`** —— 那是**部署位置那棵树**的检查，与本次验的不是同一棵树。
这与 `docs/decisions/0010` 达梦那条同一处置：**能验的验掉，不能验的如实标注**。
