# 冻结模块与共享包清单（F-07 / F-09 结案；F-05 已解冻）

> 结论日期：2026-10-28（Wave 5.73 更新）
> 状态：**冻结（代码保留、不扩张、不构建、不路由）**
> 原则：冻结不等于删除。保留代码与历史，避免破坏可能的外部引用与仓库考古；但不再投入功能开发、不纳入默认构建/部署路径。

## 1. 冻结清单

| 编号 | 对象 | 类型 | 冻结原因 | 处置 |
|---|---|---|---|---|
| F-07 | `whale-frontend/games/whale-games-core` | 前端共享包 | 主循环与资源清理未实现，且无游戏前端应用/消费方 | 标记冻结；不升级依赖、不新增功能；保留在仓库供后续游戏项目复用 |
| F-09 | `whale-frontend/shared-packages/whale-common-three` | 前端共享包 | WebGL 渲染未验收，控制器/后处理/性能监控未封装，无消费方 | 标记冻结；不升级依赖、不新增功能；后续需要 3D 时重新立项 |

> **F-05 已于 2026-10-28 解冻**（见 §4 解冻记录），不再列入本清单。

## 2. 影响面与验证

| 冻结对象 | 已做变更 | 验证 |
|---|---|---|
| `whale-games-core` | 仅在 [service-catalog](../service-catalog.md) 标注冻结 | 检索确认无前端/后端消费方 |
| `whale-common-three` | 仅在 [service-catalog](../service-catalog.md) 标注冻结 | 检索确认无前端/后端消费方 |

> 说明：仓库内仍有 `package.json`/workspace 条目，这是为了保留历史与可追溯性；冻结口径是“不消费、不升级、不构建为交付物”，不是从 workspace 中物理移除。

## 3. 解冻条件

任一冻结对象解冻前必须补齐：

1. **明确消费方**：具体应用到模块/页面，不接受“以后可能用”；
2. **验收标准**：功能、性能、兼容性（浏览器/WebGL 版本）与回滚方案；
3. **构建与发布归属**：进入哪个流水线、产出什么制品、谁维护依赖升级；
4. **安全与许可证复核**：依赖漏洞扫描、第三方素材授权。

未满足以上条件时，新增需求一律走现有模块或新建独立模块，不在这两个冻结对象上叠加。

## 4. 解冻记录

### F-05 `whale-config` 解冻（Wave 5.73，2026-10-28）

- **定位**：业务配置管理服务（与 Nacos 互补——Nacos 管服务装配/基础设施配置，whale-config 管业务应用配置：配置项 CRUD / 版本历史 / 回滚 / 租户隔离）。
- **解冻条件对照**：① 消费方——admin「业务配置管理」页（菜单 35）+ 经网关 `/config/**` 的业务 API；② 验收标准——CRUD/版本/回滚实机检查 + 单测，见 [检查报告](../reviews/2026-10-28-wave5.73-whale-config-and-v06-alert-evidence.md)；③ 构建归属——`.github/workflows/docker-deploy.yml` matrix 恢复 `whale-registry/whale-config`；④ 安全——权限 246–250（`sys:config:*`）仅授 `tenant_admin`，审计/接口日志落 `bluewhale_config.audit_log`/`api_log`。
- **恢复的冻结动作**：网关 `/config/**` 路由、CI 镜像 matrix 条目、两份 Prometheus 配置的 `whale-config` job。

## 5. 关联记录

- 待办入口：[pending-issues-2026-10-27.md](../plans/pending-issues-2026-10-27.md) F-05/F-07/F-09
- 服务与端口：[service-catalog.md](../service-catalog.md)
