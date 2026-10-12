package com.bluewhale.sts.app.audit;

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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计管道的端到端测试 —— 一条真实请求穿过
 * Token 过滤器 → 审计拦截器 → 鉴权拦截器 → Controller → Service → Mapper → H2，
 * 请求结束后由拦截器把这次操作写进 {@code bw_audit_log}。
 *
 * <p><b>本类真正要证明的一件事：审计记录不是上报上来的。</b>
 * 下面每条用例里都没有任何"发送审计"的代码 —— 调用的是普通的业务接口
 * （新建用户、越权尝试），记录却在请求返回之前就已经躺在库里了。
 * 这正是审计管道与另外两条（日志、事件）的根本差别：
 * 那两条要求调用方主动上报，这一条不需要，也因此**无法被调用方伪造**。
 *
 * <p><b>注意本类用 {@code @Transactional}，但审计记录不会被回滚掉。</b>
 * 业务数据在测试事务里、测试结束回滚；而审计落库走的是独立的
 * {@code REQUIRES_NEW} 事务（见 {@code AuditRecorder}），已经提交了。
 * 这个"不对称"本身就是设计要的效果 —— 业务失败要能回滚，审计不能跟着回滚。
 * 相应地，下面所有断言都**按唯一条件过滤**（用新建用户的 id / 用户名），
 * 而不是断总数，否则会被同一次构建里别的用例产生的记录干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditApiTest {

    /** 与 AuthApiTest 用同一套种子口令（见 sts-server-auth 的 AuthProperties） */
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String AUDITOR_PASSWORD = "Auditor@12345";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("新建用户后审计记录已自动产生：没有任何一行上报代码")
    void shouldRecordUserCreationAutomatically() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, "audit_probe_" + shortId());

        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .param("action", "auth.user.create")
                        .param("targetId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].action").value("auth.user.create"))
                .andExpect(jsonPath("$.data.records[0].resource").value("auth:user"))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("admin"))
                .andExpect(jsonPath("$.data.records[0].httpMethod").value("POST"))
                .andExpect(jsonPath("$.data.records[0].requestUri").value("/api/auth/users"))
                // 失败时才有的两列，成功时必须为空 —— 否则审计里"成功"与"失败"
                // 会同时带着错误码，谁都不知道该信哪个
                .andExpect(jsonPath("$.data.records[0].errorCode").value(nullValue()))
                // 只有一个时间戳（服务端记录时间），没有"客户端时间"这一列
                .andExpect(jsonPath("$.data.records[0].serverTime").isNumber());
    }

    @Test
    @DisplayName("越权尝试被记为 FAILURE，并带上真实业务码 —— 这是本仓最容易漏掉的一类")
    void shouldRecordRejectedAttemptAsFailure() throws Exception {
        String auditorToken = login("default", "auditor", AUDITOR_PASSWORD);

        // 审计员没有 auth:user:create 权限：鉴权拦截器在进入 Controller 之前就抛异常，
        // 响应是 **HTTP 200 + 业务码 40301**（本仓约定：业务结果只由 code 表达）。
        // 因此"这次操作失败了"这件事从状态码上完全看不出来 —— 审计必须靠
        // GlobalExceptionHandler 留下的标记来判断，否则会把越权记成"成功创建了用户"。
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"intruder_%s","password":"Intruder@12345"}
                                """.formatted(shortId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        String adminToken = loginAsAdmin();
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "auth.user.create")
                        .param("result", "FAILURE")
                        .param("operatorName", "auditor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 用 greaterThanOrEqualTo 而不是等于 1：同一次构建里别的用例
                // （AuthApiTest 的只读身份用例）也会产生同类记录，而审计走独立事务
                // 不会被测试回滚掉。断言"至少有一条"才是这里真正要守的语义
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].result").value("FAILURE"))
                .andExpect(jsonPath("$.data.records[0].errorCode").value(40301))
                .andExpect(jsonPath("$.data.records[0].errorMessage").isNotEmpty());
    }

    @Test
    @DisplayName("审计查询受权限保护：无权限 403、未认证 401")
    void shouldProtectAuditQuery() throws Exception {
        // 未认证
        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        // 已认证但没有 auth:audit:read
        String adminToken = loginAsAdmin();
        String username = "audit_nobody_" + shortId();
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Nobody@12345","displayName":"无权限用户"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String nobodyToken = login("default", username, "Nobody@12345");
        mockMvc.perform(get("/api/audit/logs").header("Authorization", "Bearer " + nobodyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("审计域没有任何写入接口：POST 被明确拒绝，而不是含糊的『系统内部错误』")
    void shouldExposeNoWriteEndpoint() throws Exception {
        String token = loginAsAdmin();

        // 这条用例守的是一个**架构不变量**，不是功能：一旦有人给审计域加了写入接口，
        // 审计表就从"服务端自己的账本"降级成"谁都能写的一张表"。
        // 断言的是 40500（方法不被支持）而不是任意的非零码 —— 后者会把这条不变量
        // 混进"随便什么错都行"，将来换个 50000 也照样通过。
        mockMvc.perform(post("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"fake.action","resource":"auth:user","result":"SUCCESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40500));
    }

    @Test
    @DisplayName("按动作聚合：动作分布里含刚发生的动作，且失败数单列")
    void shouldAggregateByActionWithFailureCount() throws Exception {
        String token = loginAsAdmin();
        createUser(token, "audit_stats_" + shortId());

        mockMvc.perform(get("/api/audit/logs/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("resource", "auth:user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.failureTotal").isNumber())
                // 聚合窗口必须回传实际生效的时间段：没回传的话，用户看到总数偏小
                // 会以为丢了数据，而不是意识到自己查的是最近 7 天
                .andExpect(jsonPath("$.data.startTime").isNumber())
                .andExpect(jsonPath("$.data.endTime").isNumber())
                .andExpect(jsonPath("$.data.actions[*].action", hasItem("auth.user.create")));
    }

    @Test
    @DisplayName("详情接口按主键取回单条记录")
    void shouldReturnDetailById() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, "audit_detail_" + shortId());

        MvcResult listed = mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .param("action", "auth.user.create")
                        .param("targetId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andReturn();
        Number id = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.records[0].id");

        mockMvc.perform(get("/api/audit/logs/{id}", id.longValue())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.targetId").value(String.valueOf(userId)))
                .andExpect(jsonPath("$.data.id").value(id.longValue()));
    }

    @Test
    @DisplayName("审计记录不存在时返回 40400，而不是空对象")
    void shouldReportNotFoundForUnknownId() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/audit/logs/{id}", 999_999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /** 新建一个用户并返回其主键 */
    private long createUser(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Probe@12345","displayName":"审计探针"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String loginAsAdmin() throws Exception {
        return login("default", "admin", ADMIN_PASSWORD);
    }

    private String login(String tenantCode, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(tenantCode, username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /** 短随机后缀：避免同一次构建里多个用例撞上"登录名租户内唯一" */
    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
