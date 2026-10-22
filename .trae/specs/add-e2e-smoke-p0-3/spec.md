# Spec：P0-3 E2E 冒烟（第一批 2 条）

> 来源：[docs/plans/2026-10-22-platform-remediation.md](../../../docs/plans/2026-10-22-platform-remediation.md) P0-3
> 范围：第一批两条用例（登录+菜单权限、表单闭环）。工作流闭环为第二批，本 spec 不含。

## 1. 目标与验收

### 目标
1. 新建独立 workspace 包 `frontend/e2e`（Playwright + 自带 `webServer`）。
2. 用例 A：未登录访问受保护页 → 跳登录 → `admin/123456` 登录 → 侧边菜单与 `/auth/me` 返回的权限菜单一致。
3. 用例 B：建表（含加列）→ 发布 → 填报 → 数据列表可见并可查看详情。
4. 本地一条命令 `pnpm e2e` 可复现；失败产出 trace/截图。

### 验收
- `pnpm e2e`（前端根目录）2 条用例一次通过。
- 故意改坏权限判断（临时在 `AdminLayout` 侧边栏过滤掉 `知识助手`）→ 用例 A 失败；恢复后通过。
- 新包通过 `lint` 与 `format:check`。

## 2. 前提与运行环境（不新增 CI 流水线）

### 依赖服务（必须已启动）
| 服务 | 端口 | 说明 |
|---|---|---|
| MySQL | 3306 | `bluewhale_auth` / `bluewhale_form` 已初始化 |
| Redis | 6379 | auth 令牌黑名单/刷新 |
| Nacos | 8848 | 服务发现（认证开启） |
| whale-gateway | 8080 | 统一入口 |
| whale-auth | 8081 | 登录、`/auth/me` |
| whale-form | 8084 | 表单定义/数据 |

前端 `whale-admin`（8001）与 `whale-form-designer`（8002）由 Playwright `webServer` 自动拉起。

### 启动 whale-form 的本机命令（凭据不入仓）
```powershell
$env:JAVA_HOME='D:\tools\installer\jdk\java21'
$env:MYSQL_PASSWORD=...; $env:SPRING_DATA_REDIS_PASSWORD=...
$env:SPRING_CLOUD_NACOS_USERNAME=bluewhale; $env:SPRING_CLOUD_NACOS_PASSWORD=...
& 'D:\tools\installer\jdk\java21\bin\java.exe' -jar backend\whale-services\whale-form\target\whale-form.jar
```

### 环境变量（可覆盖，均有默认值）
- `E2E_ADMIN_BASE_URL` = `http://localhost:8001`
- `E2E_FORM_BASE_URL` = `http://localhost:8002`
- `E2E_API_BASE_URL` = `http://localhost:8080`
- `E2E_ADMIN_USERNAME` = `admin`，`E2E_ADMIN_PASSWORD` = `123456`

### CI 决策
P0-3 步骤 4 允许「Gitee Go **或** 本地 `pnpm e2e`」。GitHub Actions 当前无 MySQL/Redis/Nacos 全栈 job，第一批**不新增 workflow**；等全栈 CI job 就绪后在第二批一并挂载。此决策记录在 devlog 与整改清单。

## 3. 文件级改动

### 3.1 `frontend/pnpm-workspace.yaml`
新增 `- 'e2e'`。

### 3.2 `frontend/package.json`
新增脚本：`"e2e": "pnpm --filter @bluewhale/e2e test"`。

### 3.3 `frontend/e2e/package.json`（新建）
- `name: "@bluewhale/e2e"`, `private: true`, `type: "module"`
- scripts：`test: "playwright test"`、`test:ui: "playwright test --ui"`、`report: "playwright show-report"`、`lint: "eslint tests playwright.config.ts --max-warnings 0"`
- devDependencies：`@playwright/test`（安装时取当前稳定版，写入 lockfile）

### 3.4 `frontend/e2e/playwright.config.ts`（新建）
- `testDir: './tests'`；`globalSetup: './tests/support/global-setup.ts'`
- `timeout: 90_000`、`expect.timeout: 10_000`、`workers: 1`（避免表单用例并发写库）
- `retries: process.env.CI ? 1 : 0`
- `reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]]`
- `use: { trace: 'retain-on-failure', screenshot: 'only-on-failure', video: 'off' }`
- `webServer` 数组（`cwd` 为 `frontend` 根，`reuseExistingServer: true`，`timeout: 120_000`）：
  1. `pnpm --filter whale-admin dev -- --port 8001 --strictPort`，url `http://localhost:8001/login`
  2. `pnpm --filter whale-form-designer dev -- --port 8002 --strictPort`，url `http://localhost:8002/login`

### 3.5 `frontend/e2e/tests/support/api.ts`（新建）
薄封装，仅测试用：
- `login(username, password)` → `POST ${API}/auth/login`，断言 `code===0`，返回 `accessToken`
- `fetchMe(token)` → `GET ${API}/auth/me`，返回 `data`（permissions / menus）
- `authed(token)` → 通用请求头 `{ Authorization: Bearer ... }`
- 表单接口：`pageTableDefinitions(token, code)`、`tableDefinitionDetail(token, id)`、`createFormDefinition(token, payload)`、`pageFormData(token, definitionCode)`
- 统一断言：HTTP 200 且 `code===0`，否则抛出含 `message` 的错误

