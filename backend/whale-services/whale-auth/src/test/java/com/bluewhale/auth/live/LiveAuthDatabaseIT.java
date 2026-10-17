package com.bluewhale.auth.live;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * whale-auth 建库建表 + 种子数据的实时集成测试（真实 MySQL）。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-services/whale-auth -am test -Dwhale.live.it=true -Dtest=LiveAuthDatabaseIT -DfailIfNoTests=false
 * </pre>
 * <p>仅操作 {@code bluewhale_auth} 库；脚本含 {@code DROP TABLE IF EXISTS}，影响范围为该库的目标表。</p>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveAuthDatabaseIT {

    // 说明：Connector/J 不接受 characterEncoding=utf8mb4（UnsupportedEncodingException），
    // 测试侧使用 Java 字符集名 UTF-8；生产 application.yml 同类写法已于 Wave 5.1d 修正为 utf8（AUTH-36）。
    private static final String MYSQL_HOST = System.getProperty("whale.live.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("whale.live.mysql.port", "3306");
    private static final String SERVER_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
            + "/?useUnicode=true&characterEncoding=UTF-8"
            + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/bluewhale_auth?useUnicode=true"
            + "&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB = "bluewhale_auth";
    private static final String MYSQL_USER = System.getProperty("whale.live.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("whale.live.mysql.password", "root");
    private static final String ADMIN_PASSWORD_PLAINTEXT = "123456";

    @BeforeAll
    static void setUpClass() throws Exception {
        try (Connection connection = DriverManager.getConnection(SERVER_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + DB
                    + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
        String script = readResource("db/init.sql");
        try (Connection connection = DriverManager.getConnection(DB_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement()) {
            for (String raw : script.split(";")) {
                String sql = stripCommentLines(raw);
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    @Test
    @DisplayName("init.sql 已创建 9 张表，且含种子数据：admin 用户 + super_admin 角色 + 关联")
    void shouldCreateTablesAndSeedData() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + DB + "'"))
                .isGreaterThanOrEqualTo(9L);
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE username = 'admin'")).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM sys_role WHERE role_code = 'super_admin'")).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 1 AND role_id = 1")).isEqualTo(1L);
    }

    @Test
    @DisplayName("种子 admin 口令为 BCrypt 密文（60 位、非明文），且与注释所述明文 123456 一致（Wave 5.1d 修正 AUTH-37）")
    void shouldStoreAdminPasswordAsBcrypt() throws Exception {
        String hash = queryOne("SELECT password FROM sys_user WHERE username = 'admin'");
        assertThat(hash).isNotNull().startsWith("$2a$").hasSize(60);
        assertThat(hash).doesNotContain(ADMIN_PASSWORD_PLAINTEXT);
        // Wave 5.1d：种子密文由项目内 BCryptPasswordEncoder 生成并写入 init.sql，
        // 使注释所述明文 123456 可实际登录（此前为 P1 缺陷，已修复）。
        assertThat(new BCryptPasswordEncoder().matches(ADMIN_PASSWORD_PLAINTEXT, hash))
                .as("种子密文须与 init.sql 注释所述明文 %s 匹配", ADMIN_PASSWORD_PLAINTEXT)
                .isTrue();
    }

    private static long count(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static String queryOne(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = LiveAuthDatabaseIT.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("类路径资源不存在: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String stripCommentLines(String sql) {
        return Arrays.stream(sql.split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }
}
