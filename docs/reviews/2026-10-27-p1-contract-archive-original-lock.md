# P1 检查报告：合同归档原件未锁定

- 日期：2026-10-27
- 范围：`backend/whale-services/whale-archive`
- 关联：台账序号 22 遗留、Wave 5.53 M2「归档后文件 status=2、锁定拦截删除」、Wave 5.61 合同归档推送

## 1. 问题

合同归档通过 `POST /archive/internal/items` 推送业务快照，`ArchiveIngestService` 只上传快照并落库，没有调用文件服务锁定接口：

1. `archive_file` 中 `file_role=ORIGINAL` 引用的合同原件仍为 `status=1`（正常），可被文件中心删除；
2. 工作流同步链路（`ArchiveSyncService`）在入档时已锁定快照与附件，合同链路与之口径不一致；
3. 条目确认归档（`ArchiveItemService#confirm`）也只更新状态，没有兜底锁定，历史「待整理」条目确认后仍可删除原件。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `.../service/ArchiveIngestService.java` | 新增 `ArchiveFileMapper` 依赖；新入档在落库前调用 `fileClient.lock` 锁定快照与原件；重复推送时读取既有条目的文件引用补锁（引用缺失时回退锁定 `snapshotFileId`） |
| `.../service/ArchiveItemService.java` | `confirm` 在状态更新成功后调用 `lockFiles(itemId)`，锁定条目下全部文件；锁定失败抛出异常，事务回滚，条目保持「待整理」可重试 |
| `src/test/.../ArchiveIngestServiceTest.java` | 新增 4 用例：快照+原件先锁后落库、无原件仅锁快照、锁定失败不落库、重复推送补锁 |
| `src/test/.../ArchiveItemServiceTest.java` | 新增 2 用例：确认归档锁定条目文件、锁定失败抛出异常 |

## 3. 行为

1. 合同 `archive()` 推送快照 → archive 上传快照文件并生成 `archive_file`（SNAPSHOT/ORIGINAL）→ 先锁定两类文件，再持久化条目与台账；
2. 锁定失败：异常返回合同侧，合同状态不落 `ARCHIVED`，archive 不产生新条目，业务可重试；
3. 重复推送：`(sourceType,sourceId)` 命中既有条目时补锁其文件引用后幂等返回，可修复历史未锁定条目；
4. 档案人员确认归档：状态更新成功后再次幂等锁定该条目全部文件，覆盖修复前创建的「待整理」条目；
5. 文件服务锁定接口幂等（`status=2`），重复调用不改变文件内容，仅保证删除拦截。

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-archive -am test
```

- `whale-archive` 全量 `45` 用例，`0` 失败、`0` 跳过；Checkstyle `0`；
- 新增用例覆盖锁定顺序（`InOrder` 验证 lock 早于 persist）、无原件分支、锁定失败不落库、重复推送补锁、确认归档锁定与失败传播；
- 依赖模块 `whale-tools` 12、`whale-security` 34、`whale-spring-boot-starter` 91 用例同批通过。

## 5. 边界

- 修复前已 `ARCHIVED` 的条目不会自动补锁：若仍可由业务重推同一 `(sourceType,sourceId)`，会命中补锁分支；否则需运维调用文件中心 `POST /file/internal/{id}/lock` 手工补锁；
- 文件服务不可用时，重复推送与确认归档会失败（fail-closed），避免在无法保证不可变时继续声明归档完成；
- 锁定为外部调用，事务回滚不能撤销已成功的部分锁定；重试幂等，最坏情况为文件提前锁定但条目仍未归档；
- 未新增数据库字段或迁移，锁定状态以文件中心 `file_info.status=2` 为准。
