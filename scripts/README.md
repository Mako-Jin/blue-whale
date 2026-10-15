# scripts — 开发与运维脚本

存放跨应用的辅助脚本：环境初始化、契约生成、数据库迁移、数据批处理等。

约定：**脚本必须可重复执行**，并且失败时要给出明确的错误信息，而不是静默跳过。

## 现有脚本

| 脚本                           | 用途                                                                  | 前置条件                                                                                                                         | 说明见                |
| ------------------------------ | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| `export-openapi.sh`            | 从运行中的应用导出 OpenAPI 契约到 `docs/api/openapi.json`。**CI 会重跑它并与入库版本逐字节比对**（`server` job）—— 接口改了却忘了导出会红 | 后端可构建且 JDK 21（脚本自查 `JAVA_HOME`）；临时以 18080 端口启动一次                                                           | `docs/decisions/0006` · `0042` |
| `init-dameng.sh`               | 在达梦 DM8 上应用交付用的建表脚本（Flyway 无达梦模块）                | 可达的 DM8 实例；凭据走环境变量 `BW_DB_USERNAME` / `BW_DB_PASSWORD`                                                              | `docs/decisions/0010` |
| `verify-micro-frontend.mjs`    | 用真实 Chrome 验证微前端全链路                                        | **后端 :8080（h2 profile，含种子账号）**与基座 :3000、以及要验证的子应用（:3200 / :3300 / :3400 / :3500 / :3600）都在跑；本机装有 Google Chrome | `docs/decisions/0012` |
| `verify-scene-switching.mjs`   | 用真实 Chrome 验证三维应用的多场景切换与显存释放                      | :3400 在跑，且本机装有 Google Chrome；可选传截图目录                                                                             | `docs/decisions/0014` |
| `verify-player-controls.mjs`   | 用真实 Chrome 验证古建筑场景的主人公操控                              | :3400 在跑，且本机装有 Google Chrome；可选传截图目录                                                                             | `docs/decisions/0015` |
| `verify-portal-auth.mjs`       | 用真实 Chrome + 真后端验证基座登录链路                                | 后端 :8080（h2 profile，含种子管理员）、基座 ：3000、`platform-log-console` :3200 都在跑；本机装有 Google Chrome；可选传截图目录 | `docs/decisions/0018` |
| `verify-admin-permissions.mjs` | 用真实 Chrome 验证按钮级权限（两种身份的界面差异 + 真实增删 + 组织树移动与主岗） | 后端 :8080（h2 profile，含两个种子账号）、基座 ：3000、`platform-admin` :3500 都在跑；本机装有 Google Chrome；可选传截图目录     | `docs/decisions/0018` · `0030` |
| `verify-atmosphere.mjs`        | 用真实浏览器验证大气（天穹、日出日落、云、时间流逝、动态 IBL 的显存） | :3400 在跑，且本机装有 Chromium 内核浏览器（Chrome 或 Edge，脚本**自动探测**）；可选传截图目录                                   | `docs/decisions/0017` |
| `verify-audit-pipeline.mjs`    | 用真实 Chrome + 真后端验证审计管道（记录自动产生、越权留痕、无写入口） | 后端 :8080（h2 profile，含两个种子账号）、基座 :3000、`platform-audit-console` :3600 都在跑；本机装有 Google Chrome；可选传截图目录 | `docs/decisions/0019` |
| `verify-nginx-config.mjs`      | 对 `infra/nginx/` 的网关配置做结构性自检；`--emit` 可渲染出部署树、`--nginx` 可顺带真跑 `nginx -t` | **无**（不带参数时不需要 nginx、不需要网络、不写文件）                                                                                     | `docs/decisions/0027` · `0028` |
| `verify-menu-permissions.mjs`  | 用真实 Chrome 验证菜单与页面级权限过滤（两种身份的导航差异 + URL 直达被拦 + 概览过滤） | 后端 :8080（h2 profile，含两个种子账号）、基座 :3000、`platform-admin` :3500、`platform-audit-console` :3600 都在跑；本机装有 Google Chrome；可选传截图目录 | `docs/decisions/0031` |
| `verify-docs-index.mjs`        | 对 `docs/README.md` §四 的文档索引做一致性自检：ADR 索引**双向**一致（有文件没登记 / 索引指向不存在的文件）、编号从 `0001` 连续、§四 里引用的其他文档都存在 | **无前置条件**（不连网、不读产物、不写文件）；`node scripts/verify-docs-index.mjs`      | `docs/decisions/0036` |
| `verify-api-contract.mjs`      | 对 `docs/api/README.md` §一 的接口清单做一致性自检：总览句的路径 / 接口 / 模型三个数、逐域明细表的每条 `方法 + 路径`（与契约**双向**比对）、按模块汇总表与分项之和、模块名是否真存在 | **无前置条件**（只读 `docs/api/openapi.json` 与 `docs/api/README.md`，不连网、不起服务、不写文件）；`node scripts/verify-api-contract.mjs` | `docs/decisions/0040` |
| `verify-bundle-secrets.mjs`    | 扫前端构建产物，确认**种子口令没有进包**                                | **无前置服务**（口令真值从 `application-h2.yml` 读；需先构建前端）；`node scripts/verify-bundle-secrets.mjs [目录…]`       | `docs/decisions/0029` · `0033` |
| `verify-result-codes.mjs`      | 两个错误码枚举里的每个码，要么在 `src/main` 里有抛出点，要么在脚本里明写着"有意不用 / 登记了没实现"的理由 | **无前置条件**（只读源码文本，不编译、不连网、不写文件）；`node scripts/verify-result-codes.mjs` | `docs/decisions/0060` |
| `verify-unique-columns.mjs`    | 服务层挡重名的每个 `*_EXISTS` 码都登记了它判的哪一列；每个判重列要么库上有 UNIQUE 索引兜底、要么在白名单写明"业务规则级唯一"的理由；顺带守三方言唯一索引集合同构 | **无前置条件**（只读源码文本与三方言迁移脚本，不编译、不连网、不写文件）；`node scripts/verify-unique-columns.mjs` | `docs/decisions/0063` |
| `verify-like-escaping.mjs`     | `server/` 的 `src/main/java` 里每一处 `LIKE` 都必须经过 `KeywordLike`（转义关键词、并把 `ESCAPE '!'` 写进 SQL），不许再有裸的 `like(...)`；带 `LIKE` 的片段同一行必须有 `ESCAPE`；白名单逐条核对条数 | **无前置条件**（只读源码文本，不编译、不连网、不写文件）；`node scripts/verify-like-escaping.mjs` | `docs/decisions/0067` |
| `verify-filter-values.mjs`     | `server/` 的 `src/main/java` 里**精确匹配**的过滤值与**要拿它去匹配的那些写入值**都在 `FilterValue` 上收口：`.eq(...)` 的条件里不许用 `hasText(...)` 或内联的 `query.getX().isBlank()`；三个手写聚合 SQL（`countByLevel` / `countByEventName` / `countByAction`）的实参不许是 `query.getXxx()`；**枚举列**的五个 getter（`getStatus` / `getResourceType` / `getMemberType` / `getLevel` / `getResult`）只能作为 `FilterValue.enumValue(...)` 的第一个实参出现；**上报侧**（`sts-server-log` / `sts-server-track`）写入实体时 `entity.set<App\|Namespace\|TraceId\|SessionId\|UserId\|DeviceId>(...)` 的实参必须含 `FilterValue`；**寻址列**（`sts-server-auth`）`entity.set<Email\|Phone\|ContactEmail\|ContactPhone>(...)` 与 `existsEmail(...)` / `existsPhone(...)` 的实参**含 `request.getXxx()` 且不含 `FilterValue` ⇒ 报红**（判重也是一次 `eq`，两侧必须共用一个归一值） | **无前置条件**（只读源码文本，不编译、不连网、不写文件）；`node scripts/verify-filter-values.mjs` | `docs/decisions/0069` · `docs/decisions/0070` · `docs/decisions/0071` · `docs/decisions/0072` |
| `verify-standalone-dev-login.mjs` | 用真实 Chrome 验证子应用**单跑**时的自建会话与开发账号自动登录（后端在跑 / 没起两种状态都跑一遍） | 至少一个子应用的 dev 服务在跑（:3200 / :3300 / :3500 / :3600，没起的会被记成"跳过"）；**后端 :8080 可选** —— 在跑时断言自动登录成功，没起时断言优雅降级；本机装有 Google Chrome | `docs/decisions/0018` · `0033` |

