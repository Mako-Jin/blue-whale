package com.bluewhale.sts.app.auth;

import com.bluewhale.sts.auth.common.AuthProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「写进库里的值，与读它的人是不是同一副面孔」—— 读取侧的规范化（ADR-0069 的
 * {@code FilterValue.text} · ADR-0070 的 {@code FilterValue.enumValue}）只解决了<b>一半</b>：
 * 若写入侧原样入库，读侧的宽容就是<b>空转</b>。
 *
 * <p><b>两处现场：</b>
 * <ul>
 *   <li>上报 {@code app = " myapp "} ⇒ 库里存的就是带空白的那一串，而过滤侧把
 *       {@code ?app=myapp} 规范成 {@code myapp} ⇒ 一条也匹配不到。
 *       同一个不对称也在条目级的 {@code namespace} / {@code traceId} / {@code userId} /
 *       {@code deviceId} / {@code sessionId} 上 —— 它们都只被 {@code truncate}（截断），不被 trim。</li>
 *   <li>{@code PUT} 传 {@code status = "enabled"} ⇒ 用户 / 租户两个域的 {@code update}
 *       路径都<b>原样入库</b>（{@code create} 却走 {@code resolveStatus} 归一）。
 *       后果有两副面孔：过滤 {@code ?status=ENABLED} 搜不到它；而所有读者都在比
 *       {@code Status.ENABLED.wireName().equals(...)} ⇒ <b>这个账号 / 这个租户登不进来</b>。
 *       租户域还多一层：那条"不可停用自己所在租户"的判据也按字面量比，
 *       于是传一个<b>小写的启用</b>会被当成"我要停用自己"而拒绝。</li>
 * </ul>
 *
 * <p>与 {@code TextEqualityApiTest} / {@code SearchMatchApiTest} / {@code SearchWildcardApiTest} /
 * {@code SearchKeywordTrimApiTest} / {@code FilterValueTrimApiTest} /
 * {@code EnumFilterNormalizationApiTest} 同形（本仓第七处）：<b>不 {@code @Transactional}、
 * 不按 profile 跳过</b>，同一批断言在 h2 与真实 MySQL 两个 job 上各跑一遍 ——
 * 本片问的正是"库里真正留下了什么"，测试事务会把"其实没写进去"掩盖掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
class WriteSideNormalizationApiTest {

    /** 两端空白用同一个常量，免得某个用例悄悄用了别的字符（比如全角空格）而改变了结论 */
    private static final String PAD = "   ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 上报侧：写进去什么样子，就得能搜到什么样子 ==================== */

    @Test
    @DisplayName("日志上报：app 带两端空白写进去，仍要能被不带空白的过滤值搜到")
    void shouldStoreNormalizedLogApp() throws Exception {
        String token = loginAsAdmin();
        String app = uniqueTag();

        reportLog(token, PAD + app + PAD, PAD + "ns" + PAD, PAD + "trace" + PAD);

        assertSameResultSet(logSearch(token, app, null), logSearch(token, PAD + app + PAD, null),
                "日志域：上报 app 带两端空白之后，?app=" + app + " 与 ?app=<带空白> 该给出同一个（非空）结果集"
                        + "（搜不到 = 写入侧原样入库，而过滤侧 trim 了 —— 读侧的宽容变成了不匹配）");
    }

    @Test
    @DisplayName("日志上报：条目级的 namespace / traceId 带两端空白，同样要能被搜到")
    void shouldStoreNormalizedLogItemFields() throws Exception {
        String token = loginAsAdmin();
        String app = uniqueTag();
        String namespace = uniqueTag();
        String traceId = uniqueTag();

        reportLog(token, app, PAD + namespace + PAD, PAD + traceId + PAD);

        assertTrue(totalOf(logSearch(token, app, null)) > 0, "上报本身就该落库，否则下面两条无从判定");
        assertEquals(1, totalOf(search(token, "/api/logs/client", "namespace", namespace)),
                "日志域：namespace 带两端空白上报后，?namespace=" + namespace + " 该搜得到");
        assertEquals(1, totalOf(search(token, "/api/logs/client", "traceId", traceId)),
                "日志域：traceId 带两端空白上报后，?traceId=" + traceId + " 该搜得到");
    }

