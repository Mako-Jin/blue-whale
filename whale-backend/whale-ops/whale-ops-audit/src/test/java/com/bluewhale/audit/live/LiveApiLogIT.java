package com.bluewhale.audit.live;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.filter.ApiLogFilter;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import com.bluewhale.common.log.properties.ApiLogProperties;
import com.bluewhale.common.log.sink.LogPersistSupport;
import com.bluewhale.common.web.WebHeaders;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后端接口日志（{@code ApiLogFilter}）的实时集成测试：真实 MySQL 落库 + 真实 Mapper + 真实脱敏器。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行，避免依赖中间件的用例污染 CI。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-ops/whale-audit -am test -Dwhale.live.it=true -Dtest=LiveApiLogIT -DfailIfNoTests=false
 * </pre>
 * <p>前置条件：MySQL 在 127.0.0.1:3306 监听，凭据 {@code root/root}；本用例会自动建库
 * {@code bluewhale_audit} 并执行 {@code db/init.sql}（脚本含 {@code DROP TABLE IF EXISTS}，
 * 仅影响该库的目标表）。</p>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveApiLogIT {

    private static final String MYSQL_HOST = System.getProperty("whale.live.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("whale.live.mysql.port", "3306");
    private static final String SERVER_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT
            + "/?useUnicode=true&characterEncoding=UTF-8"
            + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/bluewhale_audit?useUnicode=true"
            + "&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB = "bluewhale_audit";
    private static final String MYSQL_USER = System.getProperty("whale.live.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("whale.live.mysql.password", "root");
    /** 正常请求 URI */
    private static final String NORMAL_URI = "/live/api-log";
    /** 前端上报接口 URI（默认 exclude-paths 命中，防回环） */
    private static final String REPORT_URI = "/audit/v1/log/frontend/batch";
    /** 网关注入用户ID（请求头文本） */
    private static final String USER_ID_TEXT = "2001";
    /** 期望落库用户ID */
    private static final Long USER_ID_VALUE = 2001L;
    /** 网关注入用户名 */
    private static final String USERNAME_VALUE = "admin";
    /** 明文凭据（不得落库） */
    private static final String PLAIN_PASSWORD = "P@ssw0rd!";
    /** 明文授权令牌（不得落库） */
    private static final String AUTH_TOKEN = "live-secret-token";
    /** body 缓存上限（同时用于超大 body 场景） */
    private static final int MAX_BODY_LENGTH = 64;

    private static HikariDataSource dataSource;
    private static ApiLogMapper apiLogMapper;
    private static ApiLogFilter filter;

    @BeforeAll
    static void setUpClass() throws Exception {
        createDatabase();
        dataSource = buildDataSource();
        runInitScript();
        apiLogMapper = new SqlSessionTemplate(buildSqlSessionFactory()).getMapper(ApiLogMapper.class);
        ApiLogProperties properties = new ApiLogProperties();
        properties.setEnabled(true);
        properties.setSampleRate(1.0D);
        properties.setMaxBodyLength(MAX_BODY_LENGTH);
        // 直接执行的 Executor 保证落库同步完成，断言时数据必已到达
        LogPersistSupport persistSupport = new LogPersistSupport((task, channel) -> task.run());
        SensitiveDataMasker masker = new SensitiveDataMasker(JsonMapper.builder().build());
        filter = new ApiLogFilter(properties, persistSupport, apiLogMapper, masker);
    }

    @Test
    @DisplayName("正常请求：api_log 落库 ≥1 行，trace/user 与请求头一致，biz_status=1、cost_ms>=0")
    void shouldPersistApiLogRowForLiveRequest() throws Exception {
        String traceId = "live-api-ok-" + System.nanoTime();
        MockHttpServletRequest request =
                jsonRequest(NORMAL_URI, "{\"username\":\"admin\",\"password\":\"" + PLAIN_PASSWORD + "\"}");
        addIdentity(request, traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, jsonChain(new AtomicReference<>()));

        List<ApiLogRecord> rows = queryByTraceId(traceId);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(1);
        ApiLogRecord row = rows.get(0);
        assertThat(row.getId()).isNotNull();
        assertThat(row.getTraceId()).isEqualTo(traceId);
        assertThat(row.getUserId()).isEqualTo(USER_ID_VALUE);
        assertThat(row.getUsername()).isEqualTo(USERNAME_VALUE);
        assertThat(row.getRequestUri()).isEqualTo(NORMAL_URI);
        assertThat(row.getHttpMethod()).isEqualTo("POST");
        assertThat(row.getBizStatus()).isEqualTo(1);
        assertThat(row.getResponseCode()).isEqualTo(200);
        assertThat(row.getCostMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(row.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("无明文凭据：request_params 掩码为 ******，授权令牌不落入任何文本列")
    void shouldNotLeakPlaintextCredentials() throws Exception {
        String traceId = "live-api-mask-" + System.nanoTime();
        MockHttpServletRequest request =
                jsonRequest(NORMAL_URI, "{\"username\":\"admin\",\"password\":\"" + PLAIN_PASSWORD + "\"}");
        addIdentity(request, traceId);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, jsonChain(new AtomicReference<>()));

        List<ApiLogRecord> rows = queryByTraceId(traceId);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(1);
        ApiLogRecord row = rows.get(0);
        assertThat(row.getRequestParams()).isNotNull().contains("******").doesNotContain(PLAIN_PASSWORD);
        // 敏感头未被采集入任何权威文本列（表无 headers 列），断言各文本列均不含令牌明文
        assertThat(row.getRequestParams()).doesNotContain(AUTH_TOKEN);
        assertThat(row.getQueryString() == null ? "" : row.getQueryString()).doesNotContain(AUTH_TOKEN);
        assertThat(row.getResponseBody() == null ? "" : row.getResponseBody()).doesNotContain(AUTH_TOKEN);
        assertThat(row.getUserAgent() == null ? "" : row.getUserAgent()).doesNotContain(AUTH_TOKEN);
    }

    @Test
    @DisplayName("超大 body：不抛异常、下游读到全量字节，落库 body 长度不超过上限")
    void shouldTolerateOversizedBody() throws Exception {
        String traceId = "live-api-big-" + System.nanoTime();
        byte[] oversized = buildOversizedJson();
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, oversized);
        addIdentity(request, traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> downstream = new AtomicReference<>();

        filter.doFilter(request, response, jsonChain(downstream));

        assertThat(downstream.get()).isEqualTo(oversized);
        List<ApiLogRecord> rows = queryByTraceId(traceId);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(1);
        ApiLogRecord row = rows.get(0);
        assertThat(row.getRequestParams() == null || row.getRequestParams().length() <= MAX_BODY_LENGTH).isTrue();
        assertThat(row.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("排除路径：命中上报接口的请求不落 api_log（防回环），但仍放行链路")
    void shouldSkipExcludedReportPath() throws Exception {
        String traceId = "live-api-excl-" + System.nanoTime();
        byte[] body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = jsonRequest(REPORT_URI, body);
        addIdentity(request, traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> downstream = new AtomicReference<>();

        filter.doFilter(request, response, jsonChain(downstream));

        assertThat(downstream.get()).isEqualTo(body);
        assertThat(queryByTraceId(traceId)).isEmpty();
    }

    private static void createDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(SERVER_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + DB
                    + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    private static HikariDataSource buildDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(MYSQL_USER);
        config.setPassword(MYSQL_PASSWORD);
        config.setMaximumPoolSize(4);
        return new HikariDataSource(config);
    }

    private static void runInitScript() throws Exception {
        String script = readResource("db/init.sql");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String raw : script.split(";")) {
                String sql = stripCommentLines(raw);
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static SqlSessionFactory buildSqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("live", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(ApiLogMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = LiveApiLogIT.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("类路径资源不存在: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 去掉整行注释，保留可执行 DDL（脚本以分号切分后逐条执行） */
    private static String stripCommentLines(String sql) {
        return Arrays.stream(sql.split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 注入网关链路身份头
     *
     * @param request 模拟请求
     * @param traceId 链路追踪ID
     */
    private static void addIdentity(MockHttpServletRequest request, String traceId) {
        request.addHeader(WebHeaders.TRACE_ID, traceId);
        request.addHeader(WebHeaders.USER_ID, USER_ID_TEXT);
        request.addHeader(WebHeaders.USERNAME, USERNAME_VALUE);
    }

    /**
     * 构造 JSON 请求（文本体）
     *
     * @param uri 请求 URI
     * @param content 请求体文本
     * @return 模拟请求
     */
    private static MockHttpServletRequest jsonRequest(String uri, String content) {
        return jsonRequest(uri, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造 JSON 请求（字节体）
     *
     * @param uri 请求 URI
     * @param content 请求体字节
     * @return 模拟请求
     */
    private static MockHttpServletRequest jsonRequest(String uri, byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(content);
        request.addHeader(HttpHeaders.USER_AGENT, "junit-live");
        return request;
    }

    /**
     * 构造读全请求体并写 JSON 响应的过滤器链
     *
     * @param captured 请求体捕获器
     * @return 过滤器链
     */
    private static FilterChain jsonChain(AtomicReference<byte[]> captured) {
        return (request, response) -> {
            captured.set(((HttpServletRequest) request).getInputStream().readAllBytes());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":0}");
        };
    }

    /**
     * 构造超过缓存上限的 JSON 请求体
     *
     * @return 超长请求体字节
     */
    private static byte[] buildOversizedJson() {
        StringBuilder builder = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 200; i++) {
            builder.append('A');
        }
        return builder.append("\"}").toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 按链路追踪ID查询接口日志
     *
     * @param traceId 链路追踪ID
     * @return 命中的接口日志记录
     */
    private static List<ApiLogRecord> queryByTraceId(String traceId) {
        return apiLogMapper.selectList(new LambdaQueryWrapper<ApiLogRecord>()
                .eq(ApiLogRecord::getTraceId, traceId));
    }
}
