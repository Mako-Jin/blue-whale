# whale-file 能力扩展（批次 8：领域事件）

## 一、范围与决策

批次 1~7 已完成存储路由与回收站、重命名/移动、指标与健康检查、孤儿对象对账、分享链接、
批量删除与打包下载、上传内容魔数校验、租户/桶级配额。本批次落地路线图阶段 3 的第一项：
**领域事件（锁定 / 解锁 / 删除 / 恢复）**。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 统一事件载荷 | ✅ | `FileAssetDomainEvent`（fileId、changeType、tenantId、bucket、objectKey、uploaderId、operatorId） |
| 锁定 / 解锁事件 | ✅ | 仅状态实际变化时发布，重复调用不发事件 |
| 删除事件 | ✅ | 单删与批量删除中成功的条目逐条发布；失败条目不发 |
| 恢复事件 | ✅ | 实际恢复才发布；已恢复的幂等调用不发 |
| 发布端口 | ✅ | 直接复用 `common-domain` 的 `DomainEventPublisher`，不重复定义端口 |
| Spring 事件适配器 | ✅ | 基础设施层桥接 `ApplicationEventPublisher`，监听器异常只告警不反噬主流程 |
| 上传 / 分享 / 打包事件 | ❌ | 路线图只要求四项；避免一次铺太大，确有消费方时再加 |
| 事务性 outbox / MQ | ❌ | 进程内同步事件，无投递保证；跨进程可靠投递列为待做 |

**边界与语义**：事件描述「已经发生的业务事实」，只承载标识与必要上下文，不携带原始聚合或框架类型；
发布发生在业务方法内、事务提交前，消费方若需读取已提交状态应使用
`@TransactionalEventListener(phase = AFTER_COMMIT)`。事件为**进程内、尽力而为**：适配器捕获监听器异常
仅记录 WARN，业务操作不因通知失败而回滚或报错。

## 二、设计

### 2.1 领域

新增 `com.bluewhale.file.domain.file.event.FileAssetDomainEvent`：

| 字段 | 说明 |
| --- | --- |
| `fileId` | 文件主键 |
| `changeType` | `LOCKED` / `UNLOCKED` / `DELETED` / `RESTORED` |
| `tenantId` | 发布时的租户上下文；无租户态为 `null` |
| `bucket` / `objectKey` | 定位物理对象，供审计与缓存失效使用 |
| `uploaderId` | 文件上传者 |
| `operatorId` | 本次操作人；内部锁定/解锁无操作人时为 `null` |

### 2.2 应用层

`FileInfoServiceImpl` 注入 `DomainEventPublisher`（common-domain 端口），私有方法
`publishFileEvent(asset, changeType, operatorId)` 统一构造载荷；接入点：

| 入口 | 时机 | 类型 |
| --- | --- | --- |
| `delete` | `deleteById` 成功后 | `DELETED` |
| `batchDelete` → `deleteOne` | 每条删除成功后 | `DELETED` |
| `restore` | `restoreWithQuotaCheck` 返回成功 | `RESTORED` |
| `lock` | `save` 成功后 | `LOCKED` |
| `unlock` | `save` 成功后 | `UNLOCKED` |

锁定/解锁的聚合方法返回 `false`（状态未变）或恢复命中幂等分支时，不发布事件。

### 2.3 基础设施

新增 `com.bluewhale.file.infrastructure.event.SpringDomainEventPublisher`（`@Component`），
把 `DomainEventPublisher#publish` 桥接到 Spring `ApplicationEventPublisher`；
监听器抛异常时记录 WARN（含事件类型与 fileId）后吞掉，保证通知失败不影响业务结果。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 领域 | 事件携带全部字段；`changeType` 为空时构造失败 |
| 应用 | 删除/批量删除（含失败条目）发布 `DELETED`；恢复发布 `RESTORED`；已恢复幂等不发；锁定/解锁发布且重复调用不发 |
| 基础设施 | 事件原样透传 `ApplicationEventPublisher`；监听器抛异常不影响调用方 |

## 四、完成情况

- 领域：新增 `FileAssetDomainEvent`（record，含 `LOCKED`/`UNLOCKED`/`DELETED`/`RESTORED` 四种变更类型），
  构造时校验变更类型非空；新增 `domain/file/event` 子包说明。
- 应用：`FileInfoServiceImpl` 注入 common-domain 的 `DomainEventPublisher`，新增
  `publishFileEvent` 统一构造载荷；`delete`、`batchDelete`（逐条成功项）、`restore`、
  `lock`、`unlock` 在写操作成功后发布；状态未变与幂等恢复不发布。
- 基础设施：新增 `SpringDomainEventPublisher`（`@Component`）桥接 Spring `ApplicationEventPublisher`，
  监听器异常记录 WARN 后吞掉，不影响业务结果。
- 测试：领域 `FileAssetDomainEventTest` 2 项；基础设施 `SpringDomainEventPublisherTest` 2 项；
  应用新增 3 项（批量删除仅成功项发布、锁定/解锁各一次且重复调用不发、恢复发布且幂等不发）。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
284 项单测零失败（批次 7 为 277，本轮新增 7 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 测试首轮编译失败：新增 `publishedEvents` 字段时漏了 `java.util.ArrayList` 导入 →
  补齐导入后通过。
- 设计期规避的隐患（非上线后发现）：
  - 监听器异常若向上传播，删除/恢复已落库却让调用方收到失败并可能重试 → 适配器统一捕获并降级为告警；
  - 批量删除若在循环外统一发布，失败条目也会产生事件 → 事件下沉到 `deleteOne` 成功分支逐条发布；
  - 锁定/解锁与恢复的幂等分支若不提前返回，会重复发布事实事件 → 仅在状态实际变化或恢复成功时发布。

## 七、待做项

- 事务性 outbox / MQ 可靠投递与跨服务消费。
- 上传、分享、打包下载、回收站物理清理事件。
- 事件消费方：审计落库、缓存失效、搜索索引同步。
- 路线图其余项：文件版本、存储迁移工具、CDN 地址、多段 Range、存储加密、策略热更新、防盗链/限流。
