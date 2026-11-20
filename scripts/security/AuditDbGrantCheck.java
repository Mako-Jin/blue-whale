import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 审计表「最小权限」核查（等保 2.0 8.1.4.3 b：审计记录应受到保护，避免未预期的删除、修改或覆盖）。
 *
 * <p>现状：{@code audit_log} 的不可改写靠<b>触发器</b>保证（{@code AuditAppendOnlyGuard}），
 * 应用账号对表仍有 {@code UPDATE}/{@code DELETE} 权限。触发器能拦住改写，但拦住之后
 * {@code ALTER TABLE ... DROP TRIGGER} 仍然可以拆掉守卫——权限层收口是第二道防线，
 * 让应用账号连「改/删/改表结构」的语法都发不出去。</p>
 *
 * <p>本工具不做任何写操作，只回答两个问题：</p>
 * <ol>
 *   <li>指定应用账号对每个库的 {@code audit_log} 到底有哪些权限（全局 ∪ 库级 ∪ 表级生效值）；</li>
 *   <li>没达到「只 SELECT + INSERT」时，<b>该 REVOKE 哪一级</b>——表级能精确收，库级收完要逐表补授，
 *       两者的整改代价完全不同，不能混为一谈。</li>
 * </ol>
 *
 * <p>为什么一定要区分级别：MySQL 权限是<b>累加</b>的，库级 {@code UPDATE} 无法用表级
 * {@code REVOKE} 抵消。账号若持库级 UPDATE，只能先收库级（影响该库所有表）再逐表补授，
 * 属于授权模型调整，必须人工评估；只在表级多授的，可以一行 REVOKE 精确修掉。</p>
 *
 * <p>用法（仓库根执行，JDK 单文件源码启动）：</p>
 * <pre>
 *   DB_USER=root DB_PWD=*** java -cp &lt;mysql-connector-j.jar&gt; \
 *       scripts/security/AuditDbGrantCheck.java --app-user=whale_app
 *   ... --app-host=%          账号 host（默认 %，也可写 10.0.% 或 localhost）
 *   ... --schemas=a,b         只核查指定库
 * </pre>
 *
 * <p>退出码：0 = 全部库已收口；1 = 存在越权或缺失；2 = 用法 / 连接 / 账号不存在。</p>
 *
 * <p><b>不要挂 CI</b>：本地开发用的常是 root 或全权账号，必然 FAIL；这是交付闸门，
 * 与 {@code scripts/verify/super-admin-retirement.sql} 同类。</p>
 *
 * @author BlueWhale
 */
public class AuditDbGrantCheck {

    /** 被核查的表 */
    private static final String TARGET_TABLE = "audit_log";

    /** 应用账号对该表应有的权限（精确相等即达标） */
    private static final Set<String> ALLOWED = Set.of("SELECT", "INSERT");

    /**
     * 能改写数据或拆掉守卫的权限：{@code UPDATE}/{@code DELETE} 直接改删，
     * {@code ALTER}/{@code DROP} 能拆触发器，其余为建表侧写入口。
     *
     * <p>只把这一类算作「对 audit_log 的越权」。{@code SUPER}、{@code FILE}、{@code PROCESS}
     * 这类管理权限不是表级概念，混进来会把报告刷成 70 项，反而看不出真正的风险点；
     * 它们的作用是判断「这个账号压根不该当应用账号」，单独提示。</p>
     */
    private static final Set<String> WRITE_PRIVS = Set.of(
            "UPDATE", "DELETE", "ALTER", "DROP", "CREATE", "INDEX", "REFERENCES", "TRIGGER", "LOCK TABLES");

    /** 非权限的词，查询结果里可能出现，不算越权也不算达标 */
    private static final Set<String> IGNORED = Set.of("USAGE");

