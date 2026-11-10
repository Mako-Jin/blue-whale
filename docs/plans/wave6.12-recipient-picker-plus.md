# Wave 6.12 —— 接收人选择器增强：分页 + 部门树筛选

> 承接 [Wave 6.11](wave6.11-alert-notify-plus.md) 遗留第 2 条。
> 6.11 把「手填用户 ID」换成了通讯录选人，但只拉一页 20 条、不能按部门缩范围，
> 一千人的租户里等于「只能选到前 20 个」。本波次补齐分页与部门树，并顺手修掉一个
> 让部门内网接口常年 401 的鉴权漏配。

## 一、范围与状态

| 编号 | 项 | 状态 |
|---|---|---|
| M0 | 分页：`total` 透出，前端提示「匹配 N 人」而不是「本页 N 条」 | ✅ 已落地并实机验证 |
| M1 | 部门树：`GET /twin/alert-notify/departments` 代理 auth，前端 TreeSelect 筛人 | ✅ 已落地并实机验证 |
| M2 | 选父部门含子部门（`includeChildren`），避免「选了生产部一个人都没有」 | ✅ 已落地并实机验证 |
| M3 | 修复：auth `/dept/internal/**` 漏进 `permitAll`，部门内网接口恒 401 | ✅ 已落地（影响 OA 目录网关） |

## 二、分页

- `GET /twin/alert-notify/recipients?keyword&deptId&current&size` 返回
  `PageResult{records,total,current,size}`（与平台其它分页同构）。
- 孪生侧 `size` 默认 20、上限 50（选择器只做增量搜索，不需要大页）。
- auth 未返回 `total` 时按本页条数兜底——否则前端会出现「共 0 人」却列出若干条。
- 前端提示：`当前：N 人 · 匹配 M 人，列表仅取前 20 条（继续输入关键字收窄）`。

## 三、部门树

- `GET /twin/alert-notify/departments` → `[{id,name,children}]`，来自 auth 内网
  `GET /dept/internal/tree`；只转发选人需要的三个字段（负责人、排序、时间戳不扩散）。
- 缺 `id` 的节点连同子树一起丢弃（选了也筛不出人）；空 `children` 归一为 `null`。
- 前端用 antd `TreeSelect`（可搜索、可清空），默认不展开，选中即按部门重拉第一页。
- 失败一律退化为空列表并记 WARN：部门树只是筛选辅助，拉不到仍可按关键字搜人。

## 四、选父部门含子部门

- auth `GET /user/internal/directory` 新增 `includeChildren`（默认 `false`，保持精确匹配语义）。
- `UserDirectoryAppService.deptScope`：按部门表 `parentId` 反复扫描直到不再新增
  （不依赖返回顺序与层级深度；数据成环时也只加入未见过的 ID，能收敛）。
- 指定的部门不存在（已删或不在本租户）时直接返回空页，**不**退化成「不过滤部门」；
  repository 侧对空集合用 `1 = 0` 而不是 `in ()`（后者是非法 SQL）。
- 孪生侧固定传 `includeChildren=true`。

## 五、顺带修复：`/dept/internal/**` 恒 401

`DeptInternalController` 的注释写着「已列入网关 internal-deny、仅限内网可信调用」，
但 `SecurityConfig` 的 `permitAll` 只列了 `/tenant/internal/**` 与 `/user/internal/**`，
漏了 `/dept/internal/**` → 服务侧 Spring Security 先按「未认证」拒绝，任何内网调用都是 401。

- 影响面：OA 的 `DirectoryGatewayImpl`（审批人/部门解析）与孪生的部门树都调这个接口，
  属于「写了但一直没真正打通」。
- 修复：`permitAll` 补 `/dept/internal/**`；外部仍由网关 `/*/internal/**` 403 拦住，
  服务侧仍由 `InternalTokenFilter`（默认 `/**/internal/**`）校验 `X-Internal-Token`，
  鉴权层级没有被削弱。

## 六、验证（2026-11-10 实机）

- 单测：twin **193/193**（客户端 4 → 10 例）、Checkstyle 0；auth `UserDirectoryAppServiceTest` **7/7**
  （新增「逐层展开 10→11→12」「部门不存在返回空页且不下推」两例），Checkstyle 0。
- 前端 `tsc -b` 与 `eslint --max-warnings 0` 均 0 问题。
- 容器：auth / twin 重建后均 `healthy`。
- 造数（验收后已还原）：`sys_dept` 插入 990001「AcceptDeptA」→ 990002「AcceptDeptB」，
  临时把 admin 挂到 990002。
  - `GET /twin/alert-notify/recipients` → `total=1`、`deptName=AcceptDeptB`
  - `?deptId=990001`（父）→ 仍返回 admin（含子部门生效）
  - `?deptId=990002`（子）→ 返回 admin
  - `?deptId=999999`（不存在）→ `records=[]`、`total=0`，无 500
  - `GET /twin/alert-notify/departments` → `[{990001,AcceptDeptA,children:[{990002,AcceptDeptB,children:null}]}]`
  - 无 `X-Permission-Codes` → `403 无权限`
  - 清理后 `sys_user.dept_id=NULL`、`sys_dept` 0 行，`bw-twin` 日志异常计数 0。

## 七、遗留

- 选择器仍是「搜一次看一页」，没做滚动加载更多（`size` 上限 50，关键字收窄是主要手段）。
- 部门树按全量一次性拉取，未按层级懒加载；部门数量过千时首次打开会慢，届时再改懒加载。
- 告警仍不支持按用户偏好（场景级配置），见 [Wave 6.11](wave6.11-alert-notify-plus.md) §七。
