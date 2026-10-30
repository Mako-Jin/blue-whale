# 基线加固批次检查报告（K-5/K-4 + T-22/T-23 + T-03 + T-02）

> 日期：2026-10-30 ｜ 范围：T-24 / T-13 / T-15 / T-20 / T-21 / T-22 / T-23 / T-03 / T-02（对应盘点 [2026-10-30-workspace-backlog-inventory.md](2026-10-30-workspace-backlog-inventory.md)）
> 关联提交：`e5103c74`（基线批）、`2fd14a92`（批A）、`ee557d66`（批B）、`8c866545`（批C 后端）；批C 前端与本文档同批提交。
> 对照清单：[code-review-checklist.md](../checklists/code-review-checklist.md)

## 一、变更摘要与自检

| 项 | 变更 | 自检要点 | 结论 |
|---|---|---|---|
| T-24 | 删除根目录运行时残留 logs/、data/、.tmp/、*.log、dump.rdb（≈151MiB） | 全部在 `.gitignore` 覆盖内，`git status` 零变化；删除前确认无 java/mysqld/redis 进程占用 | 通过（无代码变更） |
| T-13 | 20 个数据源服务 `application.yml` 注入显式 `spring.datasource.hikari` 块 | 池参数全部经 `DB_POOL_*` 环境变量外置（默认 5/2、超时/泄漏检测），无魔法值硬编码入代码；正则校验 20/20 位置与缩进正确；`mvn -o -DskipTests -T 1C install` 52 模块通过 | 通过 |
| T-15 | Grafana dashboards provisioning + `bluewhale-overview` 看板（6 面板） | compose 只读挂载；JSON 解析校验通过；无凭据；容器内加载待 B-01 恢复后补验 | 通过（附阻塞注记） |
| T-20/T-21 | architecture.md 补 23 服务全景与限界上下文；新建 6 份指南；README 目录树补全 | 文档与 service-catalog.md 口径一致（端口/库/前缀）；链接无死链（新建文档均已存在） | 通过 |
| T-22 | LICENSE（Apache-2.0）+ `.gitattributes` | 换行规则含 ps1/bat CRLF、sh/mvnw LF、二进制清单；本轮不 renormalize，无巨量 diff | 通过 |
| T-23 | `.trae/` 保留入库，docs/README 约定第 0 条登记 | 用户拍板口径留痕 | 通过 |
| T-03 | AdminPasswordInitializer 随机轮换 + must_change_password + smoke env 化 | 见下 | 通过 |
| T-02 | refresh 令牌 HttpOnly Cookie + access 内存化 + CSRF 双提交（前后端，用户拍板 HttpOnly 全方案） | 见「三、T-02 重点复核」 | 通过 |

## 二、T-03 重点复核（安全项）

1. **出厂种子检测**：常量 `$2a$10$rzq21So0/…` 与 init.sql:385 逐字符一致；已轮换后幂等跳过，重复启动无副作用。
2. **随机口令质量**：SecureRandom（非 Random）；16 位，三类字符各至少一次 + Fisher-Yates 全量洗牌；剔除易混淆字符集；无硬编码盐/密钥。
3. **明文暴露面**：仅启动 WARN 分隔框打印一次（日志面板可见性 = 服务器本机权限）；响应体不回传口令；BCrypt 落库。
4. **强制改密闭环**：`must_change_password` 列（幂等迁移照 information_schema PREPARE 既有模式）；改密成功清标记；登录/刷新/切换租户响应统一透出。前端消费弹窗在批C（T-02）落地。
5. **smoke 无硬编码**：`grep 123456 scripts/smoke` = 0；`SMOKE_PASSWORD`/`SMOKE_TESTUSER_PASSWORD` 缺失即 throw，带明确指引；29 个脚本 Parser 语法 0 错误。
6. **分层合规**：轮换器在 starter 组合根编排，落库走 UserRepository + User 聚合行为，未直连 Mapper/裸写 SQL。

## 三、T-02 重点复核（安全项，前后端同批）