### 3.6 `frontend/e2e/tests/support/global-setup.ts`（新建）
快速失败，给出可操作提示：
1. `POST /auth/login`（admin）必须 `code===0`，否则抛「网关 8080 / whale-auth 8081 未就绪」
2. `GET /auth/me` 必须返回非空 `menus`
3. `GET /form/table-definition/page?current=1&size=1` 必须 `code===0`，否则抛「whale-form 8084 未就绪」

### 3.7 `frontend/e2e/tests/login-menu.spec.ts`（新建）
用例 A：`未登录跳登录 → 登录 → 菜单按权限渲染`
1. `page.goto(ADMIN + '/users')` → 断言 URL 以 `/login` 结尾，标题文本 `BlueWhale Admin` 可见。
2. 填写用户名 `admin`、密码 `123456`（placeholder 前缀匹配），点击「登录」。
3. 断言 URL 为 `/`；`PermissionGate` 完成后侧边栏出现动态菜单。
4. 通过 API 登录并 `GET /auth/me`，取顶层 `menus.map(name)`（按 `sort`）。
5. 断言 `.ant-layout-sider .ant-menu-root > li` 的顶层标题文本集合 **等于** 期望集合（admin 为 `系统管理`、`知识助手`）。此项用于捕获权限过滤被改坏。
6. 断言 admin `permissions` 含 `sys:agent:chat`，且侧边栏存在 `知识助手`。
7. 展开 `系统管理` → 点击 `用户管理` → 断言 URL 含 `/system/user`，`.ant-table` 可见。

### 3.8 `frontend/e2e/tests/form-loop.spec.ts`（新建）
用例 B：`建表 → 加列 → 发布 → 填单 → 列表可见`
唯一后缀：`suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 6)`；`tableCode = 'e2e_' + suffix`（满足 `^[a-z][a-z0-9_]{0,39}$`）。
1. UI 登录 `FORM_BASE/login`（admin/123456），断言进入 `/tables`。
2. 点击「新建表定义」→ 填 `表编码`=tableCode、`表名称`=`E2E冒烟表{suffix}`。
3. 点击「新增列」→ 填 `列编码`=`e2e_name`、`列名称`=`姓名`（默认 `text`）。
4. 点击弹窗「保存」→ 断言提示 `创建成功` 且列表行含 tableCode。
5. 该行点击「发布」→ 断言提示 `发布成功，物理表结构已同步`。
6. API：`GET /form/table-definition/page?code=...` 取 `id` → `GET /form/table-definition/{id}` 取 `columns[0].id`。
7. API：`POST /form/definition`
```json
{
  "code": "<tableCode>_form",
  "name": "E2E冒烟表单",
  "tableDefinitionId": "<tableId>",
  "version": 1,
  "status": 1,
  "fields": [{
    "columnMetaId": "<columnId>",
    "fieldLabel": "姓名",
    "widgetType": "text",
    "showInList": 1,
    "showInForm": 1,
    "editable": 1,
    "sortNo": 0,
    "colSpan": 24,
    "status": 1
  }]
}
```
  > 说明：字段配置绕开 @dnd-kit 拖拽（合成 PointerEvent 不可自动化，Wave 5.29/5.30/5.32 既有结论）；保存逻辑与设计器 `persist()` 一致，均为「tableDefinitionId + fields」元数据模型。
8. UI：`FORM_BASE/render/{definitionId}` → 填写 `姓名` = `测试用户{suffix}` → 点击「提交」→ 断言提示 `提交成功`。
9. UI：`FORM_BASE/data?definitionCode={formCode}` → 断言首行含 `formCode`；点击「详情」→ 抽屉中出现 `姓名` 与提交值。
10. API 复核：`GET /form/data/page?definitionCode=...` → `total >= 1`。
11. `afterAll` 尽力清理（逐项 try/catch，不因清理失败判用例失败）：删除数据 → 表单定义 → 表定义（若后端因引用拒绝则忽略）。

### 3.9 `.gitignore`
追加 `frontend/e2e/test-results/`、`frontend/e2e/playwright-report/`、`frontend/e2e/.playwright/`。

## 4. 文档与收尾
- `docs/devlog/2026-10-22.md` 追加「平台评审 P0-3：E2E 冒烟第一批」章节：范围、文件、验证命令与结果、拖拽限制说明、CI 暂缓原因。
- `docs/plans/2026-10-22-platform-remediation.md` P0-3 标记第一批完成（步骤 1/2/4 完成，步骤 3 第二批/CI 挂载待办）。
- 提交并 `git push origin main`（Gitee）；GitHub `ci` remote 视网络补推。

## 5. 明确不做
- 不新建 GitHub Actions E2E workflow（全栈 CI job 未就绪）。
- 不做工作流闭环用例（第二批）。
- 不改动 `@dnd-kit` 拖拽方案、不改后端接口。
- 不在仓库保存任何数据库/中间件凭据。
