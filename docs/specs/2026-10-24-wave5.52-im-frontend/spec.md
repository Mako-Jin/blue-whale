# Wave 5.52 · whale-im 前端子应用 Spec

> 制定日期：2026-10-24 ｜ 状态：已实现（T18b，2026-10-24）
> 关联规划：`docs/plans/wave5.52-webim-langchain-agent.md` §七、§八（T19 前置）
> 目标：补齐 IM 前端（`frontend/platform-apps/whale-im`，8107 / base `/im`），并在基座接入「消息」入口与全局未读角标。

## 一、范围与非目标

### 范围

1. 新建 qiankun 子应用 `whale-im`：三层布局（会话列表 / 消息流 / 成员与机器人信息）。
2. 会话：列表（未读红点、搜索）、创建单聊/群聊、打开「蓝鲸助手」会话。
3. 消息：文本 / Markdown / 图片 / 文件 / 确认卡片渲染；发送（`clientMsgId` 幂等）、撤回、已读上报、历史分页。
4. 实时：WS ticket 握手、心跳、断线重连与按 `lastSeq` 补拉；机器人流式帧（`agent.*`）。
5. 机器人消息：流式打字、引用角标、工具轨迹折叠（`GET /im/agent-runs/{id}`）、👍/👎、确认按钮。
6. 基座：`/im` 微应用注册、菜单「消息」、全局未读角标、`sys:im:chat` 权限接入 `PermissionProvider`。
7. 日志统一 `createLogger('whale-im › …')`，不使用 `console`。

### 非目标

- 不改造后端 IM 业务逻辑（唯一例外见 §四.5：确认卡片 content 增补 `runId`）。
- 不实现语音/视频、消息撤回超时策略变更、群公告等未登记需求。
- 不做 IM 独立登录页；鉴权复用基座登录态（同源 localStorage + qiankun props）。

## 二、鉴权与跨应用上下文（决策）

开发态子应用运行在 `localhost:8107`，与基座 `localhost:8000` **不同源**，localStorage 不共享。因此采用「基座 props 注入 + 同源 localStorage 兜底」：

1. 基座 `apps.ts` 中 `whale-im` 条目额外注入：
   ```ts
   props: {
     token: 'bluewhale-platform-token',
     getToken: () => getToken(),
     getPermissions: () => getCurrentUser()?.permissions ?? [],
   }
   ```
   其中 `getToken` / `getCurrentUser` 来自 `whale-platform/src/api/token.ts`（已存在）。
2. 子应用 `src/platform/host.ts` 保存 `mount(props)` 传入的宿主上下文；`setHostProps()` 在 render 前调用。
3. 子应用 `src/api/token.ts` 解析顺序：
   - accessToken：`host.getToken?.()` → `localStorage['whale_platform_access_token']` → `localStorage['whale_im_access_token']`
   - permissions：`host.getPermissions?.()` → `whale_platform_user` / `whale_im_user` 的 `permissions`
4. 子应用 `App` 用解析到的权限包裹 `<PermissionProvider permissions={...}>`；确认按钮额外按卡片 `permission` 与 `sys:im:chat` 双判定。
5. 独立启动（无 qiankun）时无 token：`getToken()` 为空，请求由网关返回 401，`whale-log-sdk` 留痕并提示「请从平台基座进入」。

## 三、文件清单（精确到文件）

### 新增 `frontend/platform-apps/whale-im`

```
package.json              name=whale-im；deps 对齐 whale-oa，另加 @bluewhale/whale-ui、@ant-design/icons
tsconfig.json             复制 whale-oa（extends ../../tsconfig.base.json）
vite.config.ts            qiankun('whale-im')，port 8107，base '/im'，/im 代理（REST + ws）
index.html                title「Whale IM - 消息中心」
src/main.tsx              initAuditLogging + ThemeProvider + qiankun 生命周期（setHostProps）
src/App.tsx               三层布局 + 会话/消息/成员编排 + PermissionProvider
src/index.css             布局与消息气泡样式（使用 --bw-* / antd token）
src/vite-env.d.ts         vite/client 引用
src/public-path.ts        复制模板
src/platform/host.ts      宿主 props 存取（token/permissions）
src/api/token.ts          鉴权解析（§二）
src/api/client.ts         createHttpClient（getToken/401 清态）
src/api/im.ts             REST 封装与类型（§四.1）
src/api/file.ts           uploadFile（multipart）、previewBlob、downloadBlob
src/ws/client.ts          ImSocket：ticket 握手、心跳、重连、帧分发
src/hooks/useImData.ts    会话/消息状态机（加载、发送、已读、补拉）
src/hooks/useImSocket.ts  WS 与状态机桥接（帧 → state）
src/components/ConversationList.tsx
src/components/MessageList.tsx
src/components/MessageItem.tsx
src/components/ConfirmationCard.tsx
src/components/AgentTracePanel.tsx
src/components/MemberPanel.tsx
src/components/Composer.tsx
src/components/NewConversationModal.tsx
src/utils/markdown.tsx    轻量安全 Markdown 渲染（不引入新三方依赖）
src/utils/format.ts       时间/文件大小格式化
```

