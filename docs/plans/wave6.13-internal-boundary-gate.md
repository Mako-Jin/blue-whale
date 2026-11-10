# Wave 6.13 · 内网端点边界静态门禁

> 状态：**已交付**（2026-11-11）。产出 `scripts/check-internal-boundary.py` + CI 作业
> `internal-boundary`，并修掉目录重命名后残留的 3 处 CI 工作目录与 2 份 Gitee 流水线。

## 一、为什么做这一波

Wave 6.12 联调时发现：`DeptInternalController` 的类注释写着「已列入网关 internal-deny、
仅限内网可信调用」，但 auth 的 `SecurityConfig.permitAll` 里没有 `/dept/internal/**`，
Spring Security 在令牌过滤器之前就按「未认证」返回 401 —— **接口写了，但从来没通过**。
OA 的 `DirectoryGatewayImpl`（审批人/部门解析）走的是同一个端点，等于一直没打通。

这类缺陷的共同特征：

- 编译通过、单测全绿、Checkstyle 干净；
- 只有真实跨服务调用才暴露；
- 调用方通常把异常折算成空列表（twin 的选人组件就是这样），**连报错都没有**。

三层访问控制各管一段，任何一层漏配都只在运行期显现，靠读代码发现不了，所以做成门禁。

## 二、三层模型（门禁的判定依据）

| 层 | 实现 | 模式 | 漏配的症状 |
|---|---|---|---|
| 网关 | `InternalApiDenyGlobalFilter` | `/*/internal/**`（**星号不跨段**）+ `/audit/log/ingest/**` | 内网端点可从外部经网关直达，只剩令牌层 |
| 服务 | `InternalTokenFilter`（starter，最高优先级） | `/**/internal/**`（**跨段**）+ 服务内 `path-patterns` 覆盖 | 直连端口即可调用，dev 环境令牌为空时 fail-open |
| auth | `SecurityConfig.permitAll` | 显式列 `/tenant/internal/**`、`/user/internal/**`、`/dept/internal/**` 等 | 401，令牌层根本轮不到 |

> 注意两条 `**` 语义不同：网关的单段星号是为了「按约定命名即自动纳管」，
> 而它**拦不住** `/twin/notify/internal/x` 这种多段命名；令牌层跨段，
> 所以深路径实际上只有一层防护。门禁因此把深度 > 1 的内网端点判为 FAIL。

## 三、门禁检查项

`python scripts/check-internal-boundary.py`（退出码 0 通过 / 1 阻断）：

1. **网关收口**：内网端点的 `internal` 段前必须恰好一段（或为根级 `/internal/**`），
   否则外部可绕过网关直取（依赖 `/*/internal/**` 不跨段的事实）。
2. **令牌覆盖**：端点必须命中所属模块的 `bluewhale.internal-token.path-patterns`
   （未声明则取 starter 默认 `/**/internal/**`）；命中 `exclude-patterns` 记 WARN，
   提示该端点只能靠自带凭据（如 Alertmanager 的 webhook token）。
3. **auth 白名单**：auth 模块的内网端点必须命中 `SecurityConfig` 的 `permitAll`，
   否则就是 2026-11-10 那个 401 漏洞的重演。
4. **权限注解冲突**：内网控制器不得标 `@RequirePermission` —— 服务间调用没有用户权限码，
   标了必然 403（`MailInternalController` 的类注释专门写了「刻意不加」，门禁据此校验）。
5. **CI 工作目录**：`.github/workflows/*.yml` 与 `.workflow/*.yml` 里
   `working-directory:` 指向的目录必须存在（目录重命名后残留旧路径 → 作业在 checkout 后直接失败）。

实现要点：

- **剥注释**再扫描。`MailInternalController` 的注释里写着「刻意不加 `@RequirePermission`」，
  直接 grep 会把它当成真的用了注解（第一次跑就误报了）。
