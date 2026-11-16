# whale-file 能力扩展（批次 29：版本组版本号并发唯一约束）

## 一、范围与决策

`uploadVersion` 与 `rollback` 的新版本号由「组内最大版本号 + 1」计算，两次并发操作可能算出
相同版本号并各自写入成功，产生同组重复版本：版本列表出现两条同号版本、回滚/删除按版本号
定位时只能命中其中一条。应用层没有串行化手段（配额锁只覆盖配额统计），本批次用数据库唯一
约束兜底，并让冲突方自动重算重试。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 唯一约束 | ✅ | `uk_parent_version(parent_id, version)`，迁移 `V8__file_version_unique.sql` |
| 冲突重试 | ✅ | `uploadVersion` / `rollback` 冲突后重读最新版本号重试，最多 3 次 |
| 普通索引 | ✅ 替换 | 唯一索引完全覆盖 `idx_parent_version`，迁移中一并移除 |
| 根版本 | ✅ 不受约束 | `parent_id` 为 NULL，MySQL 唯一索引允许多个 NULL |
| 逻辑删除行 | ✅ 纳入约束 | 已删除版本占用的序号不复用，与「最大版本号查询含已删除行」一致 |
| `init.sql` | ✅ 同步 | 手工初始化基线改为唯一索引 |
| 权限码 / 接口 | ✅ 不变 | 无新增接口与权限码 |

**不做**：版本号重排与去重脚本。存量若已有重复版本号，迁移会失败并暴露问题，由运维按文档
提供的查重 SQL 人工处理，不在迁移里自动改号（改号会让历史回滚/下载引用失真）。

## 二、设计

### 2.1 迁移（file 库 V8）

- 幂等：先查 `information_schema.STATISTICS`，`uk_parent_version` 已存在则跳过；
- 若普通索引 `idx_parent_version` 存在，单条
  `ALTER TABLE ... ADD UNIQUE INDEX ..., DROP INDEX ...` 完成替换；
- 迁移前置检查（写入脚本注释，供上线前执行）：

```sql
SELECT parent_id, version, COUNT(*) FROM file_info
WHERE parent_id IS NOT NULL GROUP BY parent_id, version HAVING COUNT(*) > 1;
```

- `db/init.sql` 同步把 `KEY idx_parent_version` 改为 `UNIQUE KEY uk_parent_version`，保证手工初始化
  的空库与迁移后的结构一致；`V1__baseline.sql` 属历史基线，不修改。

### 2.2 应用层冲突重试

- 新增私有方法 `registerVersionWithRetry(...)`：每次尝试都重新读取组内最新版本号；
- 捕获 `DuplicateKeyException`（Spring 对唯一键冲突的翻译）后重试，最多
  `MAX_VERSION_CONFLICT_ATTEMPTS = 3` 次（含首次）；重试耗尽抛出原异常，由调用方按既有补偿
  逻辑删除已写入的物理对象；
- 物理对象在进入重试前已落盘，重试只重写元数据，不重复写存储；
- 非唯一键冲突的异常（配额超限等）原样上抛，不进入重试。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用层 | 首次冲突后重算版本号成功（版本号 +1，仅写一次存储，不补偿删除） |
| 应用层 | 连续冲突 3 次后抛出原始异常并补偿删除物理对象 |
| 迁移 | 脚本命名/递增与无破坏性语句校验通过（既有用例覆盖） |
| 回归 | file 模块全量单测通过 |

## 四、完成情况

- 迁移：新增 `V8__file_version_unique.sql`，幂等创建 `uk_parent_version(parent_id, version)`，
  同一 `ALTER` 中移除被覆盖的普通索引 `idx_parent_version`；脚本注释内附存量查重 SQL；
- `db/init.sql` 同步为 `UNIQUE KEY uk_parent_version (parent_id, version)`，手工初始化空库与迁移后结构一致；
- 应用层：新增 `FileInfoServiceImpl#registerVersionWithRetry`，`uploadVersion` 与 `rollback` 改为调用它；
  每次尝试重读组内最新版本号，`DuplicateKeyException` 最多尝试 3 次，耗尽后原样抛出并由既有
  补偿逻辑删除已写入对象；非唯一键异常不重试；
- 接口、权限码、配置项无变化。

## 五、验证结果

- `mvn -o test`（工作目录 `whale-backend/whale-services/whale-service-file`）→
  13 个模块 BUILD SUCCESS，383 项单测零失败，checkstyle 0 violation；
- 单测分布：domain 44、infrastructure 65、application 160、plugins-local 12、
  plugins-minio 10、architecture 4、web 88；
- 本轮新增 2 项：`shouldRetryUploadVersionOnVersionConflict`（冲突后重算版本号、仅写一次存储）、
  `shouldCompensateWhenVersionConflictPersists`（3 次冲突后抛原始异常并补偿删除对象）；
- 迁移脚本通过 `FileFlywayMigrationScriptTest` 的命名递增与无破坏性语句校验。

## 六、已修正问题项

- 设计初稿考虑在滚动发布期间保留普通索引 `idx_parent_version`，避免旧版本实例与新索引并存时
  出现差异；核对后确认唯一索引以 `parent_id` 为前缀，完全覆盖普通索引的查询能力，
  保留两份只会增加写入开销，故在迁移中一并移除。
- 未采用「迁移中自动去重/重排版本号」：改号会让既有回滚、下载与审计记录中的版本引用失真，
  存量重复数据交由运维按脚本注释的查重 SQL 人工处理。

## 七、待做项

- 上线前在目标环境执行查重 SQL，确认无重复版本号后再执行 V8 迁移；
- 版本超限清理与孤儿对账的运行审计（批次 28 待做项延续）；
- 既有候选：outbox 可靠领域事件、分块加密与密钥轮换、异步打包下载。

