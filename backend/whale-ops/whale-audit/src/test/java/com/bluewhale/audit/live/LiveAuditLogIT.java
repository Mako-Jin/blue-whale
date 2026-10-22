package com.bluewhale.audit.live;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.audit.service.impl.AuditLogServiceImpl;
import com.bluewhale.audit.support.AuditArchiveClient;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.aop.AuditLogAspect;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.core.result.PageResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审计落库 + 查询的实时集成测试（真实 MySQL / 真实 MyBatis-Plus Mapper / 真实切面）。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行，避免依赖中间件的用例污染 CI。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-ops/whale-audit -am test -Dwhale.live.it=true -Dtest=LiveAuditLogIT -DfailIfNoTests=false
 * </pre>
 * <p>前置条件：MySQL 在 127.0.0.1:3306 监听，凭据 {@code root/root}；本用例会自动建库
 * {@code bluewhale_audit} 并执行 {@code db/init.sql}（脚本含 {@code DROP TABLE IF EXISTS}，
 * 仅影响该库的目标表）。</p>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveAuditLogIT {

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
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String ORDER_MODULE = "live-order-it";
    private static final long OVERSIZE_REQUEST = 1000L;
    private static final long CLAMPED_SIZE = 200L;

    private static HikariDataSource dataSource;
    private static AuditLogMapper mapper;
    private static AuditLogAspect aspect;
    private static AuditLogServiceImpl auditLogService;
    private static AuditSampleService sampleService;

    @BeforeAll
    static void setUpClass() throws Exception {
        createDatabase();
        dataSource = buildDataSource();
        runInitScript();
        mapper = new SqlSessionTemplate(buildSqlSessionFactory()).getMapper(AuditLogMapper.class);
        SensitiveDataMasker masker = new SensitiveDataMasker(JsonMapper.builder().build());
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "whale-live");
        aspect = new AuditLogAspect(mapper, masker, Runnable::run, environment, null);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new AuditSampleService());
        proxyFactory.addAspect(aspect);
        sampleService = proxyFactory.getProxy();
        auditLogService = new AuditLogServiceImpl(mapper,
                new AuditArchiveClient(new AuditExportProperties()), new AuditExportProperties());
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("成功路径：真实落库 1 行，biz_status=1、trace/username 一致、password 为掩码、cost_ms>=0")
    void shouldPersistSuccessRowWithMaskedPassword() {
        String traceId = "live-ok-" + System.nanoTime();
        mockRequest(traceId);

        String result = sampleService.createUser(new AuditPayload("admin", 2001L, "P@ssw0rd!"));

        assertThat(result).isEqualTo("ok");
        List<AuditLogRecord> rows = queryByTraceId(traceId);
        assertThat(rows).hasSize(1);
        AuditLogRecord row = rows.get(0);
        assertThat(row.getId()).isNotNull();
        assertThat(row.getBizStatus()).isEqualTo(1);
        assertThat(row.getTraceId()).isEqualTo(traceId);
        assertThat(row.getUsername()).isEqualTo("admin");
        assertThat(row.getUserId()).isEqualTo(2001L);
        assertThat(row.getOperateType()).isEqualTo(OperateType.CREATE.name());
        assertThat(row.getTargetType()).isEqualTo("sys_user");
        assertThat(row.getRequestParams()).contains("\"password\":\"******\"")
                .doesNotContain("P@ssw0rd!");
        assertThat(row.getCostMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(row.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("异常路径：落库 1 行 biz_status=0 且 error_msg 非空，异常原样抛出")
    void shouldPersistFailureRowWithErrorMsg() {
        String traceId = "live-fail-" + System.nanoTime();
        mockRequest(traceId);

        assertThatThrownBy(() -> sampleService.deleteRole(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        List<AuditLogRecord> rows = queryByTraceId(traceId);
        assertThat(rows).hasSize(1);
        AuditLogRecord row = rows.get(0);
        assertThat(row.getBizStatus()).isEqualTo(0);
        assertThat(row.getErrorMsg()).isNotBlank().contains("IllegalStateException");
        assertThat(row.getOperateType()).isEqualTo(OperateType.DELETE.name());
        // recordParams=false：失败路径同样不记录参数
        assertThat(row.getRequestParams()).isNull();
    }

    @Test
    @DisplayName("查询接口：条件过滤命中、size 超限收敛 200、按 create_time 倒序")
    void shouldPageWithFilterClampAndDescOrder() {
        String marker = "live-order-" + System.nanoTime();
        LocalDateTime older = LocalDateTime.now().minusSeconds(5);
        LocalDateTime newer = LocalDateTime.now();
        insertRecord(marker + "-old", older, 1);
        insertRecord(marker + "-new", newer, 1);

        AuditLogQueryRequest oversize = new AuditLogQueryRequest();
        oversize.setModule(ORDER_MODULE);
        oversize.setSize(OVERSIZE_REQUEST);
        PageResult<AuditLogVO> clamped = auditLogService.page(oversize);
        assertThat(clamped.getSize()).isEqualTo(CLAMPED_SIZE);

        List<AuditLogVO> ordered = clamped.getRecords().stream()
                .filter(record -> record.getTraceId().startsWith(marker))
                .toList();
        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0).getTraceId()).isEqualTo(marker + "-new");
        assertThat(ordered.get(1).getTraceId()).isEqualTo(marker + "-old");
        assertThat(ordered.get(0).getCreateTime()).isAfterOrEqualTo(ordered.get(1).getCreateTime());

        // 条件过滤：module + traceId 精确命中
        AuditLogQueryRequest byTrace = new AuditLogQueryRequest();
        byTrace.setTraceId(marker + "-new");
        PageResult<AuditLogVO> hit = auditLogService.page(byTrace);
        assertThat(hit.getTotal()).isEqualTo(1L);
        assertThat(hit.getRecords()).hasSize(1);
        assertThat(hit.getRecords().get(0).getTraceId()).isEqualTo(marker + "-new");

        // 条件过滤：username 模糊匹配（成功路径写入的 admin）
        AuditLogQueryRequest byUsername = new AuditLogQueryRequest();
        byUsername.setUsername("admin");
        byUsername.setBizStatus(1);
        PageResult<AuditLogVO> byUser = auditLogService.page(byUsername);
        assertThat(byUser.getTotal()).isGreaterThanOrEqualTo(1L);
        assertThat(byUser.getRecords()).allMatch(record -> record.getBizStatus() == 1);
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
        configuration.addMapper(AuditLogMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = LiveAuditLogIT.class.getClassLoader().getResourceAsStream(path)) {
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

    private static void mockRequest(String traceId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/live/audit");
        request.addHeader(TRACE_ID_HEADER, traceId);
        request.addHeader(USER_ID_HEADER, "2001");
        request.addHeader(USERNAME_HEADER, "admin");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static List<AuditLogRecord> queryByTraceId(String traceId) {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLogRecord>()
                .eq(AuditLogRecord::getTraceId, traceId));
    }

    private static void insertRecord(String traceId, LocalDateTime createTime, int bizStatus) {
        AuditLogRecord record = new AuditLogRecord();
        record.setTraceId(traceId);
        record.setModule(ORDER_MODULE);
        record.setOperation("排序验证");
        record.setOperateType(OperateType.OTHER.name());
        record.setBizStatus(bizStatus);
        record.setCostMs(1L);
        record.setCreateTime(createTime);
        mapper.insert(record);
    }

    /** 被切面代理的样例服务（真实调用目标方法，触发真实落库） */
    public static class AuditSampleService {

        @AuditLog(module = "whale-live", operation = "创建用户", type = OperateType.CREATE,
                targetType = "sys_user", targetId = "#payload.userId", recordParams = true)
        public String createUser(AuditPayload payload) {
            return "ok";
        }

        @AuditLog(module = "whale-live", operation = "删除角色", type = OperateType.DELETE,
                targetType = "sys_role", recordParams = false)
        public String deleteRole(Long id) {
            throw new IllegalStateException("boom");
        }
    }

    /** 含敏感字段的样例请求体 */
    public static class AuditPayload {

        private final String username;
        private final Long userId;
        private final String password;

        public AuditPayload(String username, Long userId, String password) {
            this.username = username;
            this.userId = userId;
            this.password = password;
        }

        /** SpEL {@code #payload.userId} 依赖 getter 访问属性 */
        public String getUsername() {
            return username;
        }

        public Long getUserId() {
            return userId;
        }

        public String getPassword() {
            return password;
        }
    }
}
