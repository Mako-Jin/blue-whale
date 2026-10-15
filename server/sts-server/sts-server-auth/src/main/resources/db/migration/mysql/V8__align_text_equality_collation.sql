-- ============================================================================
-- V8 · 文本相等的语义：把 MySQL 的默认比较语义对齐到"区分大小写"（MySQL 8 方言）
--
-- 与 db/migration/h2/V8__*.sql、db/migration/dameng/V8__*.sql 一一对应，
-- 但**本片只有 MySQL 有语句** —— 另两个方言的默认比较语义已经是目标值，
-- 那两个文件里写的是"为什么这里没有语句"。见 docs/decisions/0065。
--
-- 【为什么是 V8】Flyway 的 locations 是全模块共享的 classpath:db/migration/<vendor>，
-- 版本号**全仓唯一**：V1 日志域 / V2 行为事件域 / V3 权限域建表 / V4 审计域 /
-- V5 口令加固 / V6 日志加列 / V7 事件加列，本片接 V8。见 docs/decisions/0009。
--
-- 【为什么要改】V3 给每张表**显式**写了 `COLLATE = utf8mb4_0900_ai_ci`，
-- 那是"不区分大小写、也不区分重音"的排序规则。于是"两个文本算不算相等"
-- 这个从来没有被回答过的问题，就由三个方言各自的默认值代答了 —— 而它们互不相同。
-- 当次实测（同一段 SQL 在两台引擎上跑，见 docs/decisions/0065）：
--
--   探针                                  H2 2.4.240   _ai_ci   _as_cs   _bin
--   已存 a@b.com，查 = 'A@B.COM'              0          1        0       0
--   已存 a@b.com，查 = 'a@b.com '（尾空格）    0          0        0       1
--   已存 a@b.com，查 = 'á@b.com'              0          1        0       0
--   WHERE username = 'Admin'（已存 admin）     0          1        0       0
--   唯一索引：已有 admin 再插 Admin          允许       拒绝     允许    允许
--
-- 落在用户可见处，不是纸面差异：
--   · MySQL 上 "Login" 与 "login" 是同一个登录名、A@b.com 与 a@b.com 是同一个邮箱；
--     H2 上它们是两个。而登录正是 `WHERE tenant_id = ? AND username = ?`
--     （AuthService#login / UserMapper#selectAnyByTenantAndUsername）——
--     同一串请求在两个 profile 下得到不同答案。
--
-- 【为什么选 _as_cs，而不是名字更像"二进制比较"的 _bin】
--   `_bin` 是遗留排序规则、**PAD SPACE** ⇒ 'a@b.com ' = 'a@b.com' 成立（上表第 2 行 = 1），
--   而 H2 认为它们不同。`_as_cs` 是 NO PAD ⇒ 上表四格全对。
--   **这一条是量出来的，不是从名字推的** —— 名字更像"二进制"的那个反而错一格。
--
-- 【为什么不直接改 V3】0009：迁移脚本一旦在真实环境执行过就不可修改
--   （校验和不匹配会让启动直接失败）。所以 V3 与 V5 里的表选项保持原样，
--   由本片在它们之后覆盖一层。
--
-- 【零接口形状改动】纯 DDL：不改任何列、不新增错误码、不动 OpenAPI 契约。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 13 张 bw_auth_* 表逐张转换
--
-- 用整表 CONVERT（而不是逐列改 COLLATE）有两个理由：
--   1. 它会同时改**表默认值**与**已有列** ⇒ 将来新增的列自动继承 as_cs，
--      而不是悄悄退回库级默认值；
--   2. "一个库一个等价关系"比"哪几列特殊"更容易守 ——
--      本域没有一处文本比较是**特意**要大小写不敏感的。
--
-- 日志 / 行为事件 / 审计三域的表不在此列：那三张表上**没有任何唯一索引**、
-- 也没有按文本列的等值查询，比较语义对它们无从生效。
-- ---------------------------------------------------------------------------
ALTER TABLE bw_auth_tenant CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_user CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_organization CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_role CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_user_group CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_resource CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_rel_role_member CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_rel_role_resource CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_rel_role_resource_instance CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_rel_org_user CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_rel_user_group_member CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_refresh_token CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
ALTER TABLE bw_auth_password_history CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
