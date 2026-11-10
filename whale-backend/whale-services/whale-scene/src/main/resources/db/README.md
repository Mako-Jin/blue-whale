·# whale-scene 数据库脚本约定

- 建表走 Flyway 版本化脚本（`V1__baseline.sql`），不提供手工 `init.sql`（REQ-SEC-022）。
- 脚本禁止 `DROP` / `TRUNCATE` / `DELETE`；种子数据使用 `INSERT ... WHERE NOT EXISTS`，可重复执行。
- 业务表统一带 `tenant_id`，由 starter 的租户行插件自动追加过滤与写入。
- `sc_node_binding.target_path` 只允许平台内路径（`/` 开头）或 `http/https` 外链；服务层做白名单与内网地址校验。
- 3D 相关字段（`coordinate_z`）在 M1/M2 只保存不使用，M3 解冻评审通过后再启用。
