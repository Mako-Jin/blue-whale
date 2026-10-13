package com.bluewhale.sts.app.log;

import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.log.clientlog.ClientLogEntity;
import com.bluewhale.sts.log.clientlog.ClientLogMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 日志服务的端到端测试 —— 一条请求真实穿过 Controller → Service → Mapper → H2。
 *
 * <p>放在**应用层**而不是日志模块内：这里验证的是"整条链路装配正确"，
 * 包括 Mapper 扫描、MyBatis-Plus 拦截器、建表脚本、统一响应包装是否同时生效。
 * 这些正是单元测试覆盖不到、又最容易在真实部署时才炸掉的部分。
 *
 * <p>{@link Transactional} 让每个用例结束后自动回滚，用例之间互不干扰，
 * 也不必在断言里处理历史数据。
 *
 * <p><b>本类同时是"鉴权上线"这件事的守卫。</b>修复前，
 * {@code GET /api/logs/client*} 全部是公开接口（{@code AuthInterceptor} 的规则是
 * "没声明 @RequiresPermission 就放行"，而本域当时一个都没声明），
 * 任何能访问服务的人都能读到全部租户的日志 —— 而当时的测试**全是绿的**，
 * 因为它们全都是不带令牌发的请求。因此下面刻意保留了两种发法：
 * 查询一律带令牌，并单独有用例证明"不带令牌读不到"。
 * 同时上报一律不带令牌，用来钉住"上报是刻意公开的"这条分类。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientLogApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（`docs/decisions/0029` 的后续约束） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private ClientLogMapper clientLogMapper;

    private String adminToken;

    /** 管理员的租户，从 {@code /me} 取 —— 不写死数字 */
    private Long adminTenantId;

    @Test
    @DisplayName("上报 → 按链路查询：合法条目落库，非法条目被单独计数")
    void shouldReportAndQueryByTraceId() throws Exception {
        String token = loginAsAdmin();
        String traceId = "trace-" + UUID.randomUUID();

        String body = """
                {
                  "app": "it-test",
                  "release": "0.1.0",
                  "sessionId": "session-1",
                  "logs": [
                    {"level": "info",  "namespace": "it › step", "message": "第一步", "timestamp": 1, "traceId": "%s"},
                    {"level": "error", "namespace": "it › step", "message": "第二步", "timestamp": 2, "traceId": "%s",
                     "data": [1, "two"], "context": {"userId": "u-1"}},
                    {"level": "verbose", "namespace": "it › step", "message": "级别非法", "timestamp": 3, "traceId": "%s"}
                  ]
                }
                """.formatted(traceId, traceId, traceId);

        // 带令牌上报：这样日志才有归属。**不带令牌也照样成功**（见 shouldStillAllowAnonymousReport），
        // 但那一批的归属为空，后面就查不出来了 —— 这正是本域租户语义的核心。
        mockMvc.perform(post("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(2))
                .andExpect(jsonPath("$.data.rejected").value(1));

        mockMvc.perform(get("/api/logs/client/trace/{traceId}", traceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 升序返回：链路的阅读顺序就是发生顺序
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].message").value("第一步"))
                .andExpect(jsonPath("$.data[1].level").value("error"))
                // 结构化字段进得去、出得来
                .andExpect(jsonPath("$.data[1].data[1]").value("two"))
                .andExpect(jsonPath("$.data[1].context.userId").value("u-1"))
                // 信封级元数据回填到了条目上
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                // 这条断言看着普通，实则是 release 字段的**唯一守卫**：
                // 它在数据库里的列名是 app_release（RELEASE 是 MySQL 保留字，
                // 见 ClientLogEntity 的字段注释），靠下划线转驼峰推导而不是注解。
                // 推导一旦破坏，这条会立刻失败 —— 否则要等到换用 MySQL 时才发现，
                // 而那时 H2 上的一切测试都还是绿的。
                .andExpect(jsonPath("$.data[0].release").value("0.1.0"));
    }

    @Test
    @DisplayName("链路查询有条数上限：同一 traceId 攒到超过上限时只返回前 200 条")
    void shouldCapTraceRows() throws Exception {
        String token = loginAsAdmin();
        String traceId = "trace-cap-" + UUID.randomUUID();
        String app = "it-trace-cap-" + UUID.randomUUID();

        // 一批 250 条同一个 traceId —— 单批上限是 500，所以**一次请求**就能把某个
        // traceId 灌过上限。这正是它危险的地方：traceId 由上报方自填，
        // 服务端只做长度截断，没有任何结构性约束。
        mockMvc.perform(post("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchWithTraceId(app, traceId, 250)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(250));

        mockMvc.perform(get("/api/logs/client/trace/{traceId}", traceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 修前这里是 250：整个 traceId 链路的记录会被一次性拉回内存并整体序列化。
                // 200 对应 ClientLogService.TRACE_MAX_ROWS（private，故此处写死数字）
                .andExpect(jsonPath("$.data.length()").value(200));
    }

    @Test
    @DisplayName("分页查询：按级别过滤，并由服务端算好总页数")
    void shouldPageAndFilter() throws Exception {
        String token = loginAsAdmin();
        String app = "it-page-" + UUID.randomUUID();
        String namespace = app + " › ns";
        report(token, app, namespace, "warn", 3);
        report(token, app, namespace, "error", 2);

        mockMvc.perform(get("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .param("namespace", namespace)
                        .param("level", "warn")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                // 3 条按每页 2 条 = 2 页，向上取整由服务端负责
                .andExpect(jsonPath("$.data.pages").value(2));
    }

    @Test
    @DisplayName("每页条数被钳制在上限内，避免一次请求拖垮服务")
    void shouldClampPageSize() throws Exception {
        mockMvc.perform(get("/api/logs/client")
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .param("size", "100000"))
                .andExpect(status().isOk())
                // PageQuery.MAX_SIZE
                .andExpect(jsonPath("$.data.size").value(200));
    }

    @Test
    @DisplayName("聚合统计补齐没有数据的级别，便于直接绘图")
    void shouldReturnAllLevelsIncludingEmptyOnes() throws Exception {
        String token = loginAsAdmin();
        String app = "it-stats-" + UUID.randomUUID();
        String namespace = app + " › ns";
        report(token, app, namespace, "info", 2);
        report(token, app, namespace, "error", 1);

        mockMvc.perform(get("/api/logs/client/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.levels.length()").value(7))
                .andExpect(jsonPath("$.data.startTime").isNumber())
                .andExpect(jsonPath("$.data.endTime").isNumber());
    }

    @Test
    @DisplayName("信封级错误整批拒绝：logs 为空")
    void shouldRejectEmptyBatch() throws Exception {
        String body = """
                {"app": "it-test", "logs": []}
                """;

        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("信封级错误整批拒绝：app 缺失")
    void shouldRejectMissingApp() throws Exception {
        String body = """
                {"logs": [{"level": "info", "message": "x"}]}
                """;

        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                // 回传字段级原因，联调时不必靠猜
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("app")));
    }

    @Test
    @DisplayName("查询不存在的日志返回 40400，而不是 50000")
    void shouldReturnNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/logs/client/{id}", 999_999_999L)
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("参数类型错误回 40000 而非 50000：责任在调用方，不该算作服务端故障")
    void shouldTreatTypeMismatchAsClientError() throws Exception {
        mockMvc.perform(get("/api/logs/client")
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .param("page", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("响应体不泄漏 success 字段：业务结果只由 code 表达")
    void shouldNotExposeDerivedSuccessField() throws Exception {
        mockMvc.perform(get("/api/logs/client")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    /* ------------------------------------------------------------------ 鉴权 */

    @Test
    @DisplayName("未认证查询日志一律 40100 —— 修复前这里是 200，且能看到全部租户的日志")
    void shouldRejectAnonymousQuery() throws Exception {
        // 四个查询入口逐个钉住：只加在列表上、漏了详情或链路，等于洞还在
        mockMvc.perform(get("/api/logs/client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/logs/client/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/logs/client/trace/{traceId}", "any"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/logs/client/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("未认证上报仍然成功 —— 上报是**刻意公开**的，不是漏加了注解")
    void shouldStillAllowAnonymousReport() throws Exception {
        // 登录页出错时也要能上报：若这条挂了，说明有人"顺手"把上报也加了鉴权，
        // 后果是最需要诊断的那批日志（登录前的报错）全部发不出来
        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app": "it-anon-%s", "logs": [{"level": "error", "message": "登录页报错"}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(1));
    }

    @Test
    @DisplayName("未认证上报的日志不属于任何租户，因此带令牌也查不到")
    void shouldHideAnonymousRecordsFromEveryTenant() throws Exception {
        String token = loginAsAdmin();
        String app = "it-anon-hide-" + UUID.randomUUID();

        // 未登录上报（真实场景：登录页出错）
        mockMvc.perform(post("/api/logs/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app": "%s", "logs": [{"level": "error", "message": "未登录时的报错"}]}
                                """.formatted(app)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 管理员查看：查不到。这不是 bug —— 无法判断这条匿名日志属于哪个租户，
        // 展示给"当前租户"就等于把别租户的匿名日志一并展示出去，
        // 而登录页日志里可能带着用户输入的用户名。宁可不显示，不可错显示。
        mockMvc.perform(get("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 聚合也不该把它算进去
        mockMvc.perform(get("/api/logs/client/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("别的租户的日志：列表查不到、聚合不计入、详情返 40401 而不是 403")
    void shouldIsolateAcrossTenants() throws Exception {
        String token = loginAsAdmin();
        String app = "it-foreign-" + UUID.randomUUID();

        ClientLogEntity foreign = new ClientLogEntity();
        foreign.setTenantId(adminTenantId + 1000L);
        foreign.setApp(app);
        foreign.setLevel("error");
        foreign.setMessage("别的租户的报错正文");
        foreign.setServerTime(System.currentTimeMillis());
        // 用 BaseMapper.insert 而不是 insertBatch：批量那支**刻意不回填主键**
        // （见 ClientLogMapper 的注释），而这里需要拿 id 去请求详情。
        clientLogMapper.insert(foreign);

        mockMvc.perform(get("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/logs/client/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                // 聚合走的是手写 SQL（countByLevel），租户条件是单独写的一份 ——
                // 只改 wrapper 忘了它，症状就是"总数对、分布全库的"
                .andExpect(jsonPath("$.data.total").value(0));

        // 详情：40401 而不是 403。403 等于向调用方承认"这条记录存在，只是不给你看"，
        // 而"这个 id 上有没有记录"本身就是不该跨租户泄漏的信息。
        mockMvc.perform(get("/api/logs/client/{id}", foreign.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /* ------------------------------------------------------------------ 辅助 */

    private void report(String token, String app, String namespace, String level, int count)
            throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"level": "%s", "namespace": "%s", "message": "第 %d 条", "timestamp": %d}
                    """.formatted(level, namespace, i, i));
        }
        String body = "{\"app\": \"" + app + "\", \"logs\": [" + items + "]}";

        // 带上令牌上报 —— 这样日志才有归属，后面的查询才查得到。
        // 带令牌与不带令牌都是合法用法：登录前不带、登录后由前端自动带。
        mockMvc.perform(post("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 造一批**同一个 traceId** 的上报体，用于验证链路查询的条数上限 */
    private static String batchWithTraceId(String app, String traceId, int count) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"level": "info", "namespace": "cap", "message": "第 %d 条", "timestamp": %d, "traceId": "%s"}
                    """.formatted(i, i, traceId));
        }
        return "{\"app\": \"" + app + "\", \"logs\": [" + items + "]}";
    }

    private String loginAsAdmin() throws Exception {
        if (adminToken != null) {
            return adminToken;
        }
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"default","username":"admin","password":"%s"}
                                """.formatted(properties.getSeedAdmin().getPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        adminToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        adminTenantId = ((Number) JsonPath.read(
                me.getResponse().getContentAsString(), "$.data.tenantId")).longValue();
        return adminToken;
    }

}
