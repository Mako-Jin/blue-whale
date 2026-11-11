# Wave 6.16 · 前端菜单/权限码映射静态门禁

> 状态：**已交付**（2026-11-11）。产出 `scripts/check-frontend-perm-mapping.py` +
> CI 作业 `frontend-perm-mapping`，并修掉 1 处真实权限码缺陷
> （`sys:oa:expense:category:add/delete`）。

## 一、为什么做这一波

前端的菜单与按钮级权限完全由后端数据驱动，但两侧没有编译期约束，错误全部是
**静默失败**：

1. **菜单组件契约**：admin 动态路由由 `/auth/me` 的菜单树驱动，
   `sys_menu.component` 字符串要在 `dynamicRoutes.tsx` 的 `COMPONENT_MAP`
   里按契约键（或路径末段）解析成页面组件。解析失败不报错，只渲染
   「页面未配置」404 占位——菜单配了但键写错，用户只会看到白页。
2. **按钮级权限码**：`AuthButton perm="sys:xxx"`、`hasPermission('sys:xxx')`
   判断的权限码必须真的被后端发放过（`sys_permission.perm_code` 种子、
   `sys_menu.permission_code`，或控制器 `@RequirePermission`），否则任何角色
   都无法持有它，按钮/入口**永远不渲染**，且没有任何报错。

本轮静态扫描实际抓到并修掉的缺陷：

- `whale-oa/src/pages/expense/category.tsx` 判断的
  `sys:oa:expense:category:add` / `sys:oa:expense:category:delete` 在后端
  **不存在**——`ExpenseCategoryController` 的新增/删除/编辑统一由
  `sys:oa:expense:category:edit` 保护。结果是：持有 edit 权限的用户也看不到
  「新增类别」「删除」按钮。已改为与后端口径一致（三个动作都判 `edit`）。

## 二、门禁检查项

`python scripts/check-frontend-perm-mapping.py`（退出码 0 通过 / 1 阻断）：

1. **检查 A（菜单落点）**：每条 `sys_menu` 种子（除 `DIR` 目录型）必须落到
   前端，两条合法通道：
   - `component` 可被 admin `COMPONENT_MAP` 解析（复刻 `resolveComponent`：
     契约键直连 → 剥 `/index` → 路径末段回退）；
   - `path` 首段命中基座 qiankun 注册表的 `activeRule`（子应用自消费
     component，如 `/crm`、`/mall`、`/party`）。
   两条都不命中 → FAIL（页面未配置占位 / 路由 404）。
2. **检查 B（权限码全集）**：前端权限判断上下文（`perm=`、`anyOf=`、
   `hasPermission(`、`hasAny(`、`has(`、`permission:`、`includes(`）里引用的
   权限码必须存在于后端种子全集（`sys_permission.perm_code` ∪
   `sys_menu.permission_code` ∪ `@RequirePermission` 字面量）。
3. **检查 C（死键，WARN）**：`COMPONENT_MAP` 中从未被任何菜单种子命中的
   契约键。

当前基线：**265 条菜单种子 / 32 个契约键 / 904 个后端权限码 / 646 个前端引用
权限码，FAIL 0，WARN 3**（见下）。

## 三、实现要点与坑

- **剥注释**再扫描（SQL 的 `--`、TS/Java 的 `//` `/* */`），字符串字面量保留：
  演示页注释里写着 `sys:demo:not-exist`，不剥会把注释里的示例当真引用。
- **SQL 两种插入风格**都要认：`init.sql` 的 `VALUES (...), (...)` 多行、
  迁移脚本的 `SELECT ... FROM DUAL WHERE NOT EXISTS` 单行。值切分必须
  **引号感知**（`sys_permission.path` 一列里塞了逗号分隔的多个端点路径）。
- **INSERT-SELECT 拷贝型语句要跳过**：按租户复制菜单的语句
  （`SELECT m.name, m.path ... FROM sys_menu m`）SELECT 列是别名表达式，
  不携带新的 component 事实。
- **目录型菜单（`menu_type='DIR'`）跳过检查 A**：动态路由只递归子节点，
  目录本身不生成路由，`component` 为空是正常形态。
- **权限码形态约束**：小写冒号分段（`PERM_CODE_RE`），且只在权限判断上下文
  里取字面量——否则 `errorKeys.has(...)` 这类 Set 操作会被误收。
- **演示刻意引用**走显式白名单（`INTENTIONAL_MISSING_CODES`）：
  `ComponentsDemo.tsx` 的 `sys:demo:not-exist` 就是用来演示
  「无权限按钮不渲染」的，删除会破坏教学语义。
- **已知基线**（`KNOWN_UNROUTED_MENUS`，只 WARN 不 FAIL）：
  菜单 #9「知识助手」（`/agent`，DIR）与 #10「知识问答」（`/agent/chat`）是
  Wave 5.47 遗留模块，`whale-agent` 子应用至今未注册进基座 qiankun
  （`activeRule` 集合里没有 `/agent`），菜单可见但路由 404。**待办**：
  要么把 whale-agent 挂进基座（补 `apps.ts` + `routes.tsx` + 静态菜单项），
  要么下线这两条菜单种子——需要产品决策，门禁先挂基线防止新增同类问题。

## 四、CI 接入

`.github/workflows/code-quality.yml` 新增作业 `frontend-perm-mapping`
（紧跟 `internal-boundary` 之后），触发路径与本工作流一致
（`whale-backend/**`、`whale-frontend/**`、`scripts/**` 等）。

## 五、验收记录

```
$ python scripts/check-frontend-perm-mapping.py
菜单种子 265 条 / 契约键 32 个 / 后端权限码 904 个 / 前端引用权限码 646 个
[WARN] 菜单 #10「知识问答」path=/agent/chat 未挂载到任何前端（已知基线…）×2
[WARN] 契约键 'components' 未被任何菜单种子 component 命中（疑似死代码）
通过 940 项，FAIL 0 项，WARN 3 项
```
