# Whale IM 前端验收清单

> 对应 spec：`docs/specs/2026-10-24-wave5.52-im-frontend/spec.md`

- [x] 子应用 `whale-im` 可构建：8107 / base `/im`，qiankun 生命周期完整
- [x] `/im` 同时作为路由与 API 前缀时可刷新：vite 与基座均按 `Accept: text/html` 旁路
- [x] 跨源鉴权可用：基座 props 注入登录态，`getToken/getPermissions` 解析顺序正确
- [x] 文件上传/预览/下载带 Bearer：multipart 不走 JSON 客户端，图片用 objectURL
- [x] WS 鉴权：先换一次性 ticket，URL 不含 JWT；心跳与 1s→30s 退避重连生效
- [x] 帧处理覆盖：`message.new/recalled`、`agent.meta/delta/done/error/confirm` 均有分支
- [x] 重连后不丢消息：刷新会话列表并按 `afterSeq` 补拉，按 `id` 去重、按 `seq` 排序
- [x] 会话列表：搜索、未读、摘要（CARD/IMAGE/FILE 占位文案）、按活跃时间排序
- [x] 消息渲染：TEXT/MARKDOWN/IMAGE/FILE/CARD；引用条可跳转；撤回态可见
- [x] Markdown 安全：本地渲染器无 `dangerouslySetInnerHTML`
- [x] 输入区：Enter 发送、Shift+Enter 换行、发送失败保留草稿、发送中禁用
- [x] 机器人：流式占位（思考中/降级/耗时/错误）与真实消息替换
- [x] 工具轨迹：`Collapse` 懒加载 `GET /im/agent-runs/{id}`，citations 可复制，空轨迹有提示
- [x] 反馈 👍/👎：本地切换选中态，不伪造后端请求
- [x] 确认卡片：卡片 `permission` 与 `sys:im:chat` 双判定、过期禁用、无 `runId` 提示失效
- [x] 后端卡片 JSON 含 `runId`，刷新后仍可确认（单测断言）
- [x] 成员面板：角色标识、群主/管理员增删成员、非群主退群二次确认、BOT 信息
- [x] 基座：`apps.ts` 注册、菜单「消息」、`im/*` 路由、`/im` 网关前缀
- [x] 基座未读角标：30s 轮询 + 聚焦刷新，无权限或失败静默为 0
- [x] 日志规范：`createLogger('whale-im › ...')`，`whale-im/src` 无 `console.`
- [x] `pnpm --filter whale-im --filter @bluewhale/whale-platform lint` EXIT=0（0 warning）
- [x] `pnpm build` 全 workspace 19/19 成功
- [x] `whale-im` 后端单测 113 例全绿且 Checkstyle 0
- [x] 规划 §七/§八、devlog、review 已更新