### `verify-micro-frontend.mjs` 做什么

它用 CDP（Chrome DevTools Protocol，`--headless=new`，调试端口 9333）驱动真实的 Chrome 走一遍：

0. **真实登录**（基座自 ADR-0018 起有登录守卫，不登录页面里既没有导航也没有入口 —— 这一步是 2026-10-12 补的，脚本在此之前一直失败）
1. 确认导航与首页入口都来自同一张注册表（导航 6 项，首页入口 5 张卡片，相差的 1 项是基座自己的「概览」）
2. 点导航挂载日志控制台 → 断言已挂载、`window.__MICRO_HOST__` 存在、右下角标记内容正确（含 `userId` / `roles` / `perm` → 会话经全局状态真的到了子应用）
3. **改源码**探测子应用热更新；**改样式**探测样式热更新（测完自动还原）
4. 点基座的「全局主题」按钮 → 断言子应用的 `theme=` 跟着变（全局状态下发）；并断言它拿到的身份非空
5. 在子应用里 `fetch('/api/health')` → 验证子应用经基座代理请求后端
6. 切换到事件控制台 → 切换到三维环境应用 → 断言画布存在、渲染统计产生，并**抓住 WebGL 上下文引用**
7. 返回首页 → 断言容器被清空、标记移除、`window.__MICRO_HOST__` 归 `undefined`，
   并且对之前抓住的上下文断言 `isContextLost() === true`（GPU 资源确实被主动释放，见 `docs/decisions/0013` 第 4 条）
8. 汇总浏览器侧错误与 4xx/5xx —— **浏览器侧报错会让脚本非零退出**（子应用没起时
   qiankun 会抛 `TypeError: Failed to fetch`，而每一步都还能"读出"一个 `undefined` 继续往下走，
   只打印不判定等于看不见）

> **为什么用真实浏览器而不是 jsdom。** 微前端的问题几乎都只在真实运行时才暴露：脚本的执行方式（`import-html-entry` 用 `eval` 执行脚本文本）、生命周期对接、容器挂载与卸载、样式隔离。这些在 jsdom 里都不会重现 —— 第一版实现时 Fast Refresh preamble 抛的 `SyntaxError` 就是只有真实浏览器才看得见的错误。

> **注意脚本里的 HMR 探测是"就地改源码"**：它会往 `platform-log-console/src/App.tsx` 与 `App.less` 里写入一段探针文本并在结束时还原。中途被中断时请检查这两个文件是否留有 `·HMR探测` 或 `--hmr-probe` 残留。

### `verify-player-controls.mjs` 做什么

用 CDP（Chrome DevTools Protocol，`--headless=new`，调试端口 9335）驱动真实 Chrome，验证的都是**单元测试测不到的接线**：

1. 等舞台与主人公就绪，断言首屏场景是古建筑、人物在出生点
2. 小地图确实画出来了：画布尺寸、指北标记是 `N`、人物箭头的像素确实存在
3. **真实键盘事件**按住 `W` 再松开 → 断言位移与"镜头前向"的一致度 ≥ 0.9
   （方向符号写反的经典症状"按上往后走"，就是被这条逮住的）
4. 按 `D` 同理，断言与"镜头右向"的一致度
5. 右键（合成 `contextmenu`）在若干候选屏幕点里挑一个**离人物足够远**的设为终点，
   等人物自己走过去：断言距离收敛、终点被清除、人物没停在障碍里
6. 滚轮缩放 → 断言相机距离变化
7. 切到太空场景 → 断言小地图隐藏、交互快照归 `null`
8. 汇总浏览器侧错误与 4xx/5xx

### `verify-portal-auth.mjs` 做什么

用 CDP（`--headless=new`，调试端口 9337）驱动真实 Chrome，访问的是**真后端**（h2 profile 的种子管理员），
验证的全是单测测不到的浏览器侧事实：

1. 未登录访问 `/log-console` → 地址栏真的变成 `/login`，且导航栏没有渲染
2. 登录表单的默认租户/用户名与种子数据一致、口令框是 `password` 类型
3. 用 `Input.insertText` 真实输入口令并提交 → 拿到真令牌，回到原定页面；
   断言 `localStorage` 持久化的是 `refreshToken` 而**不含** `accessToken`
4. 子应用右下角标记显示 `userId=1 · roles=1 · perm=27` —— 权限经全局状态真的下发到了子应用
5. 已登录再访问 `/login` 被送回首页
6. **硬刷新页面** → 仍在受保护页面且子应用重新收到身份（内存令牌已丢，靠刷新令牌续期）
7. 登出 → 弹回登录页、`localStorage` 清空、导航栏移除
8. 浏览器侧零报错

### `verify-admin-permissions.mjs` 做什么

用 CDP（`--headless=new`，调试端口 9338）驱动真实 Chrome，验证**按钮级权限**这条链路 ——
它验证的都是单测测不到的接线（单测里权限集合是直接塞进去的、请求是替身、子应用也没有真正被基座挂载）：

1. 未登录访问 `/admin` → 基座守卫弹到 `/login`（新子应用也被同一条守卫覆盖）
2. 以 `admin`（45 项权限 = 27 项读写 + 18 条菜单码）登录 → 子应用出现全部操作入口，页面上的权限集合与后端给的逐字一致
3. **真实写一遍**：新建一个带时间戳用户名的用户 → 出现在列表 → 删除 → 从列表消失（可重复执行）
4. 切到租户页 → 另一组权限标识（`auth:tenant:*`）各自独立判定
5. **组织页**：树按层级渲染 → 界面新建一层、接口再建一层造出两层子树 →
   **移动**（子树级改写：自身层级与路径、下级路径、**原父级 `hasChild` 归零**）→
   搬回原位后派生列与原值**逐字相等** → 挂成员（「是否主岗」保持缺省，服务端自动设为主岗）→
   把主岗设到别处（原主岗被一并降下、**主岗恰好 1 条**）→ 清理并按守卫顺序删树
6. 登出后以 `auditor`（仅 3 项读权限）登录 → **同一段代码**：列表照常显示，
   新建 / 编辑 / 重置口令 / 删除一个都不出现，且页面停在原处（403 不等于登录失效）
7. **审计员的组织面**：组织树读不到（报错指名缺少 `auth:org:read`、树上 0 个节点、0 个写入口），
   但用户页的「组织归属」「数据范围」照常可用 —— 验证 `auth:org:read` 与 `auth:user:read`
   这组**刻意的划分**（两者不互相蕴含）
8. **硬刷新** → 权限随会话一起恢复（内存令牌已丢，靠刷新令牌重新拿回身份与权限）
9. 登出 → `localStorage` 清空、子应用界面卸载
10. 浏览器侧零报错