    public static void main(String[] args) throws Exception {
        String appUser = option(args, "--app-user");
        String appHost = option(args, "--app-host") != null ? option(args, "--app-host") : "%";
        List<String> onlySchemas = optionList(args, "--schemas");
        if (appUser == null || appUser.isBlank()) {
            System.err.println("缺少 --app-user：请指定被核查的应用数据库账号（本工具不猜账号名）");
            System.exit(2);
        }

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

        String grantee = "'" + appUser + "'@'" + appHost + "'";
        int failures = 0;
        try (Connection conn = DriverManager.getConnection(url, user, pwd);
             Statement st = conn.createStatement()) {

            if (!accountExists(conn, appUser, appHost)) {
                System.err.println("账号不存在：" + grantee + "（--app-user / --app-host 写错，或该账号尚未创建）");
                List<String> hosts = knownHosts(conn, appUser);
                if (!hosts.isEmpty()) {
                    System.err.println("  该用户名已存在，但 host 只有：" + String.join(", ", hosts)
                            + "；请用 --app-host=<其中一个> 重试");
                }
                System.exit(2);
            }

            Set<String> globalPrivs = loadGlobalPrivileges(conn, grantee);
            Map<String, Set<String>> schemaPrivs = loadSchemaPrivileges(conn, grantee);
            Map<String, Set<String>> tablePrivs = loadTablePrivileges(conn, grantee);

            List<String> schemas = discoverSchemas(st);
            if (onlySchemas != null) {
                schemas.retainAll(onlySchemas);
            }
            if (schemas.isEmpty()) {
                System.err.println("未发现含 " + TARGET_TABLE + " 的库，无法核查");
                System.exit(2);
            }

            System.out.println("核查账号 " + grantee + " 对 " + schemas.size() + " 个库的 "
                    + TARGET_TABLE + " 权限（应精确等于 SELECT, INSERT）");
            boolean globalWrite = containsAny(globalPrivs, WRITE_PRIVS);
            boolean globalAny = globalPrivs.stream().anyMatch(p -> !IGNORED.contains(p));
            if (globalAny) {
                System.out.println("  [警告] 该账号持有全局（*.*）权限" + (globalWrite ? "，其中含数据改写类：" + intersect(globalPrivs, WRITE_PRIVS) : "")
                        + "。全局权限靠表级 REVOKE 抵消不了，最小权限只能在账号层面实现——"
                        + "应新建专用应用账号（仅库级 SELECT/INSERT），而不是继续用这个账号。");
            }

            for (String schema : schemas) {
                Set<String> effective = new TreeSet<>(globalPrivs);
                effective.addAll(schemaPrivs.getOrDefault(schema, Set.of()));
                effective.addAll(tablePrivs.getOrDefault(schema, Set.of()));
                effective.removeAll(IGNORED);

                Set<String> violations = new TreeSet<>(effective);
                violations.removeAll(ALLOWED);
                Set<String> missing = new TreeSet<>(ALLOWED);
                missing.removeAll(effective);

                if (violations.isEmpty() && missing.isEmpty()) {
                    System.out.println("[PASS] " + schema + " 权限=SELECT,INSERT");
                    continue;
                }
                failures++;
                System.out.println("[FAIL] " + schema
                        + " 数据改写类权限=" + describeWrite(effective)
                        + (missing.isEmpty() ? "" : " 缺失=" + String.join(",", missing))
                        + (globalWrite ? "（含全局权限，见上方警告）" : ""));
                if (!globalWrite) {
                    printRemedy(schema, grantee,
                            intersect(violations, schemaPrivs.getOrDefault(schema, Set.of())),
                            intersect(violations, tablePrivs.getOrDefault(schema, Set.of())),
                            missing);
                }
            }
        }

        System.out.println(failures == 0
                ? "PASS：应用账号对 " + TARGET_TABLE + " 仅有 SELECT/INSERT（改写与拆守卫的语法都发不出去）"
                : "FAIL：存在 " + failures + " 个库未收口。表级/库级越权按上面语句整改后重跑；"
                + "若账号持有全局权限，则须改用仅库级 SELECT/INSERT 的专用应用账号");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * 账号是否存在（读 mysql.user；管理账号无权读取时跳过检查，不因此阻断核查）
     *
     * @param conn 连接
     * @param user 账号名
     * @param host 账号 host
     * @return 存在返回 {@code true}；无法判定时也返回 {@code true}
     */
    private static boolean accountExists(Connection conn, String user, String host) {
        String sql = "SELECT COUNT(*) FROM mysql.user WHERE user = ? AND host = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, host);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception ex) {
            System.out.println("  跳过账号存在性校验（无权读取 mysql.user，不影响权限核查）: " + ex.getMessage());
            return true;
        }
    }