1. **令牌暴露面收敛**：refreshToken 全链路不再进响应体（`AuthTokenIssue` 分离载荷与令牌，接口层才写 Cookie）也不进 localStorage（前端 `grep` 复验 0 残留）；access 令牌仅存模块级内存 Map（页面刷新即清，`bootstrapAuth` 静默恢复）。
2. **Cookie 属性**：`whale_refresh_token` HttpOnly + Path=/auth（不随业务请求外发）+ SameSite=Strict + MaxAge 对齐 refreshToken 有效期；Secure 经 `whale.auth.cookie.secure`（env `WHALE_AUTH_COOKIE_SECURE`）控制，默认 false 适配本机 HTTP 联调。
3. **CSRF 防护**：`whale_csrf` 32 字节 SecureRandom hex、登录/刷新/切换租户轮换；`/auth/refresh`、`/auth/logout` 双提交校验（`X-CSRF-Token` 头 == Cookie，缺失或不等一律 403）；前端 @bluewhale/http 非 GET 自动回传，刷新自身路径豁免防递归。
4. **会话续期闭环**：401 → 单飞刷新（并发合并同一次请求）→ 原请求重放一次 → 仍 401 清态抛出；下载/SSE 同口径；登录/刷新路径不触发续期。
5. **破坏性变更兼容**：`RefreshRequest`/`LogoutRequest` 删除，`LoginResponse` 不含 refreshToken——grep 确认前端/脚本 0 处消费旧字段；e2e `tenant-switch` 改从响应体取 accessToken；smoke 走 `SMOKE_PASSWORD` 环境变量（批B）不受影响。
6. **首登改密闭环（消费批B）**：登录响应 `mustChangePassword=true` → 6 个带登录页应用渲染不可关闭弹窗 → `PUT /user/{id}/password`（≥12 位三类字符）→ 尽力 logout 清刷新 Cookie → 回登录页重登；持久约束由后端 T-03 负责。
7. **偏离点（已登记）**：logout 缺 refresh Cookie 严格 401（不静默成功）；switch 不加 CSRF（凭 Bearer access 鉴权，Cookie 仅作令牌来源）；5 个子应用令牌改 qiankun host-props 注入（基座内存化后的必要补充，复用 whale-im 既有模式）。
8. **遗留**：`admin/src/api/generated/auth-schema.d.ts` 为 OpenAPI 生成产物仍含 refreshToken（无引用），后端启动后 `pnpm --filter whale-admin openapi:types` 重生成消除；Cookie 下发/CSRF 403/静默续期/首登改密的实机验证与 Playwright 实跑待 B-01/B-02。

## 四、验证记录

- `mvn -o -DskipTests -T 1C install`：52 模块 BUILD SUCCESS（基线批）。
- `mvn -o -pl whale-services/whale-service-auth/whale-service-auth-starter test -am`：**410 用例 0 失败**（含新增 AdminPasswordInitializerTest 4 例、改密清标记、登录响应字段）。
- 批C 复跑 `mvn -o -pl whale-service-auth-starter,whale-gateway test -am`（13 模块 BUILD SUCCESS）：auth-starter **421 用例 0 失败**（含新增 `AuthCookieManagerTest` 9 例）、gateway **120 用例 0 失败**。
- 批C 前端：whale-auth / @bluewhale/http 共享包 tsup 构建成功（含 http smoke.mjs Cookie 流断言全过）；全仓 `pnpm run lint` exit 0（独立复跑）；e2e `tsc --noEmit` 通过；grep 复验 token 类 localStorage 读写与 `getRefreshToken`/`PLATFORM_*_TOKEN_KEY` 全仓 0 残留。
- 看板 JSON `JSON.parse` 通过；Hikari 块 20/20 正则校验通过。
- 本机 Docker/MySQL/Redis/Nacos 未启动（B-01/B-02），实机联调（随机口令启动日志、首登改密、Grafana 加载、内网令牌）留待环境恢复。

## 五、遗留

- ~~T-02（V-03）前后端 HttpOnly 迁移进行中~~ ✅ 已完成并提交；实机联调（Cookie 下发、CSRF 403、静默续期、首登改密全流程）与 Playwright e2e 实跑待 B-01/B-02 环境恢复。
- `admin/src/api/generated/auth-schema.d.ts`（OpenAPI 生成产物）待后端启动后重生成。
- 命名符合性（E15）：新增类 `com.bluewhale.auth.bootstrap.*`、`com.bluewhale.auth.web.AuthCookieManager`、配置键 `whale.auth.admin-password.*`/`whale.auth.cookie.secure`、cookie `whale_refresh_token`/`whale_csrf` 均按「品牌词根 + 域词」规则。
