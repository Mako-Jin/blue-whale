import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志「追加写」数据库守卫（等保 2.0：审计记录应受到保护，避免被删除、修改或覆盖）。
 *
 * <p>做法：给每个含审计/观测日志表的库建 {@code BEFORE UPDATE} / {@code BEFORE DELETE}
 * 触发器，命中即 {@code SIGNAL SQLSTATE '45000'} 报错。静态门禁
 * {@code scripts/security/check-audit-append-only.py} 只管「代码里不再写」，本工具管
 * 「任何人，包括手工 SQL 与 DBA 客户端，都改不动」——两道闸门缺一不可。</p>
 *
 * <p>为什么用触发器而不是只收账户权限：各库的 application 连接串当前是同一个账号，
 * 逐个库 REVOKE 要改 N 份配置；触发器与账号无关，新库跑一遍即可补齐。
 * 生产环境建议再叠加「应用账号对日志表只有 INSERT/SELECT」的授权（见交付文档）。</p>
 *
 * <p><b>范围只到 audit_log</b>：api_log / outbound_log / frontend_log / behavior_log 属运维观测
 * 数据，有明确的保留期清理任务（{@code LogRetentionCleaner} 按 cron 删除过期行），
 * 给它们加 DELETE 守卫会让保留期失效、表无限膨胀。audit_log 是合规证据，且归档任务
 * 只导出不删除，因此只对它做硬保护。需要临时保护某张表时用 {@code --tables=} 指定。</p>
 *
 * <p>幂等：先 {@code DROP TRIGGER IF EXISTS} 再建，可反复执行。
 * 内置负向验证：建完后对每张表发一条「把首列赋给自己」的 UPDATE（不改变任何数据），
 * 触发器必须拦住；拦不住即判 FAIL 并以退出码 1 结束。</p>
 *
 * <p>用法（仓库根执行，JDK 单文件源码启动）：</p>
 * <pre>
 *   DB_USER=root DB_PWD=*** java -cp &lt;mysql-connector-j.jar&gt; scripts/security/AuditAppendOnlyGuard.java
 *   ... --dry-run            只打印将要执行的 DDL
 *   ... --rollback           移除守卫（仅在需要做数据修复时临时使用）
 *   ... --schemas=a,b        只处理指定库
 *   ... --tables=audit_log   指定保护表（默认仅 audit_log，可逗号分隔）
 * </pre>
 *
 * <p>退出码：0 = 全部库已加固且负向验证通过；1 = 有库未加固或验证失败。</p>
 *
 * @author BlueWhale
 */
public class AuditAppendOnlyGuard {

    /** 默认保护的 append-only 表：合规证据表，归档只导出不删除 */
    private static final List<String> DEFAULT_TABLES = List.of("audit_log");

    /** 需要拦住的写动作 */
    private static final List<String> ACTIONS = List.of("UPDATE", "DELETE");

