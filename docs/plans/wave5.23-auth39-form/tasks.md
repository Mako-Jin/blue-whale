# Wave 5.23 任务分解（并行执行）

> 规格：[spec.md](spec.md)。执行原则：**先冻结契约（spec §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归**。
> 文件所有权：各轨仅改自己模块与已授权文件；跨轨文件冲突为零（T1=frontend-admin+whale-auth / T2=whale-form+whale-auth db 种子）。

## 并行轨与任务

| 编号 | 任务 | 落点模块 | 依赖 | 产物 | 自检 |
|---|---|---|---|---|---|
| **T1-a** | 刷新 API：`api/auth.ts` 增 `refresh()`（`POST /auth/refresh`，成功 `saveLogin` 替换双 token）；`api/http.ts` 增 401 → 单飞刷新 → 重放原请求一次（刷新/登录接口自身 401 不递归；失败则 `clearAuth` 抛 401） | `frontend/admin` | 无 | 2 文件 | `pnpm --filter whale-admin build` |
| **T1-b** | AUTH-39 轮换运行时取证（串行，收尾段）：`whale.auth.refresh.rotation.enabled=true` 起 auth → 登录 → refresh 换新 → 复用旧 refreshToken → HTTP 401 + 文案命中 + Redis `whale:auth:refresh:family:*` 落键 | `whale-auth`、运行时 | T1-a | 冒烟证据 | 运行时 |
| **T2-a** | whale-form 骨架接线：`application.yml` 补 datasource(`bluewhale_form`)/mybatis-plus/`whale.permission.header.enabled=true`；`db/init.sql`（`form_definition` + 建库注释） | `whale-services/whale-form` | 无 | 2 文件 | `mvn -o -pl ... test` |
| **T2-b** | whale-form 业务：`FormDefinition`(继承 `BaseEntity`) + `FormDefinitionMapper` + `FormQueryRequest`(继承 `PageQuery`) + `FormDefinitionVO` + `FormDefinitionService(+Impl)` + `FormController`（5 接口 + `@RequirePermission`） | 同上 | T2-a | 7 文件 | 同上 |
| **T2-c** | 权限种子：whale-auth `db/init.sql` 追加 `sys:form:add/edit/delete/list`（id 36–39） | `whale-services/whale-auth` | 无 | 1 文件（追加） | 人工核对 |
| **T2-d** | 单测：`FormDefinitionServiceImplTest`（新增/更新/删除/详情/分页 + code 查重 + schema 超限），standalone 风格 | `whale-services/whale-form` | T2-b | 1 测试类 | `mvn -o -pl ... test` + `checkstyle:check` |
| **T2-e** | 建库与种子落库（串行，收尾段）：建 `bluewhale_form` + 执行 `form_definition` DDL；`bluewhale_auth.sys_permission` 插入 id 36–39；起 8084 经网关 `/form/**` 冒烟 | 运行时 | T2-b/T2-c | 冒烟证据 | 运行时 |
| **E** | 收尾（串行，主执行）：全量 `mvn clean install -T 1C` → 检查报告 `docs/reviews/2026-10-19-wave5.23-auth39-form.md` → devlog 追加 → roadmap/task-breakdown/whale-auth-backlog 勾选 → commit + push origin | 全仓 | T1–T2 | 报告 + 日志 + 提交 | 全量 SUCCESS + 冒烟证据 |

> **T1-a 单文件域**（`frontend/admin/src/api/`）。
> **T2-a~T2-d 同属 `whale-form` 模块**（由同一执行单元串行完成）。

## 执行波次

```
Wave 5.23-a（并行）：T1-a（admin 续期） ｜ T2-a~T2-d（whale-form 服务 + 种子）
Wave 5.23-b（串行/并行）：T1-b（轮换取证） ｜ T2-e（建库 + /form 冒烟）
Wave 5.23-c（串行）：E 收尾
```

## 完成定义（DoD）

- 各模块级 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS；前端 `pnpm --filter whale-admin build` 通过。
- 运行时证据：AUTH-39 轮换复用 401 + family 吊销；whale-form 经网关 `/form/**` 全链路 `code=0`。
- `docs/reviews/` 检查报告 + `docs/devlog/2026-10-19.md` 追加 + 台账勾选 + commit & push origin（Gitee）。