> **这一步里有一条"脚本内置的 API 通道"**（第 5 步与第 7 步用）。原因是组织页有几件事
> **在界面上看不见**：典型的是"移动之后原父级的 `hasChild` 有没有跟着归零"——
> 树的组装用的是 `parentId`，所以就算 `hasChild` 是陈旧的，界面看起来也完全正常。
> 上一轮（后端）的用例正是靠这条断言抓出了一个真缺陷：移动时路径与层级改了、`parent_id`
> 没落库，于是"界面显示在原地、数据范围算在新位置"，两处都不报错。
> 因此这条通道**只用于读数与造场景**，不替代界面验证。
> 它用 `scripts/lib/seed-credentials.mjs` 里的管理员凭据登录 —— 脚本里不含任何口令字面量。

> 截图存到 `docs/assets/0018/`（`05-admin-can-write.png` / `06-auditor-read-only.png`），
> 与 `verify-portal-auth.mjs` 的 01–04 放在一起，正好构成"登录态 → 权限下发 → 权限生效"的完整证据链；
> 组织页与反查的截图是 `07-org-management.png` / `07-auditor-reverse-lookup.png`。

### `verify-audit-pipeline.mjs` 做什么

用 CDP（`--headless=new`，调试端口 9339）驱动真实 Chrome，访问**真后端**（h2 profile 的两个种子账号）。
审计这条管道最容易"看起来成了"（页面上有记录 ≠ 记录是这一次操作产生的），所以它验证的全是**因果**：

1. 未登录访问 `/audit` → 被基座守卫弹到 `/login`（新子应用同样被同一条守卫覆盖）
2. 以 `admin`（45 项权限）登录 → 列表里有已有记录、时间窗可见、按服务端时间倒序
3. **演练区真实写一次**：点按钮 → 真的 `POST /api/auth/tenants` 建一个租户 →
   **轮询等审计表出现 `auth.tenant.create`**（断言 `targetId` / `operatorName` / `durationMs` 对得上）
   → 再 `DELETE` 掉它 → 等到 `auth.tenant.delete`
4. 按动作聚合里出现该动作，计数随操作增加
5. 点聚合项 → 列表被筛成该动作（聚合与流水是同一份数据的两条读法）
6. **往审计表里写一条**：`POST /api/audit/logs` → 断言回来的是 `40500`（不是 404、不是 500）
7. 页面自己发出的审计请求**全是 `GET`**（前端没有任何写路径）
8. 换成 `auditor`（14 项权限 = 3 项读写 + 11 条菜单码）→ 列表照常可见；在演练区点"创建" → 被拒（403）
   → **回到列表断言这次越权尝试留了痕**（`FAILURE` + `errorCode`），而这正是演练区按钮
   刻意不按权限显隐的原因
9. 打开一条记录详情 → 断言有"记录时间"（审计只有服务端时间这一条，必须在界面上看得见）
10. **硬刷新** → 审计页仍在（会话靠刷新令牌恢复）
11. 汇总浏览器侧错误与 4xx/5xx

> 截图存到 `docs/assets/0019/`（01 管理员视角 → 07 记录详情），构成
> "**写接口被调用 → 审计记录出现 → 越权同样留痕 → 前端无写入路径**"的完整证据链。
>
> **为什么第 3 步必须"轮询等"而不是"断言立刻可见"**：审计写在拦截器的 `afterCompletion`，
> 与业务响应之间没有"先落库再返回"的强保证（ADR-0019 的后果一节）。断言立刻可见
> 会在快的机器上偶尔通过、在慢的机器上偶尔失败 —— 那是最难排查的一类测试。

### `verify-menu-permissions.mjs` 做什么

用 CDP（`--headless=new`，调试端口 9341）驱动真实 Chrome，访问**真后端**（h2 profile 的两个种子账号）。
菜单过滤这类改动最容易的做法是"看一眼导航栏少了没有"，但那验不出真正要防的两件事，所以它验的是：

1. 未登录访问 `/admin` → 被登录守卫弹到 `/login`
2. `admin` 登录 → 导航栏 **5 个入口**（日志 / 事件 / 三维 / 权限管理 / 审计），逐个点名比对
3. 进入 `/admin` → **6 个标签页都在**，且**顺序**与 `AuthMenus` 的声明顺序一致
4. 登出、换成 `auditor` → 导航栏**只剩 2 个**（权限管理 / 审计日志），并额外断言**看不到**「日志控制台」
5. **直达没有授权的路由**：`location.href = :3000/log-console` → 被送回 `/`，
   且概览页**说明缺的是哪一个菜单码**（`ui:log_console:view`）
6. 概览页列出的入口卡片**也只有两个** —— 否则首页会变成唯一的"假开关集合"
7. 审计员进 `/admin` → 仍是 6 个标签页（决策是"页面码全给"），
   但**看不到「新建用户」**：这是"菜单码决定进不进得去、读写权限决定进去能做什么"的现场证据
8. 审计员进 `/audit` → 流水 / 聚合 / 演练三块都在
9. 汇总浏览器侧错误（有错即非零退出）

> **第 5 步是这个脚本存在的核心理由**：只过滤导航栏的话，地址栏就是后门 ——
> 那正是"交付一个假开关"。它同时也是 `scripts/verify-portal-auth.mjs` 的补充：
> 那边验"谁能进来"，这边验"进来之后看得到什么"。

> **换账号的坑**：`Input.insertText` 是**追加**不是替换，换账号时会拼出 `adminauditor`。
> 也不可直接赋 `input.value` —— React 受控组件靠 value 的 setter 记录旧值，
> 直接赋值会让它认为"外部改了但没触发 onChange"，下一次渲染又改回去。
> 正解是取原型上的原生 setter 写值、再手动派发 `input` 事件（见脚本里的 `setInput`）。

### `verify-atmosphere.mjs` 做什么

用 CDP（Chrome DevTools Protocol，`--headless=new`，调试端口 9336）驱动真实浏览器验证大气系统。
它验证的都是**单元测试一条都测不到**的接线 —— 内核算得对，不等于画面上真的变了：

1. 拨时刻滑块到四个锚点（正午 / 黄金时刻 18:00 / 日落 19:30 / 午夜），
   逐点记录**画面上部天空区域的平均色**（CDP 截图 → 页面内解码 → 统计）
2. 断言天穹确实被画出来了：正午的天空偏蓝、亮度远高于那个写死的背景色 `#342f3d`、存在明暗结构（云）
3. 断言亮度随太阳高度角**单调下降**，且日落的地平线比正午更暖
4. 点 `×3600`，**先把窗口对齐到帧边界**再计时 → 断言实测速率 ≈ 3600×
   （把推进的模拟秒数除以真实秒数，而不是断言"2 秒推进 2 小时"），且控制面板的时钟跟着走
5. **反复拨 12 次时刻**（每次都会触发环境贴图重烘）→ 断言纹理数不持续增长
6. **逐个场景**检查气候域与天穹的对应：古建筑 / 丛林 / 写字楼三个室外场景都断言"白天天空亮得起来"，
   飞船（真空）反过来断言"不该有天光"；随后再切回古建筑一次 —— 验 `dispose()` → `create()`
   走完整整一圈之后天穹**还能重建**，且全程纹理数不累积
7. 汇总浏览器侧错误与 4xx/5xx

> **为什么必须用真实浏览器。** 这一层坏掉的方式全是"静态检查看不见"的：天穹没被画出来
> （画面还是那个背景色）、日出日落算对了但没接到渲染上、重烘漏了 `dispose`（显存缓慢泄漏，
> 两分钟内毫无症状）。这些在单元测试里全是绿的。第 5 条的多轮拨动是有意放大的 ——
> 那是这次改动里唯一"每次都会新建 GPU 资源"的地方。

