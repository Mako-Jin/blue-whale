# whale-file 能力扩展（批次 25：版本列表最新标识与元数据补全）

## 一、范围与决策

版本列表只按版本号倒序返回，调用方需要自行比较首条判断哪个是当前内容；上传/回滚/找回的返回
同样缺少「是否最新」信息。本批次为版本结果补充 `latest` 标识，避免前端二次推断。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 最新标识 | ✅ | `FileVersionResult`/`FileVersionResponse` 新增 `latest` |
| 列表语义 | ✅ | `versions()` 首条（版本号最大且未删除）为 `true` |
| 写操作返回 | ✅ | 上传新版本、回滚生成的新版本均为 `true` |
| 找回返回 | ✅ | 与恢复后版本组的最大值比较后判定 |
| 已删除列表 | ✅ | 恒为 `false` |
| 数据库变更 | ❌ | 无，纯结果投影变更 |
| 接口路由/权限 | ❌ | 不新增 |

## 二、设计

- `FileVersionResult` 在 `version` 之后新增 `boolean latest`；`toVersionResult(asset)` 默认
  `false`，新增 `toVersionResult(asset, latest)` 重载供显式标记；
- `versions()`：仓储已按版本号倒序且排除逻辑删除行，按索引标记首条 `latest = true`；
- `uploadVersion`/`rollback`：落库返回的新版本即组内最大版本，标记 `true`；
- `restoreVersion`：恢复后重新读取版本组首条，与目标版本号一致时标记 `true`；
- `deletedVersions`：已删除版本不可能是当前内容，保持 `false`；
- Web 层 `FileVersionResponse` 同步新增 `Boolean latest`，MapStruct 同名自动映射。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用层 | 列表首条 `latest=true`、次条 `false`；上传新版本 `latest=true`；回滚结果 `latest=true`；找回结果按版本组判定 |
| Web | 版本列表响应包含 `latest` 字段且首条为 `true`；既有上传/回滚/找回用例保持通过 |
| 回归 | 删除、版本下载、保留清理等既有版本用例保持通过 |

## 四、完成情况

- `FileVersionResult` 在 `version` 之后新增 `boolean latest`，`FileVersionResponse` 同步新增
  `Boolean latest`，MapStruct 同名自动映射；
- `toVersionResult(asset)` 默认 `false`，新增 `toVersionResult(asset, latest)` 重载；
- `versions()` 按仓储返回顺序（版本号倒序、排除逻辑删除行）标记首条 `latest = true`；
- `uploadVersion`/`rollback` 返回的新版本标记 `true`；
- `restoreVersion` 恢复后重新读取版本组首条，与目标版本号一致时标记 `true`；
- `deletedVersions` 恒为 `false`；
- 无数据库变更、无新增接口路由与权限码。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
376 项单测零失败（本轮为既有用例补充断言，未新增用例），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 无。本轮改动仅涉及结果投影与既有断言，一次通过。

## 七、待做项

- 清理/回滚审计（需新增表与迁移脚本）。
- 定时任务分布式互斥（回收站清理、孤儿对账、版本清理多实例并发）。
