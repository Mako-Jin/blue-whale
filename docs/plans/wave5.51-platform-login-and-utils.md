# Wave 5.51：基座真实登录 + 安全/工具类补齐

> 关联需求：REQ-FUNC-010（门户/基座）、REQ-ARCH-001（权限中心）、REQ-SEC-002（JWT）、REQ-DOC-001（四类信息留档）
> 制定日期：2026-10-24 ｜ 状态：已完成（2026-10-24）
> 上游依据：[todo-inventory-2026-10-23.md](todo-inventory-2026-10-23.md)（Top 1 / Top 5）、[wave5.46-phase4-business-apps.md](wave5.46-phase4-business-apps.md) 轨 G1/G2、[whale-auth-backlog.md](whale-auth-backlog.md)
> 执行原则：先落文档 → 编码 → 单测 → 构建/检查 → 检查报告 → 提交推送

---

## 一、需求项

### 1.1 基座真实登录 + proxy（阻塞所有前端联调）

| # | 需求 | 现状 | 目标 |
|---|---|---|---|
| L1 | 基座登录对接 `POST /auth/login` | `whale-platform/src/api/auth.ts` 为本地 mock（admin/123456 + 假 JWT） | 改用 `@bluewhale/http` 真实请求，落盘 access/refresh token |
| L2 | 当前用户与权限码 `GET /auth/me` | 无 | 登录后拉取 `userId/nickname/roles/permissions/menus`，供子应用共享 |
| L3 | 登出 `POST /auth/logout` | 仅清本地 | 携带 refreshToken 调后端（access/refresh 双写黑名单） |
| L4 | 401 单飞续期 | 无 | 复用 `@bluewhale/http` 的 `refreshSession` 链 |
| L5 | 基座开发态 proxy | `vite.config.ts` 无 proxy | 全网关一级前缀转发 8080；HTML 文档导航旁路，避免与 qiankun `/erp`、`/oa`、`/mes`、`/pdm` 路由冲突 |
| L6 | 登录态存储隔离 | 键名 `whale_token` | 统一前缀 `whale_platform_`，避免与 admin/门户串号 |

### 1.2 安全 / 工具类补齐

| # | 需求 | 现状 | 目标 |
|---|---|---|---|
| S1 | `StringUtils` 常用判定 | 空类（仅 TODO） | 空白/空串判定、默认值、trimToNull |
| S2 | `DateUtils` 日期时间 | 空类（仅 TODO） | 格式化/解析/今天/起止时刻 |
| S3 | `JwtUtils` JWT 工具 | 空类（仅 TODO 注释） | HS256 生成/解析/校验/续签，弱密钥拒绝 |
| S4 | `AesCrypto` AES | 空类 | AES-256-GCM，随机 IV 前置，Base64 |
| S5 | `RsaCrypto` RSA | 空类 | 2048 密钥对、OAEP-SHA256 加解密、SHA256withRSA 签名验签、Base64 密钥序列化 |
| S6 | `Sm4Crypto` 国密 SM4 | 空类 | 纯 JDK 实现 SM4-CBC/PKCS7，随机 IV 前置，Hex 密钥 |

> 说明：`JwtUtils` / `AesCrypto` 等当前**无业务调用点**（实测仅自引用），补齐后作为公共能力底座，不改变既有 `whale-auth` 的 `JwtTokenProvider` 链路。

---

## 二、设计要点

### 2.1 前端（whale-platform）

新增/改造文件：

| 文件 | 动作 | 内容 |
|---|---|---|
| `src/api/token.ts` | 新增 | 键前缀 `whale_platform_`；`LoginResponse` / `AuthMeResponse` / `UserInfo`；`getToken/getRefreshToken/getCurrentUser/saveLogin/saveCurrentUser/clearAuth` + `useCurrentUser` |
| `src/api/client.ts` | 新增 | `createHttpClient<LoginResponse>`，注入 token/refresh/clearAuth，导出 `get/post/put/del/refreshSession` |
| `src/api/auth.ts` | 重写 | `login` / `fetchMe` / `logout`，复用 admin 同构实现；删除 mock 与假 JWT |
| `src/router/guards.tsx` | 保持 | 仍从 `api/auth` 取 `getToken`（由 token.ts 透传） |
| `src/pages/login/index.tsx` | 改造 | 登录成功后 `fetchMe()`（失败不阻断），提示文案改为真实接口 |
| `src/layouts/MainLayout.tsx` | 改造 | `useCurrentUser()` 响应式昵称；`await logout()` 后跳转 |
| `vite.config.ts` | 改造 | proxy 全前缀 → `http://127.0.0.1:8080`；`Accept: text/html` 的 GET 导航旁路 |
| `package.json` | 改造 | 增加 `@bluewhale/http` 依赖 |

proxy 前缀：`/auth /user /dept /role /menu /permission /erp /mes /pdm /oa /workflow /form /file /alert /monitor /audit /agent /job /pay /config`（`/i18n` 随 Wave 5.50 服务删除不再代理）。

### 2.2 后端（whale-tools / whale-security）