> **⚠️ 等待必须按「帧」，不能按毫秒。** `setEnvironment()` 是同步改光和雾的，但画面要等
> **下一帧**才重绘。无头浏览器走的是 `--use-gl=swiftshader` 软件渲染，**实测单帧约 2.2 秒
> （≈0.5 fps）**，于是"改完 `sleep(650)` 再截图"会稳定截到上一帧，症状是四个时刻的天空
> 颜色几乎一样 —— 看起来像"天穹没生效"，其实是测量方法错了（本脚本第一版就栽在这里）。
>
> 所以全篇用 `waitForFrames(n)` 等待，判据是舞台自己数的 `diagnostics().frames`
> （只数真正画出画面的帧）。换到有 GPU 的机器上帧率上去了，脚本会自然变快。
> 同理，测速率时**窗口必须先对齐到帧边界**：不对齐的话，窗口起点落在某帧中途，
> 而那一帧的 `getDelta()` 是从上一帧算起的整段时长（软件渲染下有 2 秒），
> 其中一大半发生在点按钮**之前**，会把速率算高一大截。
>
> 反过来说：**不要**把 `advanceEnvironment` 的推进量理解成"按帧数推进" —— 它按的是
> `timer.getDelta()`（真实秒数 × 倍速），所以掉帧时速度快慢不变，只是跳跃变大。

### `verify-nginx-config.mjs` 做什么

对 `infra/nginx/` 的网关配置做**结构性自检**。不带参数时它是**唯一一个不需要任何前置服务**
的脚本 —— 不需要 nginx、不需要网络、不写任何文件。

**它不试图替代 `nginx -t`，补的是另一类错：**

| 这类错 | `nginx -t` | 本脚本 |
| --- | --- | --- |
| 花括号不配对、指令拼写错 | 能 | 不能 |
| `limit_req zone=xxx` 引用了**不存在的 zone**（拼错了名字） | **能**（`zero size shared memory zone`，实测） | ✅（更早、直指缺哪个） |
| 模板变量漏在 `.env.example` 之外（渲染后是空值） | ❌ | ✅ |
| envsubst 白名单漏了，把 nginx 自己的 `$request_method` 替换掉 | ❌ | ✅ |
| 上报路径没挂上限流 / `map $request_method` 丢了 | ❌ | ✅ |
| 上报路径漏挂了**某一层**桶（按 IP / 全局） | ❌ | ✅ |
| **全局桶的 key 被写成 `$binary_remote_addr`**（退化成第二个按 IP 桶） | ❌ | ✅ |
| 真实 IP 网段被误配成 `0.0.0.0/0` | ❌ | ✅ |

除第一行与第二行外，其余都是**只有本脚本能挡**的：它们都能让配置"通过 `nginx -t` 但行为是错的"。
反过来，它会说"通过"而 `nginx -t` 报语法错 —— **上线前两者都要跑**。

第二行曾是"只在启动时"（`0027` 的推断），**在 nginx 1.20.1 上实测被推翻** —— 见 `0028`。

**两个可选参数**（都不给时行为与以前完全一致，因此 CI 里照旧能跑）：

| 参数 | 做什么 |
| --- | --- |
| `--emit <目录>` | 把模板渲染成一棵**与部署形态同构**的树（`conf.d/`、`snippets/`、可 `-c` 的主配置），并打印 `nginx -t` 命令。**顺带绕开 `envsubst` 的白名单陷阱**（只替换已知的 `${变量}`），因此部署时也推荐用它 |
| `--nginx <可执行文件>` | 在渲染之后真跑一次 `nginx -t`（也可用环境变量 `BW_NGINX_BIN`）。⚠️ Node 的 `spawnSync` 在部分沙箱被禁止创建进程（报 `EBUSY`），那种环境走 `--emit` + shell 手跑，检查的是同一棵树 |

> **它用两个负例验证过不是空转**：把 zone 名改错 → 命中"每个被引用的 zone 都已定义"；
> 把 `set_real_ip_from` 改成 `0.0.0.0/0` → 命中"没有把可信网段写成 0.0.0.0/0"。
> 另有一个**真实 nginx 上的反例**：把某个 `zone=` 引用的名字改错后 `nginx -t` 报
> `[emerg] zero size shared memory zone` —— 证明 `--emit` 产出的树不是空壳。
>
> ⚠️ 它检查的上报路径清单是**写死的两行**（`/api/logs/client` 与 `/api/events/client`）——
> 给上报域新增路径时，脚本与 `bluewhale.conf.template` 都要同改，脚本不会自动发现。

### `verify-bundle-secrets.mjs` 做什么

`0029` 要求过"前端任何把开发凭据写出来的地方都要 `import.meta.env.DEV` 门控，
**且验证方式是构建后 grep 产物，不是读代码判断**"。那一次是**手工执行**的，
结果写进了文档 —— 手工执行的检查会随下一次改动失效而无人知道。

本脚本把那条要求变成可重复执行的：默认扫 `web/platform` 下各应用的 `dist`，
把两个种子口令在产物里找一遍；命中即列出目录并**非零退出**，全干净则退出码 `0`。

**它不写口令字面量**，口令从 `scripts/lib/seed-credentials.mjs` 取 ——
于是口令一旦在 `application-h2.yml` 里改掉，检查跟着改，**不会静默失效**（这是它值得存在的前提）。

**它只输出计数，不输出命中的口令串** —— 否则这个检查自己就把口令打进了日志。

它也**不是密钥扫描器**：只回答"已知的种子口令有没有被卷进前端产物"，
令牌泄露、源码里的第三方 key 都不在射程内。

> **第一次跑就修正了一条记录的结论。** `0029` 写着"4 个子应用的 dev 账号字符串仍在包里"，
> 实测只有 `platform-log-console` / `platform-event-console` 是真命中；
> `platform-admin` / `platform-audit-console` 干净 —— 它们的 `DEV_ACCOUNT` 唯一引用点
> 落在 `if (!import.meta.env.DEV) return;` 之后，被摇掉了。
> **"恰好没事"不是"设计成没事"**，四处写法因此统一（见 `docs/decisions/0033`）。

### `verify-standalone-dev-login.mjs` 做什么

**这是全仓唯一覆盖"子应用单跑"的脚本。** 其余端到端脚本都从基座（:3000）进去，
而"单独 `pnpm dev` 打开一个子应用"是日常改一行代码的默认姿势 ——
`docs/decisions/0033` 恰好改的就是这条路径的形状（`devAccount` 只在开发构建里构造），
单测能钉住"不传账号就不登录"，却钉不住"浏览器里真的登录了没有"。

它验证四件事，都是单测结构上测不到的：

1. **独立运行真的走自建会话** —— 证据是 `localStorage` 里出现 `bw.session`
   （刷新令牌是会话唯一落盘的东西；访问令牌只在内存）；
2. **开发构建真的用开发账号自动登录** —— 页面出现**权限产物**
   （右栏、模块标签按钮、审计区块），而不是只渲染了一个外壳；
3. **后端没起时单跑不白屏** —— 这是 `0031` 特意保护的性质：后端不可用时应给出一句
   明确的话（两个控制台是"缺哪个菜单码"的空态，管理面与审计台是"正在获取权限…"），
   而不是一片空白；
