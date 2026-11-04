# whale-crm 数据库脚本约定

- 建表走 Flyway 版本化脚本（`V1__baseline.sql`），不提供手工 `init.sql`（REQ-SEC-022）。
- 脚本禁止 `DROP` / `TRUNCATE` / `DELETE`；种子数据使用 `INSERT ... WHERE NOT EXISTS`。
- 业务表统一带 `tenant_id`，由 starter 的租户行插件自动追加过滤与写入。
- 需求门禁（ADR-003）通过前，本库只建表、不写入业务数据；业务 CRUD 从 M4 开始。
- 客户手机号 / 邮箱保存原值但输出与审计必须脱敏（REQ-SEC-027）。
- 合同相对方只保存 `crm_customer.id` 引用，不复制客户主数据；供应商主数据不进入本库。
