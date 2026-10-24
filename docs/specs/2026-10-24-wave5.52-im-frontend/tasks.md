# Whale IM 前端任务清单

> 对应 spec：`docs/specs/2026-10-24-wave5.52-im-frontend/spec.md`

## T1 子应用骨架

- [x] 1.1 新建 `frontend/platform-apps/whale-im/{package.json,tsconfig.json,index.html,src/{main.tsx,index.css,vite-env.d.ts,public-path.ts}}`（对齐 whale-oa 模板，端口 8107 / base `/im`）
- [x] 1.2 `vite.config.ts`：qiankun 插件 + `/im` REST/WS 代理 + `Accept: text/html` 旁路
- [x] 1.3 `src/platform/host.ts`、`src/api/{token.ts,client.ts,im.ts,file.ts}` 落地（契约见 spec §二/§四）
- [x] 验证：`pnpm -C frontend --filter whale-im build` 成功

## T2 实时通道

- [x] 2.1 `src/ws/client.ts`：ticket 握手、ping 心跳、指数退避重连、帧分发
- [x] 2.2 `src/hooks/useImSocket.ts`：`message.*` / `agent.*` 帧映射到状态
- [x] 2.3 重连后 `afterSeq` 补拉与去重（按 `message.id`、`runId`）
- [x] 验证：类型检查 + 手工审查帧分支覆盖率（ready/ping/message.new/message.recalled/agent.meta/delta/done/error/confirm）

## T3 主界面与消息渲染

- [x] 3.1 `App.tsx` 三层布局 + `PermissionProvider`
- [x] 3.2 `ConversationList`（搜索、未读、摘要、新建/机器人入口）
- [x] 3.3 `MessageList` / `MessageItem`（TEXT/MARKDOWN/IMAGE/FILE/CARD、引用条、日期分隔、撤回态）
- [x] 3.4 `Composer`（Enter 发送、上传图片/文件、失败保留草稿）
- [x] 3.5 `utils/markdown.tsx` 本地渲染器（标题/列表/代码块/行内代码/链接/加粗，禁 `dangerouslySetInnerHTML`）

## T4 机器人与确认

- [x] 4.1 流式占位消息（meta/delta/done/error）与真实消息替换
- [x] 4.2 `AgentTracePanel`（工具折叠、citations 跳转/复制、👍/👎 本地反馈、耗时/降级标记）
- [x] 4.3 `ConfirmationCard`（按 `permission` 与 `sys:im:chat` 判定、过期禁用、确认/拒绝调用 `/im/agent-runs/{id}/confirm`）
- [x] 4.4 后端 `ImAgentChatService.handleConfirm` 卡片 JSON 增补 `runId`；同步受影响单测断言
- [x] 验证：`whale-im` 后端单测全绿 + Checkstyle 0

## T5 成员面板

- [x] 5.1 `MemberPanel`：成员/角色/BOT 信息、添加/移除成员、退出群聊
- [x] 5.2 群主/管理员操作按钮权限与后端错误提示（403/409）展示

## T6 基座接入

- [x] 6.1 `qiankun/apps.ts` 注册 whale-im 并注入 `getToken/getPermissions`
- [x] 6.2 `router/menu.ts` + `router/routes.tsx` 新增「消息」入口与 `/im` 路由
- [x] 6.3 `MainLayout` 图标 + 未读 `Badge`；新增 `hooks/useImUnread.ts`（30s 轮询 + 聚焦刷新，失败静默）
- [x] 6.4 基座 `vite.config.ts` 网关前缀补 `/im`

## T7 质量门禁与文档

- [x] 7.1 `pnpm lint` EXIT=0；`pnpm build` 全 workspace 成功
- [x] 7.2 `whale-im/src` 无 `console.`
- [x] 7.3 更新规划 §七/§八（T19 前记录前端完成）、devlog 新增一节、`docs/reviews/2026-10-24-wave5.52-m5-whale-im-frontend.md`
- [x] 7.4 提交并推送 Gitee `origin/main`（`feat(im): 补齐 whale-im 前端子应用`）

## 完成判定

- spec §八 六项验收全部可达；lint/build/单测三项门禁 EXIT=0。