4. **此时不该有脚本层报错** —— 自动登录失败是**预期内**的路径，不该以"未处理的拒绝"
   的形式冒到控制台。这一条**当场抓到一个真缺陷**：两个控制台当时写的是
   `void ensureSession().finally(render)`，丢掉了返回值 ⇒ 后端没起时各抛一条
   `unhandledrejection`；改为与 `platform-admin` 一致的 `try/catch`（失败也渲染，但记一条 error）。

**它自己判定后端在不在**（探 `/api/health`），按对应的一套期望断言，因此两种状态下都能跑、
结论都算数。

**报错分两类**，这个区分是脚本能用的前提：

| 类别                                                                  | 后端在跑 | 后端没起                         |
| --------------------------------------------------------------------- | -------- | -------------------------------- |
| 脚本层（未捕获异常、未处理的拒绝，CDP 与页面侧各报一次、按内容去重）  | 判失败   | **判失败**                       |
| 网络层（浏览器为每个非 2xx 记的一条 `Log.entryAdded`，`source=network`） | 判失败   | 只报个数（**请求本来就该失败**） |

不分开的话，后端没起时那几条 502 会把脚本永远染红。

> **判据只能选"权限产物"，不能选"布局外壳"。** 这条是实测撞出来的：
> 最初给管理面选的 `ready` 判据是 `admin-tabs`、给审计台选的是 `audit-identity` ——
> 两者都**无条件渲染**，于是后端没起时它们照样在，脚本报出"后端没起却出现了鉴权内容"
> 这种假问题。真正由权限决定的是**里面装的东西**（`admin-tab-users`、`audit-list-panel`）。
> 写这类断言时先问一句："这个元素在没权限时会不会也渲染？"

### `verify-api-contract.mjs` 做什么

把 `docs/api/README.md` §一 那份**手写的接口清单**钉在契约上。按 `0039` 的判据
（**它会不会因为一次普通提交而变？**）清单里每一样东西都会漂 —— 新增一个接口就是一次普通提交。
它与"测试用例数"的唯一区别是**有权威源**（`docs/api/openapi.json` 是从运行的应用导出的、
与代码同一次提交），所以处置不是"删掉不要写"，而是**钉住**。

**为什么不只核三个总数**：三个总数旁边就有对照（汇总表之和、模块数），而**逐条清单漂了没有任何对照** ——
后端加一个接口、文档同时少一行时，总分表与清单**自洽地错**（两边都"看着对"），
而少一行在满屏表格里与"这个域本来就这么几个接口"长得一模一样。所以清单必须与**契约**比。

**查八个方面**（最后输出 11 条断言行 —— 总览句那三个数各自单独报出，便于一眼定位）：

| 查什么 | 防的错 |
| --- | --- |
| 总览句的 路径 / 接口 / 模型 三个数 | 加删接口后只改了正文、没改总览 |
| 每条明细行都归属到某个 `### 域名（模块名）` 小节 | 改了小节标题的写法 ⇒ 该节的行从**清单比对与按模块汇总里同时消失**（静默通过） |
| 明细表的 `方法 + 路径` 与契约**双向**相等 | 新增接口未登记；接口删了文档没跟；路径或方法改了（`PUT` → `POST`） |
| 明细表内无重复行 | 复制粘贴多一行，所有计数同时偏 |
| 汇总表接口数之和 = 契约接口数 | 只改明细、忘改汇总 |
| 每个模块的汇总数 = 该模块的明细行数 | 同上，且能定位到"是谁漏了" |
| 汇总行写了分项时，分项之和 = 该行写的数 | 汇总行内部自相矛盾 |
| 汇总表的模块名在 `server/` 下真有 `pom.xml` | 模块改名 / 名字写错 |

> `sts-server-auth` 在文档里跨两节（**权限域** + **组织域**），按**模块名**归拢后相加 ——
> 这正是它必须是 43 + 11 = 54、而不是两行各写一个数的原因。

**一条硬规则：解析不出来 = 失败。** 它靠正则读 Markdown，标题措辞或表格列顺序一变就会**匹配不到**；
那时如果"匹配不到就跳过"，它会在什么都没查的情况下报"通过" —— 那是**又一个空转的检查**，
比没有检查更坏（它让人以为有东西在守）。所以取不到总览句 / 汇总表 / 明细表任一处，都直接非零退出，
并指出**期望的形态**。**宁可假红，不可假绿。**

**刻意不查的：**

- **`@Operation` 描述、字段说明** —— 散文，判不了对错。
- **模型名**：只核总数（100 = `components.schemas` 条目数）。「关键数据模型」表是**精选**（62 个名字 vs 100 个 schema），
  而且混着 `AuditLogQuery` / `OrganizationQuery` / `OrgMemberQuery` / `UserGroupQuery` /
  `UserGroupMemberQuery` / `AuditResult` 这六个**被 Springdoc 摊平成查询参数、压根不是 schema** 的名字 ——
  逐个判就得维护一张白名单，而**会误报的检查终将被关掉**（同 `docs/decisions/0036` 决策 5）。
- **§五「待补充」里的历史进度句**（"已补齐到 20 条路径"这类）：与 ADR 同性质，记的是当次进度。

> **它证明不了"契约是对的"。** 它两边读的都是**入库的文件** —— 后端接口改了而忘了跑
> `scripts/export-openapi.sh` 时，契约与清单一起旧、两边一致，本脚本会报通过。
> **那一段已由 CI 的 `server` job 兜住**（重跑导出再与入库版本逐字节比对，见 `docs/decisions/0042`）。
> 两者不可互替：本脚本守"契约与文档一致"，那一步守"契约与代码一致"。

> **它不是空转**：用 11 个扰动逐个验过（删明细行 / 改汇总数 / 改三元组 / 删整句 / 模块名写错 /
> 塞一条契约没有的接口 / 改方法 / 重复列一行 / 改分项之和 / 改模型数 / **把小节标题里的模块名去掉**）——
> 11 个全部非零退出，且各自报出的正是对应的那一项。

> **它的两个兄弟都在 CI 里，都不在这里。** ① "契约有没有跟上代码"由 `server` job 的一步做
> （复用已构建的 jar 重跑 `export-openapi.sh` 再比对，见 `docs/decisions/0042`）；
> ② "生成物有没有跟上契约"（`docs/api/openapi.json` → `common-api/src/generated/schema.d.ts`）
> 由 `web` job 的一步做（重跑 `gen:api` 再比对，见 `docs/decisions/0041`）。
> 两者**刻意都没写成 `scripts/verify-*.mjs`**：① 要构建产物 + JDK 21 + 启一次应用，
> ② 要 `node_modules`（`openapi-typescript` + `prettier`）—— 都不满足本目录"零前置"这条约定。

### `verify-result-codes.mjs` 做什么

把"**枚举里登记了一个码，但没有任何代码会抛它**"这件事变成构建期可查的。它读两个枚举
（公共 `ResultCode` 与权限域 `AuthResultCode`）的全部常量，再扫 `server/*/*/src/main/java`
里对它们的引用；**没有引用的码必须在脚本的 `UNUSED` 表里写明理由**，否则非零退出。

**为什么这类错值得一个守卫。** 错误码是"先登记、再使用"的，而登记了没用那一个
不影响编译、不会让任何测试变红 —— 代价在很久以后才出现：前端照着文档分支，落在一个
永远不成立的条件上（`docs/decisions/0059` 那轮就抓到一个实例：日志 / 事件详情接口的
描述写着 `40401`，实现返的是 `40400`，而那句话已经生成进了契约与前端类型）。

**它守的是"有没有落点"，不是"有没有断言"。** 两者是两件事：有落点却没有用例钉住，
在它的射程之外（断言形式太多，机械判不了 —— `0059` 那轮是人工扫出 `404xx` 的六处空缺的）。
但它守的是**前置条件**：一个连抛出点都没有的码，谈不上有断言。

