# 冻结模块与共享包清单（F-05 / F-07 / F-09 结案）

> 结论日期：2026-10-28
> 状态：**冻结（代码保留、不扩张、不构建、不路由）**
> 原则：冻结不等于删除。保留代码与历史，避免破坏可能的外部引用与仓库考古；但不再投入功能开发、不纳入默认构建/部署路径。

## 1. 冻结清单

| 编号 | 对象 | 类型 | 冻结原因 | 处置 |
|---|---|---|---|---|
| F-05 | `backend/whale-registry/whale-config`（9201） | 后端模块 | 空壳管理面：仅启动类，无接口、无前端消费方；配置统一由 Nacos 承担 | 移除网关 `/config/**` 路由；移出镜像构建 matrix；移除 Prometheus 抓取；代码保留 |
| F-07 | `frontend/games/whale-games-core` | 前端共享包 | 主循环与资源清理未实现，且无游戏前端应用/消费方 | 标记冻结；不升级依赖、不新增功能；保留在仓库供后续游戏项目复用 |
| F-09 | `frontend/shared-packages/whale-common-three` | 前端共享包 | WebGL 渲染未验收，控制器/后处理/性能监控未封装，无消费方 | 标记冻结；不升级依赖、不新增功能；后续需要 3D 时重新立项 |

## 2. 影响面与验证

| 冻结对象 | 已做变更 | 验证 |
|---|---|---|
| `whale-config` | `whale-gateway/application.yml` 删除 `/config/**` 路由；`.github/workflows/docker-deploy.yml` 移除镜像 matrix 条目；两份 Prometheus 配置移除 `whale-config` job | 网关与 CI/Prometheus YAML 人工复核；`docker compose -f infrastructure/docker/docker-compose.yml config --services` exit 0 |
| `whale-games-core` | 仅在 [service-catalog](../service-catalog.md) 标注冻结 | 检索确认无前端/后端消费方 |
| `whale-common-three` | 仅在 [service-catalog](../service-catalog.md) 标注冻结 | 检索确认无前端/后端消费方 |

> 说明：仓库内仍有 `package.json`/workspace 条目，这是为了保留历史与可追溯性；冻结口径是“不消费、不升级、不构建为交付物”，不是从 workspace 中物理移除。

## 3. 解冻条件

任一冻结对象解冻前必须补齐：

1. **明确消费方**：具体应用到模块/页面，不接受“以后可能用”；
2. **验收标准**：功能、性能、兼容性（浏览器/WebGL 版本）与回滚方案；
3. **构建与发布归属**：进入哪个流水线、产出什么制品、谁维护依赖升级；
4. **安全与许可证复核**：依赖漏洞扫描、第三方素材授权。

未满足以上条件时，新增需求一律走现有模块或新建独立模块，不在这三个冻结对象上叠加。

## 4. 关联记录

- 待办入口：[pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) F-05/F-07/F-09
- 服务与端口：[service-catalog.md](../service-catalog.md)