### 修改 `frontend/platform-apps/whale-platform`

| 文件 | 改动 |
|---|---|
| `src/qiankun/apps.ts` | 新增 `whale-im`：`entry: '//localhost:8107'`、`activeRule: '/im'`、props 见 §二 |
| `src/router/menu.ts` | 新增 `{ key: '/im', label: '消息' }` |
| `src/router/routes.tsx` | 新增 `{ path: 'im/*', element: <SubAppContainer /> }` |
| `src/layouts/MainLayout.tsx` | `MENU_ICONS['/im'] = <MessageOutlined />`；菜单 label 包 `Badge`（未读数） |
| `src/hooks/useImUnread.ts` | 新增：30s 轮询 `/im/conversations` 汇总未读；无 `sys:im:chat` 或请求失败时静默为 0；页面聚焦时立即刷新 |
| `vite.config.ts` | `GATEWAY_PREFIXES` 增加 `'/im'`（沿用 `Accept: text/html` 旁路） |

### 修改 `frontend/platform-apps/whale-im/vite.config.ts` 代理细则

```ts
const gatewayBypass = (req) =>
  req.method === 'GET' && req.headers.accept?.includes('text/html') ? req.url : undefined;
server: {
  port: 8107,
  proxy: {
    '^/im(/|$)': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
      ws: true,
      bypass: gatewayBypass,
    },
  },
}
```

### 后端最小改动（1 处）

`ImAgentChatService.handleConfirm()` 构造卡片 JSON 时增加 `card.put("runId", String.valueOf(run.getId()))`。
原因：`POST /im/agent-runs/{id}/confirm` 需要 `runId`，而历史 CARD 消息（刷新后）只能从 content 取得；否则刷新后确认按钮不可用。

## 四、接口契约（前端类型，字段与后端 VO 一致）

1. `src/api/im.ts` 类型：
   - `ConversationVO { id, type: 'SINGLE'|'GROUP'|'BOT', title, ownerId, lastSeq, unread, lastActiveAt, lastMessage?: MessageVO }`
   - `MessageVO { id, conversationId, seq, senderId, senderType: 'USER'|'BOT'|'SYSTEM', contentType: 'TEXT'|'MARKDOWN'|'IMAGE'|'FILE'|'CARD', content, clientMsgId, replyTo?, status, createTime }`
   - `MemberVO { userId, memberType, role, lastReadSeq, muteUntil?, joinTime }`
   - `MessagePageVO { records: MessageVO[], hasMore }`
   - `AgentRunDetail { runId, conversationId, status, requestMessageId?, responseMessageId?, startedAt?, finishedAt?, durationMs, tools: unknown[], citations: unknown[], confirmation?: { confirmationId, operation, summary, permission, expiresAt, runId? } }`
   - 卡片 content JSON：`{ type:'platform_op', confirmationId, operation, summary, permission, expiresAt, runId? }`
2. 图片/文件消息 content JSON：`{ fileId:number|string, name:string, size:number, contentType?:string }`；展示时通过 `previewBlob` / `downloadBlob` 携带 Bearer 拉取，生成 objectURL（图片）或触发浏览器下载（文件）。
3. 附件上传：`POST /file/upload`（multipart `file`，可选 `bucket=im`）→ `FileUploadVO { id, fileName, originalName, bucket, objectKey, fileSize, contentType, md5 }`；成功后以 `contentType=IMAGE|FILE` 发送消息。
4. WS URL：`new WebSocket(`${wsProto}//${location.host}/im/ws?ticket=${ticket}`)`；JWT 不入 URL。
5. 卡片确认：`POST /im/agent-runs/{runId}/confirm` body `{ approved }`；无 `runId` 的卡片按钮禁用并提示「该确认已失效，请重新发起」。

## 五、WS 状态机