**它与"全称句守卫"的区别**（这是它值、而 `0056` 那类不值的理由）：它判的是
*某个标识符在源码里有没有被引用* —— 语法级、逐字符可判、不依赖任何约定；而
"给『全仓都……』加机械守卫"要理解语义，那是 `0056` 列为刻意不做的那一类。

**它区分两种"没有落点"**：

| 类别 | 含义 | 处置 |
| --- | --- | --- |
| `intentional` | **有意不用**：那条语义已经在别处表达过了 | 改动它等于改语义，不该动 |
| `pending` | **登记了但功能没做** | 它是**待办**；守卫在这里兼任"码已经先修好、路还没修"的清单 |

**它当场抓到三个码**（这就是它值得存在的实证）：`ROLE_NAME_EXISTS` / `ORG_NAME_EXISTS` /
`USER_GROUP_NAME_EXISTS` —— 三个域都登记了"名称已存在"，但**没有任何地方抛**，
而它们各自的兄弟 `*_CODE_EXISTS` 都有落点。这正是 `docs/decisions/0056` 未覆盖 4
（"`*_name` 该不该唯一"）的**具体形状**：不是一个疑问，而是三个具体的码加三处缺失。

**它后来还接住了一个"反向"的码**：`TENANT_NAME_EXISTS(40905)` 此前**有**落点
（`TenantService` 真的在挡租户名重名），定案之后**落点没了** ⇒ 它从"有落点"掉进
"没落点"，必须进 `UNUSED` 才不报红，而"进名单"这个动作本身逼着人写下理由。
**这是白名单的另一种用法**：不只登记"还没长出来的落点"，也记下"**决定不再需要的**落点"。

**那批码已经定案**（`docs/decisions/0061` 三个名称码 · `0062` 租户名那一格）：
**一律"有意不用"**，现在四个名称码（`40905` / `40907` / `40909` / `40911`）**并列**住在
`intentional` 那一栏 —— 它们是这个脚本第一次"报出来、然后被真正答掉"的码，
也是那条 `pending` → `intentional` 通路走过一遍的凭证。判据不是"租户做了所以别的也该做"
（那是把对照当理由），而是 `0062` 定下的那条：**判一个列该不该挡重名，看它在语义上是否
指认唯一对象 —— 不看它有没有索引**。名称谁也不指认（四个域**都带 `parent_id` 层级**，
层级的语义就是"同一名字可以在不同上级下复用"；消歧的职责落在"名字旁边有没有唯一标识"上：
组织靠树嵌套与 `fullName`、其余靠 `*_code`），所以四个都不挡；`tenant_name` 此前是
唯一一个反方向的，已连同两处判重一起删掉。**这批码里只有一个真缺东西**：
用户页「角色来源」的来源用户组列漏了 `groupCode`（角色列三个标识都在），
补的是**编码**，不是禁止同名。
⇒ 所以"报出来"不总是意味着"要去实现它"：这道守卫能判的只是**有没有落点**，
"该不该有落点"仍然要人（或者一片 ADR）来答 —— 而答案也可能是"**这条路本来就不该有**"。

**两个方向都验过**（扰动）：把 `RoleService` 的一处 `ROLE_CODE_EXISTS` 改名成
`ROLE_NAME_EXISTS` ⇒ 一次扰动同时触发两条 —— 前者变零落点（报"没有落点又没登记"）、
后者有了落点（报"白名单腐烂"）。

**它也有天然的负例**：首次运行就报出三个未登记的码 —— 那不是脚本写错了，是它第一次工作。

**刻意不查的：** 注释里提到的码（扫描前会剥掉注释 —— 否则一句"详见 xxx 的说明"就能把
零落点的码伪装成有落点）、断言（见上）、前端读了哪些码（那是跨端判据）。

### `verify-unique-columns.mjs` 做什么

把 `docs/decisions/0062` 那轮的**手工对账**机械化：服务层挡重名的每一列，
要么库上有 UNIQUE 索引兜底，要么在白名单里写明"业务规则级唯一"的理由。
它查四件事：

1. **每个被引用的 `*_EXISTS` 码都在脚本的 `JUDGED` 表里登记了"判的是哪一列"** ——
   登记的动作就是 `0062` 判据的落点：新加判重必须先回答"这个列在语义上指认什么"。
2. **登记的码仍然被引用**（腐烂检查）。哪天有人走 `0062` 的路删掉某处判重，
   守卫会同时提醒：删登记、把列挪进 `NO_INDEX_ALLOWED` 写明理由 —— **一处决定，两处提醒**。
3. **三方言的唯一索引集合一致**。三份迁移脚本本该是同一次提交的拷贝（`0038`），
   此前谁改一份漏两份没有任何东西会发现 —— 这是白捡的一条。
4. **每个判重列要么有 UNIQUE、要么在 `NO_INDEX_ALLOWED` 里**。反向腐烂同样红：
   给 `email` 加了 UNIQUE，白名单理由就过期了。

四格里只有"服务层判重 + 库上无索引 + 不在白名单"要红；**"有 UNIQUE 却不判重"
是合法形态**（数据约束自己会以 `40900` 兜底，如 `bw_auth_refresh_token.jti`），刻意不查。

**它是 `0062` 删完判重之后出生的**：那次手工对账的结论是"无索引却判重"的列清空、
`email` / `phone` 两列进白名单 ⇒ 守卫**首跑即全绿**。它守的是"对账"，不替人回答
"该不该判重" —— 那仍是 `0062` 的判据（语义上指认什么），登记表只是逼人把答案写下来。

**三个方向都验过**（扰动）：`JUDGED` 加一个源码里不存在的码 ⇒ "登记的码都还有落点"
红；白名单豁免的键改名 ⇒ "判重列"红；只给一个方言加 UNIQUE ⇒ "三方言一致"与
"白名单腐烂"**一次红两条**。

**刻意不查的：** 判重的具体写法（`selectCount` / `selectAny*` / exists helper 是代码风格，
不是要钉的事实 —— 锚定的是 `*_EXISTS` 码的引用）、并发窗口本身（`email` / `phone`
**有意**接受漏网，那是 `0047` 定的权衡，不是缺陷）。

### `verify-like-escaping.mjs` 做什么

把 `docs/decisions/0067` 那条约定机械化成一条零前置的检查：**搜索关键词进 `LIKE` 之前必须转义**，
而且这件事**只有一种写法**。它查三件事：

1. **`server/*/src/main/java` 里没有裸的 `like*` 调用**（`.like(` / `.likeLeft(` / `.likeRight(` /
   `.notLike*(`），除非在脚本的 `ALLOWED` 里登记过 —— 登记的动作就是回答"这个实参是从哪来的"。
   现在名单上只有 `OrganizationService` 那两处 `likeRight(getCodePath, …)`：`code_path` 是
   "各级**主键** + 斜杠"（形如 `/1/5/`），只有数字与 `/`，用户手打的通配符进不来。
2. **登记的条数逐条对上**。这一条是照 `0055`「只钉一支等于没钉」加的：只登记"形态"的话，
   两处同名调用改掉一处、另一处还在，检查照样报绿 —— **当次扰动实测到了这个假绿**，
   所以改成对账条数。真要多一处同类调用，就来把数改掉（改数的动作正是复查实参的时机）。
3. **带 `LIKE` 的 SQL 片段同一行必须有 `ESCAPE`** —— 挡住"手写一个 `LIKE` 而忘声明转义符"。
   它同时守着 `KeywordLike` 自己：哪天它的片段被改回裸 `LIKE`，这里会红。