    @Test
    @DisplayName("事件上报：app 带两端空白写进去，仍要能被不带空白的过滤值搜到")
    void shouldStoreNormalizedEventApp() throws Exception {
        String token = loginAsAdmin();
        String app = uniqueTag();
        String eventName = "probe.action";

        reportEvent(token, PAD + app + PAD, eventName, "u1", "d1", "s1");

        assertSameResultSet(search(token, "/api/events/client", "app", app),
                search(token, "/api/events/client", "app", PAD + app + PAD),
                "事件域：上报 app 带两端空白之后，两种过滤值（带 / 不带空白）该给出同一个结果集");
    }

    @Test
    @DisplayName("事件上报：条目级的 userId / deviceId / sessionId 带两端空白，同样要能被搜到")
    void shouldStoreNormalizedEventItemFields() throws Exception {
        String token = loginAsAdmin();
        String app = uniqueTag();
        String userId = uniqueTag();
        String deviceId = uniqueTag();
        String sessionId = uniqueTag();

        reportEvent(token, app, "probe.action", PAD + userId + PAD, PAD + deviceId + PAD,
                PAD + sessionId + PAD);

        assertTrue(totalOf(search(token, "/api/events/client", "app", app)) > 0,
                "上报本身就该落库，否则下面三条无从判定");
        assertEquals(1, totalOf(search(token, "/api/events/client", "userId", userId)),
                "事件域：userId 带两端空白上报后，?userId=" + userId + " 该搜得到");
        assertEquals(1, totalOf(search(token, "/api/events/client", "deviceId", deviceId)),
                "事件域：deviceId 带两端空白上报后，?deviceId=" + deviceId + " 该搜得到");
        assertEquals(1, totalOf(search(token, "/api/events/client", "sessionId", sessionId)),
                "事件域：sessionId 带两端空白上报后，?sessionId=" + sessionId + " 该搜得到");
    }

    /* ==================== 管理侧：update 写一个"另一种写法"，之后必须仍是标准写法 ==================== */

    @Test
    @DisplayName("用户域：update 传小写 enabled，回显与过滤都该按标准写法 ENABLED")
    void shouldNormalizeUserStatusOnUpdate() throws Exception {
        String token = loginAsAdmin();
        String username = uniqueUsername();
        long userId = createUser(token, username);

        MvcResult updated = putUser(token, userId, username, "enabled");

        assertEquals("ENABLED", stringOf(updated, "$.data.status"),
                "用户域：update 传 enabled 之后回显该是 ENABLED"
                        + "（回显小写 = 写入侧原样入库，而所有读者都在比 \"ENABLED\" ⇒ 这个账号登不进来）");
        assertEquals(1, totalOf(search(token, "/api/auth/users", "keyword", username,
                        "status", "ENABLED")),
                "用户域：update 传 enabled 之后，?status=ENABLED 该搜得到这个账号"
                        + "（搜不到 = 同一个字段在 create 与 update 两条路径上宽容度不同）");
    }

    @Test
    @DisplayName("租户域：update 传小写 enabled，写进去的必须是标准写法 —— 否则那个租户整个登不进来")
    void shouldNormalizeTenantStatusOnUpdate() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueTenantCode();
        long tenantId = createTenant(token, code);
        String username = uniqueUsername();
        String password = seedPassword();
        createUserIn(token, tenantId, username, password);

        // 对照：改之前这个租户是能登进来的 —— 否则下面那条"登录被拒"证明不了任何事
        login(code, username, password).andExpect(jsonPath("$.code").value(0));

        MvcResult updated = putTenant(token, tenantId, code, "enabled");
        assertEquals("ENABLED", stringOf(updated, "$.data.status"),
                "租户域：update 传 enabled 之后回显该是 ENABLED");