- **只认 `value` / `path`**：`@RequestMapping(value = "/x", produces = "application/json")`
  里的 `produces` 不是路径，否则会凭空造出 `/application/json` 假端点。
- Ant 语义自实现（`*` 单段、`**` 多段），不引第三方依赖；`/**/x` 能匹配 `/x`（零段）。

## 四、当前基线

```
扫描内网端点 118 条，涉及 24 个前缀：alert(2)、archive(3)、contract(5)、crm(14)、dept(2)、
doc(4)、erp(14)、file(6)、form(1)、im(1)、internal(3)、iot(4)、job(4)、mail(1)、mall(4)、
mes(5)、message(2)、oa(1)、pay(5)、pdm(5)、tenant(1)、twin(1)、user(4)、workflow(26)
WARN /alert/internal/prometheus 被 internal-token.exclude-patterns 豁免，只能靠自带凭据鉴权
RESULT: PASS
```

`/alert/internal/prometheus` 的 WARN 是设计意图（Alertmanager 入站带自己的 webhook token，
配置在 `InternalTokenProperties.excludePatterns`），保留为告警而非阻断。

**自检**（把 `/dept/internal/**` 从白名单删掉后重跑，确认门禁真的会响）：

```
FAIL auth 白名单缺 /dept/internal/tree：Spring Security 会先返回 401，令牌层根本轮不到
FAIL auth 白名单缺 /dept/internal/{id}/leader：…
RESULT: FAIL (5 项阻断)
```

## 五、顺带修掉的 CI 残留（目录重命名的尾巴）

2026-11-10 把 `backend/` → `whale-backend/`、`frontend/` → `whale-frontend/`，但 CI 里还有旧路径：

| 文件 | 问题 | 后果 |
|---|---|---|
| `.github/workflows/code-quality.yml` | `working-directory: frontend` | ESLint / Prettier 作业 checkout 后找不到目录，直接失败 |
| `.github/workflows/security-scan.yml` | `working-directory: frontend`（pnpm audit） | 同上 |
| `.github/workflows/security-scan.yml` | `working-directory: backend`（OWASP） | 同上；命令里又写了 `-f whale-backend/pom.xml`，即使目录存在也会指向 `backend/whale-backend/pom.xml` |
| `.workflow/backend-ci.yml` | 4 条 `mvn` 无 `-f`、产物 `./backend/**` | 根聚合 POM 已删，Gitee 侧从根执行必失败 |
| `.workflow/frontend-ci.yml` | `cd frontend` × 5、产物 `./frontend/**/dist/` | 第一步 `cd` 就失败 |

处理：前两处改指 `whale-frontend`；OWASP 作业去掉 `working-directory`（统一从仓库根执行，
靠 `-f whale-backend/pom.xml` 指定入口，报告路径正好是 `whale-backend/target/...`）；
Gitee 两份流水线同步改路径并补 `-f`。第 5 项检查保证以后不会再漏。

## 六、接入方式

- CI：`.github/workflows/code-quality.yml` 新增作业 `internal-boundary`（`python3 scripts/check-internal-boundary.py`），
  触发路径补 `scripts/**`、`.github/workflows/**`、`.workflow/**`。
- 本地：`python scripts/check-internal-boundary.py`（`--verbose` 逐条打印端点）。

## 七、遗留

- **深路径命名**：门禁目前把 `internal` 段前多段判为 FAIL。若将来确有需要，应扩展网关
  `internal-deny.patterns`（加显式模式）而不是放宽门禁——多段路径在网关侧目前只有令牌一层。
- **运行时取证**：门禁是静态口径核对，不能替代 `scripts/smoke/bw-network-boundary-check.ps1`
  的部署侧取证（端口不可达 + 伪造头不通过），上线前两者都要跑。
- **auth 白名单仍是显式清单**：可以改成「路径任意段含 `internal` 即 permitAll」的自定义
  `RequestMatcher`，彻底消除漏配可能；当前保留显式清单是因为可读性更好，且门禁已能兜住。