- `StringUtils`：委托 hutool `StrUtil`，避免自实现边界差异。
- `DateUtils`：`DateTimeFormatter` 缓存默认两种格式；自定义格式按需构造。
- `JwtUtils`：jjwt 0.12.5 新 API（`Jwts.parser().verifyWith(...)`）；密钥 UTF-8 字节 ≥32，`Keys.hmacShaKeyFor`。
- `AesCrypto`：`AES/GCM/NoPadding`，12 字节随机 IV + 128 位 tag，输出 `IV||CipherText` 后 Base64。
- `RsaCrypto`：`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`；签名 `SHA256withRSA`；密钥用 X.509 / PKCS#8 DER + Base64。
- `Sm4Crypto`：**不引入 BouncyCastle**（本机离线仓库无 `bcprov`），以纯 JDK 实现 SM4 轮函数 + CBC + PKCS7，S 盒以十六进制串在静态块构建，CK 按标准公式生成；单测用 GB/T 32907 标准向量校验单块加密。

---

## 三、任务清单

- [x] T1 文档：本方案 + 任务清单入库
- [x] T2 `whale-tools`：`StringUtils` / `DateUtils` 实现 + 单测，pom 补 `junit-jupiter`（12 用例全绿）
- [x] T3 `whale-security`：`JwtUtils` 实现 + 单测（8 用例全绿）
- [x] T4 `whale-security`：`AesCrypto` / `RsaCrypto` 实现 + 单测（13 用例全绿）
- [x] T5 `whale-security`：`Sm4Crypto` 纯 JDK 实现 + 标准向量单测（7 用例全绿）
- [x] T6 基座 `token.ts` / `client.ts` / `auth.ts` 改造
- [x] T7 基座登录页 / 守卫 / 主布局适配 + `@bluewhale/http` 依赖
- [x] T8 基座 `vite.config.ts` proxy 补齐（含 HTML 导航旁路）
- [x] T9 后端定向构建：`mvn -o -pl whale-common/whale-tools,whale-common/whale-security -am test` + Checkstyle 0 违规
- [x] T10 前端构建：`pnpm --filter @bluewhale/whale-platform build` + `lint` 均通过
- [x] T11 devlog + 检查报告 + 提交推送 Gitee origin/main
  - [devlog/2026-10-24.md](../devlog/2026-10-24.md)、[reviews/2026-10-24-wave5.51-platform-login-and-utils.md](../reviews/2026-10-24-wave5.51-platform-login-and-utils.md)

---

## 四、验收口径

| # | 验收项 | 口径 | 结论 |
|---|---|---|---|
| 1 | 登录真实化 | 基座登录不再存在 mock 账号/假 JWT 代码；`grep MOCK_ACCOUNT` 为 0 | 通过 |
| 2 | 权限共享 | `/auth/me` 结果落 `localStorage.whale_platform_user`（含 `permissions`/`menus`） | 通过 |
| 3 | 登出闭环 | 先调 `POST /auth/logout`（带 refreshToken），异常不阻断本地清态 | 通过 |
| 4 | proxy | `Accept: text/html` 的 `/erp`、`/oa`、`/mes`、`/pdm` 导航不被代理；JSON API 请求代理到 8080 | 通过 |
| 5 | 后端单测 | 新增工具类用例全绿，存量用例 0 回归 | 通过（12 + 34 用例，0 失败） |
| 6 | 规范 | Checkstyle 0 违规（Javadoc/`@author`/行宽/MagicNumber 常量抽取） | 通过（0 violations） |
| 7 | 前端 | `tsc -b && vite build` 通过，ESLint 0 error | 通过（EXIT=0） |
| 8 | E2E | 基座登录 → `/auth/me` → 登出全链路 | **未执行**（需 MySQL/Redis/网关/auth 起服） |

## 五、待做项（本波不做）

- 基座 `PlaceholderPage` 三页收口（轨道 G3）。
- 门户 Dashboard / 通知 / 待办占位收口（轨道 G4/G5）。
- 基座 `/mes/*`、`/pdm/*` 路由挂载点。
- `JwtUtils` 与 `whale-auth` `JwtTokenProvider` 的收敛（待权限中心 JWT 策略统一后评估）。
- 基座登录 E2E（需 MySQL/Redis/网关/auth 全链路起服）。
- 全后端 reactor 全量 `mvn -o test`：本机离线仓库缺 `spring-boot-starter-amqp:4.0.8`，`whale-spring-boot-starter` 解析失败（环境既有问题，与本次改动无关）。

## 六、已修正问题项

- 基座 `api/auth.ts` 的假 JWT 生成逻辑（`createFakeJwt`）属**伪认证**，本次删除。
- 基座登录态键名与 admin 冲突风险，改为 `whale_platform_` 前缀隔离。
- proxy 若按一级前缀直连 8080，会吞掉 qiankun 子应用同名的浏览器路由（`/erp`、`/oa` 等），改用 `Accept` 旁路规避。
- `Sm4Crypto` 静态字段顺序导致 `SBOX` 构建时 `HEX` 为 null（NPE），已调整声明顺序。
- `JwtUtils` 弱密钥用例与 `validateToken` 吞异常语义不一致，已修正用例断言。
- 基座首次构建缺 `useCurrentUser` 导出，已在 `auth.ts` 补齐 re-export。