**为什么它值得存在**（而 `0064` 那类不值得）：它锚定的是 `.like(` 这个**方法名** ——
无歧义的 token 形状，扫到就是扫到，不依赖对 JSX 树或展示语义的理解。
它要防的错是"新加一处搜索时又写了 `.like()`"：那种写法读起来完全正常（本仓此前 15 处都是它）、
测试也照样绿（新搜索本来就没有"搜不到"那一支断言），只有**"输 `%` 列出全表"**这个
没人会去试的角落会露出来。

**三个方向都验过**（扰动）：加一处裸 `.like(` ⇒ "没有裸调用"红；把 `condition()` 里的
`ESCAPE` 删掉 ⇒ "同一行必须有 ESCAPE"红；把两处白名单调用改掉一处 ⇒
**"裸调用"与"条数对不上"一次红两条**。

**刻意不查的：** `src/test` 下的 `like*`（测试自造查询不是生产路径，当前一处都没有）；
转义符选得对不对、`ESCAPE` 在三方言上是否同义（那是语义，靠跨 profile 的
`SearchWildcardApiTest` 钉，不靠文本扫描）；`.apply(...)` 传进去的实参有没有真的转过义
（要判这个得做数据流分析，误报率不划算 —— 这里只守"调用形状"）。

### `verify-filter-values.mjs` 做什么

把 `docs/decisions/0069` / `0070` / `0071` / `0072` 那四条约定机械化成一条零前置的检查：
**会被精确匹配的值（过滤 / 判重）进 SQL 之前、以及要拿它去匹配的那些值进库之前，都必须规范化**，
而且这件事**只有一种写法**。它查六件事：

1. **`.eq(...)` 的条件里没有 `hasText(...)`** —— 那正是"值没规范化、拿判空当开关"的旧形态
   （日志 / 事件 / 审计三域此前 20 处全是它）。
2. **`.eq(...)` 的条件里没有 `query.getX().isBlank()` / `.isEmpty()`** —— 同上，只是写成了
   内联判断（用户 / 租户两域原本就是它）。
3. **三个手写聚合 SQL（`countByLevel` / `countByEventName` / `countByAction`）的实参里
   没有 `query.getXxx()`** —— 直传原始值会让手写 SQL 与同请求的 lambda wrapper 对同一个
   参数给出**不同判定**：Ognl 的 `<if test="app != null and app != ''">` 认为 `"   "` 给了值
   （于是 `AND app = '   '`），而 Java 的 `isBlank` 认为没给（于是不过滤）——
   症状是**同一个响应里「总数 3、各级别之和 0」**。
4. **枚举列的过滤值只能作为 `FilterValue.enumValue(...)` 的第一个实参出现**（`0070`）：
   五个 getter `getStatus` / `getResourceType` / `getMemberType` / `getLevel` / `getResult`
   （在全部 `*Query.java` 里一一对应 `status` / `resourceType` / `memberType` / `level` /
   `result`，**没有第二个含义**）在 `server/` 的 `src/main/java` 里**每一处出现都必须落在
   `enumValue(` 里**。判据是"**本条语句**（以 `;` 划界）里、它之前最近的那个函数名是不是
   `enumValue(`"—— 于是多行调用 `enumValue(\n  X::parse, …)` 也认得出来。绕开它有两种形态
   （改回 `FilterValue.text(query.getStatus())`、或者一处 `FilterValue` 都不经的
   `query.getStatus()`），两种都会报红。
5. **上报侧写进实体的过滤列必须过 `FilterValue`**（`0071`）：`sts-server-log` /
   `sts-server-track` 两个模块里 `entity.set<App|Namespace|TraceId|SessionId|UserId|DeviceId>(...)`
   的**实参**里要有 `FilterValue`。判据是"从 `(` 起取到**配对的** `)` 之间的实参文本"——
   于是多行调用 `truncate(\n FilterValue.text(…), 64)` 也认得出来。前四条管"读的时候归一"，
   这一条管"**写的时候也归一**"：只归一读取侧是空转 —— 上报 `app = " myapp "` 原样落库，
   而 `?app=myapp` 被规范成 `myapp`，那条记录**永远搜不到**。
6. **寻址列（`email` / `phone`）在判重口与写入口都不得直传 `request` 原值**（`0072`）：
   `sts-server-auth` 里 `entity.set<Email|Phone|ContactEmail|ContactPhone>(...)` 与
   `existsEmail(...)` / `existsPhone(...)` 的**实参**，**含 `request.getXxx()` 且不含
   `FilterValue` ⇒ 报红**。这两列不走过滤、走**判重**，而判重也是一次 `eq`
   （`0062` 把它们归在同一条判据下），却住在 `UserService` 里、曾经是全仓最后一份
   "只判空、不 trim"的副本。**两侧都锚**，因为改一半会造出"存 `13800138000`、
   比 `' 13800138000 '`"这种**新**的不对称 —— 同一个手机号能建出两个账号。
   这个判据有个好性质：**它能把改动前的写法打红**（`entity.setEmail(blankToNull(request.getEmail()))`
   正好落在射程里），而组织域那种合法的内联写法
   （`entity.setContactPhone(FilterValue.text(request.getContactPhone()))`）不会。

**为什么规则 4 的锚点比前三条更强**：前三条是"某个**名字**出现得可疑"，
规则 4 是"这五个 getter 只有**这一个**正当用法"，所以它可以要求"每一次出现都在 `enumValue(` 里"，
而不是去猜某种坏写法。代价是它把白名单固化成了"当前这五个名字"—— 下一个域新加一个枚举列、
用了新 getter 名时它**锚定不到**（与 `0064` 同一结论：能机械化的只是"无歧义的 token 形状"）。
反过来说：**给这五个 `Query` 加一处非过滤用途会让它报红**，那是提醒你去更新白名单，
不是让你忽略它。**规则 5 同一个代价**（六个列名硬编码），
但它的射程是被**刻意收窄**的：只认那两个上报模块里的 `entity`，理由见下面「刻意不查的」。

**为什么它值得存在**（而 `0064` 那类不值得）：它锚定的是 `hasText(` 这个方法名、
`isBlank()` 这个 **String 专属**方法、`countBy*` 这个方法名、那五个**只有一种用法**的
getter 名、以及 `entity.setApp(` 这类**只会从请求体取值的 setter** —— 都是无歧义的 token 形状，
扫到就是扫到。只揪 `isBlank()` / `isEmpty()` 而不揪
`!= null`，正是因为前者**不可能出现在 `Long` 上**（`tenantId` / `parentId` / `operatorId`
本来就不该规范化，锚定 `!= null` 会大面积误报）。

**它自己有两个假绿是在扰动中发现的，都当场修掉**：

- 规则 3 起初要求调用点前面有 `.`，而同类内调用写的是 `selectCount(baseWrapper(...))` ——
  **一次都没命中**（改成 `\b` 才对）。
- 同一条规则起初还把审计域的 `baseWrapper` 纳入射程 ⇒ **误报**：它的实参里混着
  `operatorId`（`Long`），调用点写的是 `visibility.operatorFilter(query.getOperatorId())`，
  那是正当的。按 `0064`「会误报的守卫比没有更糟」把它移出射程，
  并在脚本的"刻意不查"里写明理由。