        login(code, username, password).andExpect(jsonPath("$.code").value(0));
        // 上面那条是**后果**：读者的判据是 Status.ENABLED.wireName().equals(tenant.getStatus())，
        // 库里躺着一个 "enabled" 时，口令再对也进不来（40101）。这不是"少改一列"，
        // 是一次保存成功却把整个租户锁在门外。
    }

    @Test
    @DisplayName("租户域：对自己所在租户传小写 enabled，是在启用而不是停用 —— 该成功")
    void shouldNotTreatLowercaseEnabledAsSelfDisable() throws Exception {
        String token = loginAsAdmin();
        long ownId = currentTenantId(token);
        MvcResult detail = getTenant(token, ownId);
        String code = stringOf(detail, "$.data.tenantCode");
        String name = stringOf(detail, "$.data.tenantName");

        // 判据此前写成 !Status.ENABLED.wireName().equals(request.getStatus())：
        // 小写的 "enabled" 不等于字面量 "ENABLED" ⇒ 被当成"我要停用自己所在的租户" ⇒ 40921。
        // 而它的语义就是启用：调用方唯一做错的事是拼写用了小写。
        putTenantWith(token, ownId, code, name, "enabled")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals("ENABLED", stringOf(getTenant(token, ownId), "$.data.status"),
                "写进去的该是标准写法，且这个租户仍然启用");
    }

    /* ==================== 回归钉：本轮刻意不动的两处 ==================== */

    @Test
    @DisplayName("事件名带两端空白仍按【拒收并计数】处理 —— 不做 trim 后放行")
    void shouldStillRejectPaddedEventName() throws Exception {
        String token = loginAsAdmin();

        // 事件名是"永久契约"（上游改了名，下游要到图表断崖才察觉）：空白是拼错的一部分，
        // 悄悄纠正它等于让"上游写错了"这件事永远暴露不出来。与 ClientLogLevel.parse
        // 对 level 的宽容形成对照 —— 那是**列里有封闭取值集**的枚举，这是自由契约。
        mockMvc.perform(post("/api/events/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app\":\"" + uniqueTag() + "\",\"events\":["
                                + "{\"eventName\":" + quote(PAD + "probe.action" + PAD) + "}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(0))
                .andExpect(jsonPath("$.data.rejected").value(1));
    }

    @Test
    @DisplayName("回归钉：标准写法（含标准大小写）在两条路径上都照旧")
    void shouldStillAcceptCanonicalValues() throws Exception {
        String token = loginAsAdmin();
        String app = uniqueTag();

        reportLog(token, app, "ns", "trace");
        assertEquals(1, totalOf(logSearch(token, app, null)),
                "不带空白、标准大小的 app 照旧落库并可查");

        String username = uniqueUsername();
        long userId = createUser(token, username);
        assertEquals("DISABLED", stringOf(putUser(token, userId, username, "DISABLED"), "$.data.status"),
                "标准的 DISABLED 照旧既是停用、也搜得到");
        // 条数断言必须带 keyword 锁在本次建的账号上：库里别的用例也会留下 DISABLED 的用户，
        // 不带它就会变成"单独跑绿、全量跑红"—— 与 assertSameResultSet 里那条"基准非空"同理。
        assertEquals(1, totalOf(search(token, "/api/auth/users", "keyword", username,
                        "status", "DISABLED")),
                "标准的 DISABLED 照旧既是停用、也搜得到");
    }

    /* ==================== 断言辅助 ==================== */

    /**
     * 断"两种写法给出同一个结果集"，并且**先确认基准非空** ——
     * 否则"两边都是 0 条"这种平凡通过恰好就是缺陷本身的样子。
     */
    private void assertSameResultSet(MvcResult clean, MvcResult variant, String message) throws Exception {
        int cleanTotal = totalOf(clean);
        assertTrue(cleanTotal > 0, message + "（不带空白的那一种本来就 0 条，这条断言无从判定）");
        assertEquals(cleanTotal, totalOf(variant), message + "（" + cleanTotal + " 条 vs "
                + totalOf(variant) + " 条）");
    }

    private int totalOf(MvcResult result) throws Exception {
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        return total.intValue();
    }

    private String stringOf(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    /* ==================== 请求夹具 ==================== */

    private MvcResult logSearch(String token, String app, String level) throws Exception {
        return level == null ? search(token, "/api/logs/client", "app", app)
                : search(token, "/api/logs/client", "app", app, "level", level);
    }

    /** 参数按 {@code key, value, key, value…} 成对给出，省掉每个域写一遍 builder。 */
    private MvcResult search(String token, String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path)
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    /** 上报一条日志。三个字段都可以带两端空白 —— 本类要的正是"写进去的样子"。 */
    private void reportLog(String token, String app, String namespace, String traceId) throws Exception {
        mockMvc.perform(post("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app\":\"" + app + "\",\"logs\":["
                                + "{\"level\":\"info\",\"message\":\"写入侧探针\","
                                + "\"namespace\":" + quote(namespace) + ","
                                + "\"traceId\":" + quote(traceId) + "}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(1));
    }

    private void reportEvent(String token, String app, String eventName,
                            String userId, String deviceId, String sessionId) throws Exception {
        mockMvc.perform(post("/api/events/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app\":" + quote(app) + ",\"events\":["
                                + "{\"eventName\":" + quote(eventName) + ","
                                + "\"userId\":" + quote(userId) + ","
                                + "\"deviceId\":" + quote(deviceId) + ","
                                + "\"sessionId\":" + quote(sessionId) + "}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(1));
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(properties.getSeedAdmin().getTenantCode(),
                                properties.getSeedAdmin().getUsername(),
                                properties.getSeedAdmin().getPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /** 从 {@code /me} 取当前登录者的租户，而不是写死 1 —— 写死会在种子策略变化时静默指向别的身份 */
    private long currentTenantId(String token) throws Exception {
        MvcResult result = search(token, "/api/auth/me");
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.tenantId"))
                .longValue();
    }

    private long createUser(String token, String username) throws Exception {
        return createUserIn(token, null, username, "Probe@12345");
    }

    /** {@code tenantId} 传 {@code null} 表示不指定（由 {@code /me} 推出当前租户） */
    private long createUserIn(String token, Long tenantId, String username, String password)
            throws Exception {
        String tenantPart = tenantId == null ? "" : "\"tenantId\":" + tenantId + ",";
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s"username":"%s","password":"%s","displayName":"写入侧探针"}
                                """.formatted(tenantPart, username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    /** 改用户。{@code username} 必须带上 —— 它与 create 共用 DTO，而那里是 {@code @NotBlank}。 */
    private MvcResult putUser(String token, long id, String username, String status) throws Exception {
        return mockMvc.perform(put("/api/auth/users/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"写入侧探针","status":"%s"}
                                """.formatted(username, status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    private long createTenant(String token, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"写入侧探针租户 %s"}
                                """.formatted(code, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    private MvcResult putTenant(String token, long id, String code, String status) throws Exception {
        return putTenantWith(token, id, code, "写入侧探针租户 " + code, status)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    /**
     * 改租户状态。名称与编码显式给出 —— {@code update} 会写 {@code tenantName}，
     * 拿一个假名称去改"自己所在租户"会把种子租户真的改名，那是用例不该有的副作用。
     */
    private org.springframework.test.web.servlet.ResultActions putTenantWith(
            String token, long id, String code, String name, String status) throws Exception {
        return mockMvc.perform(put("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"%s","status":"%s"}
                                """.formatted(code, name, status)))
                .andExpect(status().isOk());
    }

    private MvcResult getTenant(String token, long id) throws Exception {
        return search(token, "/api/auth/tenants/" + id);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String tenantCode, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(tenantCode, username, password)))
                .andExpect(status().isOk());
    }

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（见 {@code docs/decisions/0029}） */
    private String seedPassword() {
        return properties.getSeedAdmin().getPassword();
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /**
     * 每次跑用一个新的短标签，用它把断言限定在本次造的那一行上。
     * 只含字母（首字符必须是字母，且不该与别处撞）。
     */
    private static String uniqueTag() {
        return "W" + Long.toString(System.nanoTime(), 36) + UUID.randomUUID().toString().substring(0, 4);
    }

    private static String uniqueTenantCode() {
        return "e2ews" + Long.toString(System.nanoTime(), 36);
    }

    /** 用户名的字符集比编码窄：只允许字母开头 + 字母数字与 {@code _.-} */
    private static String uniqueUsername() {
        return "ws_" + Long.toString(System.nanoTime(), 36);
    }

}