    /**
     * 列出该用户名下所有 host（账号不存在时用于提示正确写法）
     *
     * @param conn 连接
     * @param user 账号名
     * @return host 列表；无权限或查不到时返回空列表
     */
    private static List<String> knownHosts(Connection conn, String user) {
        List<String> hosts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT host FROM mysql.user WHERE user = ? ORDER BY host")) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hosts.add(rs.getString(1));
                }
            }
        } catch (Exception ex) {
            return List.of();
        }
        return hosts;
    }

    /**
     * 全局权限（{@code ON *.*}）
     *
     * @param conn    连接
     * @param grantee 授权对象（{@code 'user'@'host'}）
     * @return 权限集合
     * @throws Exception 查询失败
     */
    private static Set<String> loadGlobalPrivileges(Connection conn, String grantee) throws Exception {
        Set<String> result = new TreeSet<>();
        String sql = "SELECT privilege_type FROM information_schema.user_privileges WHERE grantee = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, grantee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1).toUpperCase());
                }
            }
        }
        return result;
    }

    /**
     * 库级权限（{@code ON db.*}）
     *
     * @param conn    连接
     * @param grantee 授权对象
     * @return 库名到权限集合
     * @throws Exception 查询失败
     */
    private static Map<String, Set<String>> loadSchemaPrivileges(Connection conn, String grantee) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        String sql = "SELECT table_schema, privilege_type FROM information_schema.schema_privileges "
                + "WHERE grantee = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, grantee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getString(1), k -> new TreeSet<>())
                            .add(rs.getString(2).toUpperCase());
                }
            }
        }
        return result;
    }

    /**
     * 表级权限（{@code ON db.audit_log}）
     *
     * @param conn    连接
     * @param grantee 授权对象
     * @return 库名到权限集合
     * @throws Exception 查询失败
     */
    private static Map<String, Set<String>> loadTablePrivileges(Connection conn, String grantee) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        String sql = "SELECT table_schema, privilege_type FROM information_schema.table_privileges "
                + "WHERE grantee = ? AND table_name = '" + TARGET_TABLE + "'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, grantee);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getString(1), k -> new TreeSet<>())
                            .add(rs.getString(2).toUpperCase());
                }
            }
        }
        return result;
    }

    /**
     * 发现所有含目标表的业务库
     *
     * @param st 语句对象
     * @return 库名列表
     * @throws Exception 查询失败
     */
    private static List<String> discoverSchemas(Statement st) throws Exception {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT table_schema FROM information_schema.tables "
                + "WHERE table_type = 'BASE TABLE' AND table_name = '" + TARGET_TABLE + "' "
                + "AND table_schema NOT IN ('information_schema','mysql','performance_schema','sys') "
                + "ORDER BY table_schema";
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    /**
     * 打印整改语句：按「越权来自哪一级」给出对应代价最小的修法
     *
     * <p>MySQL 权限是累加的，库级授权撤不掉表级、表级 REVOKE 也抵不掉库级——
     * 所以两个级别的语句不能混写，必须分开给人看。</p>
     *
     * @param schema          库名
     * @param grantee         授权对象
     * @param schemaLevel     来自库级授权的越权
     * @param tableLevel      来自表级授权的越权
     * @param missing         缺失权限
     */
    private static void printRemedy(String schema, String grantee, Set<String> schemaLevel,
                                    Set<String> tableLevel, Set<String> missing) {
        if (!tableLevel.isEmpty()) {
            System.out.println("       表级精确收敛：REVOKE " + String.join(", ", tableLevel)
                    + " ON `" + schema + "`.`" + TARGET_TABLE + "` FROM " + grantee + ";");
        }
        if (!schemaLevel.isEmpty()) {
            System.out.println("       库级越权：REVOKE " + String.join(", ", schemaLevel)
                    + " ON `" + schema + "`.* FROM " + grantee + ";"
                    + "  -- 影响该库全部表，须评估后逐表补授");
        }
        if (!missing.isEmpty()) {
            System.out.println("       补授：GRANT " + String.join(", ", missing)
                    + " ON `" + schema + "`.`" + TARGET_TABLE + "` TO " + grantee + ";");
        }
    }

    /**
     * 判断权限集合是否与给定集合有交集
     *
     * @param privileges 权限集合
     * @param candidates 候选集合
     * @return 有交集返回 {@code true}
     */
    private static boolean containsAny(Set<String> privileges, Set<String> candidates) {
        return privileges.stream().anyMatch(candidates::contains);
    }

    /**
     * 取两个权限集合的交集
     *
     * @param privileges 权限集合
     * @param candidates 候选集合
     * @return 交集（保持权限名排序）
     */
    private static Set<String> intersect(Set<String> privileges, Set<String> candidates) {
        Set<String> result = new TreeSet<>(privileges);
        result.retainAll(candidates);
        return result;
    }

    /**
     * 只描述「数据改写类」权限，避免把管理类权限刷满一行
     *
     * @param effective 生效权限
     * @return 可读描述
     */
    private static String describeWrite(Set<String> effective) {
        Set<String> write = intersect(effective, WRITE_PRIVS);
        return write.isEmpty() ? "无" : String.join(",", write);
    }

    /**
     * 读取 {@code --name=value} 形式的参数
     *
     * @param args 命令行参数
     * @param name 参数名
     * @return 参数值；不存在返回 {@code null}
     */
    private static String option(String[] args, String name) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) {
                return arg.substring(name.length() + 1);
            }
        }
        return null;
    }

    /**
     * 读取逗号分隔参数
     *
     * @param args 命令行参数
     * @param name 参数名
     * @return 值列表；不存在返回 {@code null}
     */
    private static List<String> optionList(String[] args, String name) {
        String value = option(args, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new ArrayList<>(List.of(value.split(",")));
    }
}
