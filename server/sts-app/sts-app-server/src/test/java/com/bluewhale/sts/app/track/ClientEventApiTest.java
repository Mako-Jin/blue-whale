package com.bluewhale.sts.app.track;

import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.track.clientevent.ClientEventEntity;
import com.bluewhale.sts.track.clientevent.ClientEventMapper;
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
 * 行为事件服务的端到端测试 —— 一条请求真实穿过 Controller → Service → Mapper → H2。
 *
 * <p><b>为什么这个类此前不存在，而它现在必须存在。</b>{@code 0026} 给日志与事件两条管道
 * 都补了鉴权与租户隔离，但只有日志域留下了 API 级见证（{@code ClientLogApiTest}）——
 * 事件域的 HTTP 面**一条用例都没有**。于是"事件域的隔离与日志域一模一样"这件事，
 * 一直只是"代码看起来一样"，没有任何东西在它不样的时候会红。
 *
 * <p><b>本类同时把一句从未说出口的口径写成断言（{@code 0077}）。</b>
 * 隔离这件事在三条记录管道上都不接受"平台级"例外 —— 而此前没有任何一处把它写下来过：
 * 日志与审计那两条隔离用例用的**就是**平台级的种子管理员，只是没人点破；
 * 事件域连用例都没有。所以下面第一条用例先把"操作者确实是平台级"证明出来
 * （它的租户没有父级 ⇒ {@code TenantService#currentOperatorIsPlatform()} 为真），
 * 再断言这位平台级操作者**照样**读不到别家的事件。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientEventApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（`docs/decisions/0029` 的后续约束） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private ClientEventMapper clientEventMapper;

    private String adminToken;

    /** 管理员的租户，从 {@code /me} 取 —— 不写死数字 */
    private Long adminTenantId;

    @Test
    @DisplayName("上报 → 按事件名查询 → 详情：同一条链路走得通，且归属取服务端会话")
    void shouldReportAndQueryByEventName() throws Exception {
        String token = loginAsAdmin();
        String app = "it-event-" + UUID.randomUUID();
        String eventName = "order.submit";

        report(token, app, eventName, "u-1");

        MvcResult page = mockMvc.perform(get("/api/events/client")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app)
                        .param("eventName", eventName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].eventName").value(eventName))
                .andReturn();

        long id = ((Number) JsonPath.read(
                page.getResponse().getContentAsString(), "$.data.records[0].id")).longValue();

        mockMvc.perform(get("/api/events/client/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventName").value(eventName));

        // 聚合走的是**手写 SQL**（countByEventName），租户条件在那里是单独的一份 ——
        // 只改 wrapper 忘了它，症状是"列表对、分布是全库的"，所以要单独看一眼。
        mockMvc.perform(get("/api/events/client/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("查询要鉴权、上报刻意公开：未认证 40100、无权限 40301、匿名上报仍然成功")
    void shouldProtectQueryButAllowAnonymousReport() throws Exception {
        // 未认证查询
        mockMvc.perform(get("/api/events/client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        // 已认证但没有 event:client:read
        String adminToken = loginAsAdmin();
        String username = "event_nobody_" + shortId();
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Nobody@12345","displayName":"无权限用户"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String nobodyToken = login("default", username, "Nobody@12345");
        mockMvc.perform(get("/api/events/client").header("Authorization", "Bearer " + nobodyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        // 上报**不加** @RequiresPermission 是分类，不是漏加：登录页出错时也要能上报。
        // 把这一条和上面的 40100 写在一起，是为了让"公开"与"要鉴权"的边界一眼可辨。
        mockMvc.perform(post("/api/events/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app": "it-anon-%s", "events": [{"eventName": "page.error", "eventId": "e-1"}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(1));
    }

    @Test
    @DisplayName("平台级操作者也读不到别家的事件：列表查不到、聚合不计入、详情返 40400 而不是 403")
    void shouldNotLetPlatformOperatorReadOtherTenantsEvents() throws Exception {
        String token = loginAsAdmin();
        // 先证明这位操作者**确实是平台级**：它所在租户没有父级。
        // 没有这一步，"平台级也读不到"就只是一句自述 —— 而它正是本用例唯一想说的话。
        assertOperatorSitsAtPlatformLevel(token);

        String app = "it-foreign-" + UUID.randomUUID();
        String eventName = "foreign.probe";

        ClientEventEntity foreign = new ClientEventEntity();
        foreign.setTenantId(adminTenantId + 1000L);
        foreign.setEventId("foreign-1");
        foreign.setEventName(eventName);
        foreign.setApp(app);
        foreign.setServerTime(System.currentTimeMillis());
        clientEventMapper.insert(foreign);

        mockMvc.perform(get("/api/events/client")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/events/client/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 详情：40400 而不是 403。403 等于向调用方承认"这条记录存在，只是不给你看"，
        // 而"这个 id 上有没有记录"本身就是不该跨租户泄漏的信息。
        //
        // 口径：三条记录管道（审计 / 日志 / 事件）的读侧都**没有平台级例外** ——
        // 三个域的查询入参里从来没有"点名一个租户"这一格，而 0074 的读侧模型是
        // "默认落在自己租户，只有显式点名才跨租户"。所以要开这个口子得先改接口形状。
        mockMvc.perform(get("/api/events/client/{id}", foreign.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("未登录时上报的事件不属于任何租户：平台级操作者也查不到、详情按 40400")
    void shouldHideAnonymousRecordsFromEveryTenant() throws Exception {
        String token = loginAsAdmin();
        assertOperatorSitsAtPlatformLevel(token);

        String app = "it-anon-hide-" + UUID.randomUUID();

        // 未登录上报（真实场景：登录页出错）。归属记为空 —— 见 V7 的列注释。
        mockMvc.perform(post("/api/events/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app": "%s", "events": [{"eventName": "page.error", "eventId": "anon-1"}]}
                                """.formatted(app)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/events/client")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/events/client/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("app", app))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // tenant_id 为空的**那一行**（不是"查不到"这一支）也必须走同一个 40400：
        // 这一族里"不属于任何租户"与"不属于你的租户"是同一句话，两支逐字相同（0059 · 0075）。
        ClientEventEntity anonymous = new ClientEventEntity();
        anonymous.setTenantId(null);
        anonymous.setEventId("anon-null");
        anonymous.setEventName("page.error");
        anonymous.setApp(app);
        anonymous.setServerTime(System.currentTimeMillis());
        clientEventMapper.insert(anonymous);

        mockMvc.perform(get("/api/events/client/{id}", anonymous.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("事件不存在时返回 40400，而不是空对象")
    void shouldReportNotFoundForUnknownId() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/events/client/{id}", 999_999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /* ------------------------------------------------------------------ 辅助 */

    /**
     * 证明当前操作者是**平台级**的 —— 判据与 {@code TenantService#currentOperatorIsPlatform()} 同：
     * 它所在租户没有父级。
     *
     * <p>写出来而不是在注释里声称，是因为本类有几条用例的全部意义就是
     * "**平台级**操作者也拿不到别家的记录"；如果操作者其实不是平台级，
     * 那几条用例会照常全绿，而它们想守的那件事一刻都没被验证过。
     */
    private void assertOperatorSitsAtPlatformLevel(String token) throws Exception {
        mockMvc.perform(get("/api/auth/tenants/{id}", adminTenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 平台级 = 顶级租户成员：parentId 为空（或整个字段不出现）。
                // 非平台级会是一个数字，这一句就会红。
                .andExpect(jsonPath("$.data.parentId").doesNotExist());
    }

    private void report(String token, String app, String eventName, String userId) throws Exception {
        mockMvc.perform(post("/api/events/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app": "%s", "events": [{"eventName": "%s", "eventId": "evt-1", "userId": "%s"}]}
                                """.formatted(app, eventName, userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String loginAsAdmin() throws Exception {
        if (adminToken != null) {
            return adminToken;
        }
        adminToken = login("default", "admin", properties.getSeedAdmin().getPassword());

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        adminTenantId = ((Number) JsonPath.read(
                me.getResponse().getContentAsString(), "$.data.tenantId")).longValue();
        return adminToken;
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

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