**七个方向都验过**（扰动）：把 `UserService` 改回 `!= null && !isBlank()` ⇒ 规则 2 红；
把 `ClientLogService` 两处改回 `.eq(hasText(...), ...)` ⇒ 规则 1 红（**两处都报**）；
把 `countByAction` 的实参改回 `query.getResource()` ⇒ 规则 3 红；
把租户域改回 `FilterValue.text(query.getStatus())` ⇒ 规则 4 红（行号指向那一条语句）；
再改成完全绕开的 `query.getStatus()` ⇒ 规则 4 同样红；
把日志域的 `entity.setApp(FilterValue.text(request.getApp()))` 改回 `entity.setApp(request.getApp())`
⇒ 规则 5 红（行号指向 `ClientLogService:111`）；事件域改回 `truncate(item.getUserId(), 64)`
⇒ 规则 5 红（`ClientEventService:112`）。**规则 5 的射程边界也验了**：审计域
`AuditRecorder` 里那句 `entity.setTraceId(record.traceId())` **不报红**（审计不在
`REPORT_MODULES` 里，且审计的 `traceId` 不参与过滤），两个上报模块里的 `vo.setXxx(...)`
也不报红（装的是从库里读出来的值）。
**规则 6 两次也是**：把 `UserService#create` 的写入侧改回**改动前的原句**
`entity.setEmail(blankToNull(request.getEmail()))` ⇒ 规则 6 红（`UserService:149`，写入口）——
这一条同时说明**这个守卫逮得住它要防的那个 bug**；改回
`existsEmail(tenantId, request.getEmail(), null)` ⇒ 规则 6 红（`UserService:137`，判重口）。
**规则 6 的射程边界也验了**：`OrganizationService` 里两处
`FilterValue.text(request.getContactXxx())` 与两处 `vo.setContactXxx(...)` 都不报红。

**刻意不查的：** "新加一处过滤值有没有真的过 `FilterValue.text`"（要判它得做数据流分析，
而合法的 `!= null` 到处都是 —— 见上面那条）；"**下一个域新加的枚举列**有没有归一"
（规则 4 的白名单是当前这五个 getter 名，新名字锚定不到 —— 只能靠纸面规则，见 `0070`）；
**`entity.setStatus(...)` 的实参**（同一个 setter 在四个域有四种写法 —— `resolveStatus(...)` ·
调用方算好的局部变量 `status` · `Status.ENABLED.wireName()` · 直接字面量 —— **文本上分不出
"已归一"与"没归一"**，角色域的 `applyTo` 写的就是 `entity.setStatus(status)`，锚定它会误报；
那两处 `update` 由 `WriteSideNormalizationApiTest` 钉着）；
**`vo.setXxx(...)` / `context.setUserId(...)` / 关联表的 `target.setUserId(...)`**
（同名 setter 全仓到处都是，`setUserId` 就有八处与过滤无关 ⇒ 规则 5 把射程限定在
log / track 两个上报模块、且只认接收者是 `entity`）；
**`entity.setLevel(...)`**（日志域的 level 也是过滤列，但实参是 `level.wireName()`，
枚举归一走 `ClientLogLevel.parse`，拿 `FilterValue` 要求它只会误报）；
**审计域的写入整体不在射程**（值取自 `@Audited` 注解与上下文，不由外部输入决定）；
**规则 6 看不到"先取局部变量、再直传原值"**（`String phone = request.getPhone();` ——
判它得做数据流分析，与上面第一条同源）；**`email` / `phone` 的再加工**
（归一大小写 / 去内部空格 / 格式化号码）**规则 6 也挡不住只改一侧** ——
它认的是"直传 `request`"这个形状，不认"两侧是否共用同一个值"；
`trim()`（Java，只吃 `<= U+0020`）与 `isBlank()`（`Character.isWhitespace`）的覆盖面之差
（那是待裁决的口径，不是守卫能判的）。

### 脚本的硬性约束

- **可重复执行**：跑第二遍不应产生副作用或报"已存在"错误。
- **失败要响亮**：缺环境变量、端口不通、前置服务未启动，都应打印明确原因并**非零退出**，不要静默跳过。
- **结论打完就要退出**。CDP 是一条长连接的 WebSocket，`finally` 里只 `chrome.kill()` 会让事件循环一直被挂住 ——
  症状是"日志里结论已经打出来了，进程却迟迟不结束"，在流水线里表现为**超时**而不是那行结论，
  而超时会把排查引向"脚本卡住了"。收尾必须 `ws?.close()` **加**一个兜底 `setTimeout(() => process.exit(…)`。
  （`verify-menu-permissions` / `verify-portal-auth` / `verify-audit-pipeline` 曾漏了这条，
  `docs/decisions/0035` 那轮补齐；现在所有 `verify-*.mjs` 都是这条写法。）
- **改动子应用的启动路径后，e2e 要在"dev server 冷着"的状态下跑一遍**。开发服务器**第一次**被访问时
  才做依赖预构建，热与冷不是同一种环境 —— 冷启动会暴露只在首帧出现的问题（例如自动登录还没完成就渲染）。
  `scripts/verify-standalone-dev-login.mjs` 正是为这条而生，重跑一遍前**先重启对应的 dev 服务**。
- **能不要前置条件就不要** —— 这一条决定脚本能不能进 CI。`verify-nginx-config.mjs`、
  `verify-docs-index.mjs`、`verify-api-contract.mjs`、`verify-result-codes.mjs`、
  `verify-unique-columns.mjs`、`verify-like-escaping.mjs` 与 `verify-filter-values.mjs` 是**零前置**的
  （不连网、不读产物、不写文件），所以流水线的 `guards` job 直接跑它们；`verify-bundle-secrets.mjs` **不需要服务、但需要前端产物**，
  所以它跑在 `web` job 的构建**之后**（顺序不能反 —— 反了它就扫不到东西，还会静默"通过"）；
  需要前置服务的脚本继续留在本机 —— 真机层（浏览器 + 冷启动 dev server）**刻意不在 CI 的必过项里**，
  理由见 `docs/decisions/0036`；全树格式检查在 `web` job 里（`pnpm format:check`，是 npm script 不是脚本，
  见 `docs/decisions/0037`）；**契约生成物的同步检查**同样进不了 `guards`（它要 `node_modules`），
  也落在 `web` job 里（见 `docs/decisions/0041`）。
- **凭据走环境变量**：脚本里不出现任何密码（见 `docs/decisions/0003`）。
- **后端进程的启动方式**：`spring-boot:run` 是 CLI goal，会落到 reactor 里**每一个**模块上，
  而父工程 `sts-parent` 没有 main class ⇒ 带 `-am` 一定失败
  （`Unable to find a suitable main class`）。上游模块先用 `install` 进本地仓库，启动时只留应用模块：

  ```bash
  cd server && mvn -pl sts-app/sts-app-server -am install -DskipTests   # 首次或上游有改动时
  cd server && JAVA_HOME=<jdk21> mvn -pl sts-app/sts-app-server spring-boot:run
  ```

> **这条曾经名不副实。** 4 个 `verify-*.mjs` 各自硬编码了同一对种子口令，
> 与本节直接矛盾 —— 而且那是把一个**会变的值**复制了 4 份：口令在
> `application-h2.yml` 里一改，四个脚本会一起静默失效。
>
> 现在它们统一从 `scripts/lib/seed-credentials.mjs` 取，而那个模块从
> `application-h2.yml` 读（种子口令的**唯一真值来源**，见 `docs/decisions/0029`）。
> 需要指向另一套账号时用环境变量覆盖：
>
> ```bash
> BW_SEED_ADMIN_PASSWORD=… BW_SEED_AUDITOR_PASSWORD=… node scripts/verify-admin-permissions.mjs
> ```
>
> 读不到口令时它**抛错**，不会安静地返回 undefined —— 那样脚本会拿着空口令去登录，
> 报出一个与真正原因相隔很远的错。
