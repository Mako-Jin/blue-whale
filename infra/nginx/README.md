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

```bash
# ⚠️ 白名单形式！不带白名单的 envsubst 会把 nginx 自己的 $remote_addr /
#    $request_method / $proxy_add_x_forwarded_for 一并替换掉，配置立刻报废。
set -a; . infra/nginx/.env.example; set +a

envsubst '${HTTP_PORT} ${SERVER_NAME} ${BACKEND_UPSTREAM} ${STATIC_ROOT} ${MAX_BODY_SIZE} \
          ${REPORT_RATE} ${REPORT_BURST} ${REPORT_CONN} \
          ${API_RATE} ${API_BURST} ${API_CONN}' \
  < infra/nginx/conf.d/bluewhale.conf.template \
  > /etc/nginx/conf.d/bluewhale.conf

cp infra/nginx/snippets/*.conf /etc/nginx/snippets/     # 两个 include 依赖它们
```

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
nginx -t                                    # 语法自检，上线前必跑
node scripts/verify-nginx-config.mjs        # 结构性自检（见下）
```

`scripts/verify-nginx-config.mjs` 补的是 `nginx -t` **查不到**的那一类错：
zone 名拼错（`limit_req zone=xxx` 引用了不存在的 zone）、模板变量漏在
`.env.example` 之外、白名单没覆盖全导致渲染后残留 `${`。
它不需要装 nginx，因此可以在 CI 里跑。

## 四、几条不能想当然的地方

| 点 | 说明 |
| --- | --- |
| **上报与查询共用 URI** | `POST /api/logs/client` 上报、`GET /api/logs/client` 查询是**同一个路径**。限流只能靠 `map $request_method` 区分：把非 POST 的 key 置空，nginx 对空 key 直接 `NGX_DECLINED`（不计入）。否则查询会被上报的配额挤掉 —— 那是个已鉴权的接口，被误限比不限更糟 |
| **`42900` 由网关发，不由后端发** | 后端 `ResultCode.TOO_MANY_REQUESTS(42900)` 早在枚举里备着，但**后端从不产生它** —— 产生它的是这里的 `error_page 429`。`server/README.md` 的错误码表"来源"列已据此改 |
| **`limit_req_status` 默认是 `503`，不是 `429`** | 两行 `limit_*_status 429;` **不是装饰**：没有它们 `error_page 429` 永远不触发，超限会以"服务不可用"的形态出现，与真实宕机混进同一个告警通道 |
| **429 响应体不带 `timestamp`** | nginx 拿不到 epoch 毫秒（`$msec` 是秒 + 三位小数）。"格式对、数值错"比没有更坏。前端也不依赖它：`common-tools/http` 的 `readBody()` 对非 JSON 体原样返回，429 走 **HTTP 状态码**分类（`retryable === true`），上报通道随即指数退避 |
| **静态资源刻意不猜** | 本配置管 `/api` 入口。静态资源同机目录 / 对象存储 / 独立域名属部署形态，`location /` 默认转发给后端。猜错的表现是"页面 403 但 API 正常"这种一眼看不出原因的组合 |
| **阈值是部署级参数** | 全部走 `.env.example`（`infra/README.md` 约定 1）。不同环境的合理值不同 —— 这正是它们不硬编码的原因 |

## 五、当前状态：**未在真实 nginx 上验证过**

开发机既没有 nginx 也没有 docker（`nginx -v` / `docker --version` 均 command not found），
因此这份配置**没有跑过 `nginx -t`**，也**没有实际压出过一次 429**。

已做的只有结构性校验（`node scripts/verify-nginx-config.mjs`）与逐条人工复核。
**首次部署时必须先 `nginx -t`**。这与 `docs/decisions/0010` 达梦那条
「未用真实实例验证」同一处置：**如实标注，不假装已验证**。