    public static void main(String[] args) throws Exception {
        boolean dryRun = hasFlag(args, "--dry-run");
        boolean rollback = hasFlag(args, "--rollback");
        List<String> onlySchemas = option(args, "--schemas");
        List<String> targetTables = option(args, "--tables") != null ? option(args, "--tables") : DEFAULT_TABLES;

        String user = System.getenv("DB_USER");
        String pwd = System.getenv("DB_PWD");
        if (user == null || user.isBlank() || pwd == null) {
            System.err.println("缺少数据库口令：请先设置 DB_USER / DB_PWD 环境变量（口令不写死在脚本里）");
            System.exit(2);
        }
        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String url = "jdbc:mysql://" + host + ":" + port + "/information_schema"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8";

        int failures = 0;
        try (Connection conn = DriverManager.getConnection(url, user, pwd);
             Statement st = conn.createStatement()) {

            Map<String, List<String>> schemaTables = discover(st, targetTables);
            if (onlySchemas != null) {
                schemaTables.keySet().retainAll(onlySchemas);
            }
            System.out.println("发现 " + schemaTables.size() + " 个库含 " + targetTables + "，"
                    + (rollback ? "，模式=移除守卫" : dryRun ? "，模式=演练" : "，模式=加固"));

            for (Map.Entry<String, List<String>> entry : schemaTables.entrySet()) {
                String schema = entry.getKey();
                List<String> tables = entry.getValue();
                try {
                    if (rollback) {
                        for (String table : tables) {
                            for (String action : ACTIONS) {
                                execute(st, dropTriggerSql(schema, table, action), dryRun);
                            }
                        }
                        System.out.println("[ROLLBACK] " + schema + " 移除 " + tables.size() + " 张表的守卫");
                        continue;
                    }
                    for (String table : tables) {
                        for (String action : ACTIONS) {
                            execute(st, dropTriggerSql(schema, table, action), dryRun);
                            execute(st, triggerDdl(schema, table, action), dryRun);
                        }
                    }
                    if (dryRun) {
                        System.out.println("[DRY] " + schema + " 将为 " + tables + " 建 BEFORE UPDATE/DELETE 守卫");
                        continue;
                    }
                    String failed = verify(conn, st, schema, tables);
                    if (failed == null) {
                        System.out.println("[PASS] " + schema + " 守卫生效且负向验证通过（" + tables.size() + " 张表）");
                    } else {
                        failures++;
                        System.out.println("[FAIL] " + schema + " " + failed);
                    }
                } catch (Exception ex) {
                    failures++;
                    System.out.println("[FAIL] " + schema + " " + ex.getMessage());
                }
            }
        }

        System.out.println(failures == 0
                ? "PASS：全部库的审计表已不可改写（UPDATE/DELETE 均被触发器拦截）"
                : "FAIL：存在 " + failures + " 个库未通过");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** 发现所有含目标表的库及各自的表清单 */
    private static Map<String, List<String>> discover(Statement st, List<String> tables) throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String inList = String.join(",", tables.stream().map(t -> "'" + t + "'").toList());
        String sql = "SELECT table_schema, table_name FROM information_schema.tables "
                + "WHERE table_type = 'BASE TABLE' AND table_name IN (" + inList + ") "
                + "AND table_schema NOT IN ('information_schema','mysql','performance_schema','sys') "
                + "ORDER BY table_schema, table_name";
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
            }
        }
        return result;
    }

    /** 触发器名：库内唯一，前缀固定便于识别与批量移除 */
    private static String triggerName(String table, String action) {
        return "trg_" + table + "_no_" + action.toLowerCase();
    }

    private static String dropTriggerSql(String schema, String table, String action) {
        return "DROP TRIGGER IF EXISTS `" + schema + "`.`" + triggerName(table, action) + "`";
    }

    /** 建 DDL：SIGNAL 作为触发器单语句体，避免 BEGIN...END 带来的分号切分问题 */
    private static String triggerDdl(String schema, String table, String action) {
        return "CREATE TRIGGER `" + schema + "`.`" + triggerName(table, action) + "` BEFORE " + action
                + " ON `" + schema + "`.`" + table + "` FOR EACH ROW SIGNAL SQLSTATE '45000'"
                + " SET MESSAGE_TEXT = '" + table + " is append-only, " + action + " is forbidden'";
    }

    /**
     * 负向验证：对每张表发一条「首列赋给自己」的 UPDATE（不改变任何数据），触发器必须拦住。
     *
     * @return {@code null} 表示通过，否则返回失败原因
     */
    private static String verify(Connection conn, Statement st, String schema, List<String> tables) throws Exception {
        for (String table : tables) {
            boolean hasRow;
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM `" + schema + "`.`" + table + "` LIMIT 1")) {
                hasRow = rs.next();
            }
            if (hasRow) {
                String column = firstColumn(conn, schema, table);
                boolean blocked;
                try {
                    st.executeUpdate("UPDATE `" + schema + "`.`" + table
                            + "` SET `" + column + "` = `" + column + "` LIMIT 1");
                    blocked = false;
                } catch (Exception ex) {
                    blocked = true;
                }
                if (!blocked) {
                    return table + " 的 BEFORE UPDATE 守卫未生效（UPDATE 未被拦截）";
                }
            }
            // BEFORE x 触发器按行触发，空表无法行为验证，退化为「触发器存在性」校验
            for (String action : ACTIONS) {
                if (!triggerExists(st, schema, table, action)) {
                    return table + " 缺少 BEFORE " + action + " 守卫";
                }
            }
        }
        return null;
    }

    /** 取首列名（通常是主键 id），用于构造无害的探测性 UPDATE */
    private static String firstColumn(Connection conn, String schema, String table) throws Exception {
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "id";
            }
        }
    }

    private static boolean triggerExists(Statement st, String schema, String table, String action) {
        String sql = "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = '" + schema
                + "' AND event_object_table = '" + table + "' AND event_manipulation = '" + action + "'";
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 执行一条 DDL；演练模式只打印 */
    private static void execute(Statement st, String sql, boolean dryRun) throws Exception {
        if (dryRun) {
            System.out.println("  " + sql);
            return;
        }
        st.execute(sql);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> option(String[] args, String name) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) {
                return List.of(arg.substring(name.length() + 1).split(","));
            }
        }
        return null;
    }
}
