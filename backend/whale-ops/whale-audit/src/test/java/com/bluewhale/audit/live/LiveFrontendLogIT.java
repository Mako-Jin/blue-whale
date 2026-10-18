package com.bluewhale.audit.live;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.bluewhale.audit.dto.BehaviorLogQueryRequest;
import com.bluewhale.audit.dto.FrontendLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.dto.LogItem;
import com.bluewhale.audit.service.impl.BehaviorLogServiceImpl;
import com.bluewhale.audit.service.impl.FrontendLogServiceImpl;
import com.bluewhale.audit.vo.BehaviorLogVO;
import com.bluewhale.audit.vo.FrontendLogVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.log.mapper.BehaviorLogMapper;
import com.bluewhale.common.log.mapper.FrontendLogMapper;
import com.bluewhale.common.log.sink.LogPersistSupport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前端上报 → 查询可见的端到端实时集成测试：真实 MySQL + 真实 Service + 真实 Mapper
 * （{@code frontend_log} / {@code behavior_log}，REQ-SEC-006 A6）。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行，避免依赖中间件的用例污染 CI。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\backend}）：</p>
 * <pre>
 * mvn -B -pl whale-ops/whale-audit -am test -Dwhale.live.it=true -Dtest=LiveFrontendLogIT -DfailIfNoTests=false
 * </pre>
 * <p>前置条件：MySQL 在 127.0.0.1:3306 监听，凭据 {@code root/root}；本用例会自动建库
 * {@code bluewhale_audit} 并执行 {@code db/init.sql}（脚本含 {@code DROP TABLE IF EXISTS}，
 * 仅影响该库的目标表）。</p>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveFrontendLogIT {

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
    /** 网关注入的生效用户ID */
    private static final Long LIVE_USER_ID = 8801L;
    /** 上报体伪造用户ID（服务应以网关回填值覆盖） */
    private static final Long UNTRUSTED_USER_ID = 1L;
    /** 客户端标识 */
    private static final String CLIENT_ID = "live-client-001";
    /** 页面URL */
    private static final String PAGE_URL = "/live/page";
    /** 单页条数收敛上限 */
    private static final long CLAMPED_SIZE = 200L;
    /** 超限请求条数 */
    private static final long OVERSIZE_REQUEST = 1000L;

    private static HikariDataSource dataSource;
    private static FrontendLogServiceImpl frontendLogService;
    private static BehaviorLogServiceImpl behaviorLogService;

    @BeforeAll
    static void setUpClass() throws Exception {
        createDatabase();
        dataSource = buildDataSource();
        runInitScript();
        SqlSessionTemplate template = new SqlSessionTemplate(buildSqlSessionFactory());
        FrontendLogMapper frontendLogMapper = template.getMapper(FrontendLogMapper.class);
        BehaviorLogMapper behaviorLogMapper = template.getMapper(BehaviorLogMapper.class);
        // 直接执行的 Executor 保证落库同步完成，断言时数据必已到达
        LogPersistSupport persistSupport = new LogPersistSupport((task, channel) -> task.run());
        frontendLogService = new FrontendLogServiceImpl(frontendLogMapper, persistSupport);
        behaviorLogService = new BehaviorLogServiceImpl(behaviorLogMapper, persistSupport);
    }

    @Test
    @DisplayName("前端日志上报→查询可见：2 条落库、user_id 取网关值、字段一致、create_time 倒序")
    void shouldIngestAndQueryFrontendLogs() {
        String traceId = "live-fe-" + System.nanoTime();
        long older = System.currentTimeMillis() - 10_000L;
        long newer = System.currentTimeMillis();
        LogBatchRequest request = batchRequest(traceId);
        request.setItems(List.of(
                consoleItem("INFO", "应用启动完成", older),
                consoleItem("ERROR", "渲染失败", newer)));

        frontendLogService.saveBatch(request, LIVE_USER_ID);

        FrontendLogQueryRequest query = new FrontendLogQueryRequest();
        query.setTraceId(traceId);
        PageResult<FrontendLogVO> result = frontendLogService.page(query);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(2);
        List<FrontendLogVO> records = result.getRecords();
        assertThat(records).allMatch(vo -> traceId.equals(vo.getTraceId()));
        assertThat(records).allMatch(vo -> LIVE_USER_ID.equals(vo.getUserId()));
        assertThat(records).allMatch(vo -> CLIENT_ID.equals(vo.getClientId()));
        assertThat(records.get(0).getCreateTime()).isAfter(records.get(1).getCreateTime());
        assertThat(records.get(0).getLogLevel()).isEqualTo("ERROR");
        assertThat(records.get(0).getMessage()).isEqualTo("渲染失败");
        assertThat(records.get(0).getPageUrl()).isEqualTo(PAGE_URL);
        assertThat(records.get(0).getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("行为埋点上报→查询可见：字段与上报体一致、create_time 倒序")
    void shouldIngestAndQueryBehaviorLogs() {
        String traceId = "live-be-" + System.nanoTime();
        long older = System.currentTimeMillis() - 10_000L;
        long newer = System.currentTimeMillis();
        LogBatchRequest request = batchRequest(traceId);
        request.setItems(List.of(
                behaviorItem("PAGE_VIEW", "用户管理页访问", "#menu-user", "{\"from\":\"home\"}", older),
                behaviorItem("CLICK", "点击新建按钮", "#btn-create", "{\"action\":\"new\"}", newer)));

        behaviorLogService.saveBatch(request, LIVE_USER_ID);

        BehaviorLogQueryRequest query = new BehaviorLogQueryRequest();
        query.setTraceId(traceId);
        PageResult<BehaviorLogVO> result = behaviorLogService.page(query);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(2);
        List<BehaviorLogVO> records = result.getRecords();
        assertThat(records).allMatch(vo -> traceId.equals(vo.getTraceId()));
        assertThat(records).allMatch(vo -> LIVE_USER_ID.equals(vo.getUserId()));
        assertThat(records.get(0).getCreateTime()).isAfter(records.get(1).getCreateTime());
        assertThat(records.get(0).getEventType()).isEqualTo("CLICK");
        assertThat(records.get(0).getEventName()).isEqualTo("点击新建按钮");
        assertThat(records.get(0).getTarget()).isEqualTo("#btn-create");
        assertThat(records.get(0).getPayload()).isEqualTo("{\"action\":\"new\"}");
        assertThat(records.get(0).getPageUrl()).isEqualTo(PAGE_URL);
        assertThat(records.get(0).getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("size 超限收敛：查询 size=1000 时 PageResult.size 收敛为 200")
    void shouldClampOversizedPageSize() {
        FrontendLogQueryRequest frontendQuery = new FrontendLogQueryRequest();
        frontendQuery.setSize(OVERSIZE_REQUEST);
        PageResult<FrontendLogVO> frontendResult = frontendLogService.page(frontendQuery);
        assertThat(frontendResult.getSize()).isEqualTo(CLAMPED_SIZE);

        BehaviorLogQueryRequest behaviorQuery = new BehaviorLogQueryRequest();
        behaviorQuery.setSize(OVERSIZE_REQUEST);
        PageResult<BehaviorLogVO> behaviorResult = behaviorLogService.page(behaviorQuery);
        assertThat(behaviorResult.getSize()).isEqualTo(CLAMPED_SIZE);
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
        configuration.addMapper(FrontendLogMapper.class);
        configuration.addMapper(BehaviorLogMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = LiveFrontendLogIT.class.getClassLoader().getResourceAsStream(path)) {
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
     * 构造批量上报请求骨架
     *
     * @param traceId 链路追踪ID
     * @return 批量上报请求（含客户端标识、页面URL；上报体 userId 为不可信占位）
     */
    private static LogBatchRequest batchRequest(String traceId) {
        LogBatchRequest request = new LogBatchRequest();
        request.setTraceId(traceId);
        request.setUserId(UNTRUSTED_USER_ID);
        request.setClientId(CLIENT_ID);
        request.setPageUrl(PAGE_URL);
        return request;
    }

    /**
     * 构造前端 console 日志条目
     *
     * @param logLevel 日志级别
     * @param message 日志内容
     * @param timestamp 前端产生时间（epoch 毫秒）
     * @return 日志条目
     */
    private static LogItem consoleItem(String logLevel, String message, long timestamp) {
        LogItem item = new LogItem();
        item.setLogLevel(logLevel);
        item.setMessage(message);
        item.setStack("ERROR".equals(logLevel) ? "at live.js:1" : null);
        item.setBrowser("junit-live/1.0");
        item.setTimestamp(timestamp);
        return item;
    }

    /**
     * 构造用户行为埋点条目
     *
     * @param eventType 事件类型
     * @param eventName 事件名称
     * @param target 触发目标
     * @param payload 事件载荷
     * @param timestamp 前端产生时间（epoch 毫秒）
     * @return 日志条目
     */
    private static LogItem behaviorItem(String eventType, String eventName, String target, String payload,
                                        long timestamp) {
        LogItem item = new LogItem();
        item.setEventType(eventType);
        item.setEventName(eventName);
        item.setTarget(target);
        item.setPayload(payload);
        item.setTimestamp(timestamp);
        return item;
    }
}