1. 连接：`ticket` 成功 → 建连 → 收到 `ready` 记录 `heartbeatSeconds` → 每 N 秒发 `{"type":"ping"}`。
2. 重连：`onclose` 非主动关闭时按 1s/2s/4s/8s…最大 30s 退避重连，重连成功后按 `lastSeq` 对当前会话 `GET /messages?afterSeq=lastSeq` 补拉，并在会话列表上触发一次刷新。
3. 帧处理：
   - `message.new`：按 `id` 去重 upsert；当前会话追加并滚动，非当前会话累加未读；更新会话摘要。
   - `message.recalled`：设置 `status=0`，气泡显示「消息已撤回」。
   - `agent.meta`：创建流式占位 `{ runId, conversationId, text:'', degraded, intent }`。
   - `agent.delta`：追加文本（打字机直接追加，不节流，React 批量）。
   - `agent.done`：记录 `messageId/tokens/elapsedMillis/degraded`，占位保留至对应 `message.new` 到达；3s 后兜底移除并触发一次 `afterSeq` 补拉。
   - `agent.error`：占位转为错误提示（红色），提示语来自帧 `message`。
   - `agent.confirm`：登记 `confirmationId → runId`，占位结束；随后 `message.new` 的 CARD 消息渲染确认卡片。
4. 主动 `unmount` / 切换会话不清连接（应用级单连接）；`unmount` 时 `socket.close()` 并清理定时器。

## 六、交互与渲染细则

1. 会话列表：搜索按标题过滤；未读红点（`Badge dot` + 数字）；最后消息摘要（CARD 显示「[确认请求]」、IMAGE「[图片]」、FILE「[文件]」）；按 `lastActiveAt` 降序，本地即时重排。
2. 消息流：上滑到顶加载更早（`beforeSeq`，`hasMore` 控制）；`seq` 升序；发送者分为「我 / 他人 / 机器人 / 系统」四种样式；时间格式 `HH:mm`，跨天显示日期分隔。
3. 引用：消息 `replyTo` 展示引用条（发送者 + 摘要），点击滚动定位到原消息（`data-message-id`）。
4. 机器人消息：气泡下方操作条（引用角标、工具轨迹、👍/👎）；引用角标点击展开 `AgentTracePanel` 的 citations 列表（`sourceId/title/path`，可点击复制路径）。
5. 工具轨迹：`Collapse` 折叠，展示 `step/name/arguments/success` 与 `durationMs`；`tools` 为空时显示「本次未调用工具」。
6. 确认卡片：操作名、参数摘要（`pre` 折行）、所需权限、过期时间；`确认执行` / `拒绝`；提交中禁用；无权限（`usePermission().has(card.permission)` 为 false）时禁用并提示；`expiresAt` 已过则禁用。
7. 反馈 👍/👎：本地记录（`localStorage['whale_im_feedback']`，key=`runId`），点击切换选中态；不伪造后端请求。
8. 成员面板：成员列表（角色 Tag：群主/管理员/成员；BOT 单独标识）；群主/管理员可「添加成员」「移除成员」；非群主可「退出群聊」（二次确认）；BOT 会话展示机器人信息（名称、场景、能力说明、最近运行状态）。
9. 输入区：Enter 发送 / Shift+Enter 换行；发送中禁用；失败保留草稿并提示；图片/文件按钮走 `uploadFile`。

## 七、质量门禁

1. `node …/pnpm.cjs -C frontend lint` → EXIT=0（新代码 0 warning）。
2. `node …/pnpm.cjs -C frontend build` → 全 workspace 成功（新增 1 个子应用，19/19）。
3. 不新增第三方依赖（Markdown 用本地渲染器；图标用 `@ant-design/icons`）。
4. 全仓搜索 `console.` 在 `whale-im/src` 下 0 命中。
5. 后端改动后 `whale-im` 单测全绿 + Checkstyle 0。

## 八、验收口径（对应规划 §九）

| # | 验收 | 方式 |
|---|---|---|
| 1 | 基座「消息」入口可进入 IM，三级布局渲染 | 手动/构建产物 |
| 2 | 断线 30s 重连后按 `lastSeq` 补拉，消息完整有序 | WS 状态机单测 + 手动 |
| 3 | WS URL 无 JWT，ticket 一次性 | 代码审查 |
| 4 | 机器人流式输出、引用角标、轨迹折叠、确认卡片可用 | 手动联调 |
| 5 | 无 `sys:im:chat` 不可进 IM；无卡片权限确认按钮禁用 | 代码审查 + 手动 |
| 6 | lint/build EXIT=0 | 命令输出 |

## 九、风险

| 风险 | 处置 |
|---|---|
| 开发态跨源无 token | 基座 `getToken` props 注入；同源 localStorage 兜底 |
| 刷新后确认卡片缺 `runId` | 卡片 JSON 增补 `runId`（§三后端最小改动） |
| 图片鉴权 | 统一走 `previewBlob` + objectURL，不直接 `<img src>` |
| `/im` 既是路由又是 API 前缀 | 基座与子应用 vite 均按 `Accept: text/html` 旁路 |
