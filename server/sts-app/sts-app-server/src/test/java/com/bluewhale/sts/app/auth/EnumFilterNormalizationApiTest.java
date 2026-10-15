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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「同一个枚举参数，换个写法还搜得到吗」—— {@code status} / {@code resourceType} /
 * {@code memberType} / {@code level} / {@code result} 这些<b>列里存枚举字面量</b>的过滤值，
 * 大小写要不要归一成标准写法。
 *
 * <p>与 {@code SearchKeywordTrimApiTest}（关键词规范化 · ADR-0068）·
 * {@code FilterValueTrimApiTest}（过滤值规范化 · ADR-0069）同形：<b>不 {@code @Transactional}、
 * 不按 profile 跳过</b>，同一批断言在 h2 与真实 MySQL 两个 job 上各跑一遍。
 *
 * <p><b>为什么这不算"两次规范化"。</b>{@code 0069} 解决的是"两端空白"（{@code FilterValue.text}），
 * 本类解决的是"同一个值的另一种写法"。后者只在<b>列里存封闭取值集</b>时才成立 ——
 * 自由文本（{@code app} / {@code traceId} / 审计的 {@code action}）换个大小写就是另一个值，
 * 归一它等于篡改用户意图，所以那些列一个字都不该动。
 *
 * <p><b>本类要钉的是"同一件事在两个域必须是同一件事"。</b>{@code Status} 这一个枚举同时喂给
 * 角色 / 用户 / 租户 / 组织四个域的 {@code status} 列（见 {@code Status} 类注释），
 * 于是同一个 {@code ?status=} 参数在其中一个域搜得到、在另一个域搜不到 ——
 * 前端的下拉框两处长得一模一样，没有人能从界面上看出区别。
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnumFilterNormalizationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 四域：同一个 Status，该有同一副面孔 ==================== */

    @Test
    @DisplayName("用户域：status 的小写写法该与标准写法同义（Status 是同一个枚举）")
    void shouldAcceptLowerCaseStatusInUser() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(userSearch(token, "ENABLED"), userSearch(token, "enabled"),
                "用户域：status=enabled 应当与 status=ENABLED 给出同一个结果集"
                        + "（不同 = 这个域只把值 trim 了一下就交给 eq，没有归一成标准写法）");
    }

    @Test
    @DisplayName("租户域：status 的小写写法该与标准写法同义")
    void shouldAcceptLowerCaseStatusInTenant() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(tenantSearch(token, "ENABLED"), tenantSearch(token, "enabled"),
                "租户域：status=enabled 应当与 status=ENABLED 给出同一个结果集");
    }

    @Test
    @DisplayName("组织域：status 的小写写法该与标准写法同义 —— 而这正是该域 @Schema 承诺过的")
    void shouldAcceptLowerCaseStatusInOrg() throws Exception {
        String token = loginAsAdmin();
        // 先造一条属于本租户的组织，让下面两条断言有非空的基准
        createOrg(token, uniqueCode("EN"), "枚举探针部门");

        assertSameResultSet(orgSearch(token, "ENABLED"), orgSearch(token, "enabled"),
                "组织域：status=enabled 应当与 status=ENABLED 给出同一个结果集"
                        + "（不同 = OrganizationQuery#status 的契约写着「过滤条件宽松处理」，实现却没做）");
    }

    @Test
    @DisplayName("审计域：result 的小写写法该与标准写法同义")
    void shouldAcceptLowerCaseResultInAudit() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, uniqueUsername("enum"));

        // 用 targetId 把断言锁在刚产生的这一条上：审计表里有其它用例留下的同类记录
        assertSameResultSet(
                auditSearch(token, userId, "SUCCESS"), auditSearch(token, userId, "success"),
                "审计域：result=success 应当与 result=SUCCESS 给出同一个结果集");
    }

    /* ==================== 四域：本来就宽容的，改造时不许弄坏 ==================== */

    @Test
    @DisplayName("角色域（对照组）：status 的小写写法本来就同义 —— 它和用户域是同一个枚举")
    void shouldAcceptLowerCaseStatusInRole() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(roleSearch(token, "ENABLED"), roleSearch(token, "enabled"),
                "角色域：status=enabled 应当与 status=ENABLED 给出同一个结果集"
                        + "（这一条是**对照组**：它代表已经归一的一方，用来证明两域确实不同）");
    }

    @Test
    @DisplayName("资源域（对照组）：resourceType 的小写写法本来就同义")
    void shouldAcceptLowerCaseResourceType() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(resourceSearch(token, "MENU"), resourceSearch(token, "menu"),
                "资源域：resourceType=menu 应当与 resourceType=MENU 给出同一个结果集（对照组）");
    }

    @Test
    @DisplayName("角色成员（对照组）：memberType 的小写写法本来就同义")
    void shouldAcceptLowerCaseMemberType() throws Exception {
        String token = loginAsAdmin();
        long adminId = meUserId(token);
        long roleId = createRole(token, uniqueCode("ROLE"), "枚举探针角色");
        bindRoleMember(token, roleId, adminId);

        assertSameResultSet(memberSearch(token, roleId, "USER"), memberSearch(token, roleId, "user"),
                "角色成员：memberType=user 应当与 memberType=USER 给出同一个结果集（对照组）");
    }

    @Test
    @DisplayName("日志域（对照组）：level 的大写写法本来就同义 —— 该列存的是小写")
    void shouldAcceptUpperCaseLevelInLog() throws Exception {
        String token = loginAsAdmin();
        String app = "it-enum-" + uniqueTag();
        report(token, app, 1);

        assertSameResultSet(logSearch(token, app, "info"), logSearch(token, app, "INFO"),
                "日志域：level=INFO 应当与 level=info 给出同一个结果集（对照组）");
    }

    /* ==================== 回归钉：宽容 ≠ 不过滤 ==================== */

    @Test
    @DisplayName("认不得的值仍然按原样比（结果为空），绝不能退化成『不过滤』")
    void shouldKeepUnknownValueAsEmptyResult() throws Exception {
        String token = loginAsAdmin();

        // 归一化最容易踩坏的一支：把 parse 失败当成"没给条件"。那样 ?status=EANBLED
        // （拼错）会**静默返回全部**，调用方以为自己筛过了 —— 这比报错危险得多。
        int allUsers = totalOf(userSearch(token, null));
        assertTrue(allUsers > 0, "库里至少应当有种子账号，否则下面两条是平凡通过的");
        assertEquals(0, totalOf(userSearch(token, "NOT_A_STATUS")),
                "用户域：认不得的 status 应当返回空集，而不是退化成不过滤");
        assertEquals(allUsers, totalOf(userSearch(token, "   ")),
                "用户域：纯空白仍然该与不传同义（不过滤）——它是上一条的对照组");

        int allRoles = totalOf(roleSearch(token, null));
        assertTrue(allRoles > 0, "库里至少应当有种子角色，否则下面这条是平凡通过的");
        assertEquals(0, totalOf(roleSearch(token, "NOT_A_STATUS")),
                "角色域：认得的一方同样不该把认不得的值当成不过滤");
    }

    /* ==================== 断言辅助 ==================== */

    /**
     * 断"两种写法给出同一个结果集"，并且**先确认基准非空** ——
     * 否则"两边都是 0 条"这种平凡通过恰好就是缺陷本身的样子。
     */
    private void assertSameResultSet(MvcResult clean, MvcResult variant, String message) throws Exception {
        int cleanTotal = totalOf(clean);
        assertTrue(cleanTotal > 0, message + "（标准写法本来就 0 条，这条断言无从判定）");
        assertEquals(cleanTotal, totalOf(variant), message + "（标准写法 " + cleanTotal
                + " 条，另一种写法 " + totalOf(variant) + " 条）");
        assertEquals(idsOf(clean), idsOf(variant), message);
    }

    private int totalOf(MvcResult result) throws Exception {
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        return total.intValue();
    }

    private List<Integer> idsOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.records[*].id");
    }

    /* ==================== 请求夹具 ==================== */

    /** {@code value} 传 {@code null} 表示<b>不带这个参数</b>——与"带一个空串"不是同一件事。 */
    private MvcResult userSearch(String token, String status) throws Exception {
        return status == null ? search(token, "/api/auth/users")
                : search(token, "/api/auth/users", "status", status);
    }

    private MvcResult tenantSearch(String token, String status) throws Exception {
        return search(token, "/api/auth/tenants", "status", status);
    }

    private MvcResult orgSearch(String token, String status) throws Exception {
        return search(token, "/api/auth/orgs", "status", status);
    }

    private MvcResult roleSearch(String token, String status) throws Exception {
        return status == null ? search(token, "/api/auth/roles")
                : search(token, "/api/auth/roles", "status", status);
    }

    private MvcResult resourceSearch(String token, String resourceType) throws Exception {
        return search(token, "/api/auth/resources", "resourceType", resourceType);
    }

    private MvcResult memberSearch(String token, long roleId, String memberType) throws Exception {
        return search(token, "/api/auth/roles/" + roleId + "/members", "memberType", memberType);
    }

    private MvcResult auditSearch(String token, long targetId, String result) throws Exception {
        return search(token, "/api/audit/logs",
                "action", "auth.user.create", "targetId", String.valueOf(targetId), "result", result);
    }

    private MvcResult logSearch(String token, String app, String level) throws Exception {
        return search(token, "/api/logs/client", "app", app, "level", level);
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

    /** 当前登录用户的主键，从 {@code /me} 取 —— 写死数字会在种子策略变化时静默指向别的身份。 */
    private long meUserId(String token) throws Exception {
        MvcResult me = search(token, "/api/auth/me");
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.data.userId")).longValue();
    }

    /** 新建一个用户并返回主键：顺带产生一条 {@code auth.user.create} 的 SUCCESS 审计记录。 */
    private long createUser(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Probe@12345","displayName":"枚举探针"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long createOrg(String token, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgCode":"%s","orgName":"%s"}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long createRole(String token, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s","category":"CUSTOM","dataScope":"ALL"}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private void bindRoleMember(String token, long roleId, long userId) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"USER","memberId":%d}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 上报若干条同一 app 的日志并确认落库 —— 带令牌上报，这样日志才有归属、才查得到。 */
    private void report(String token, String app, int count) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"level": "info", "message": "第 %d 条", "timestamp": %d}
                    """.formatted(i, i));
        }
        mockMvc.perform(post("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app\": \"" + app + "\", \"logs\": [" + items + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(count));
    }

    /**
     * 每次跑用一个新的短标签，用它把断言限定在本次造的那一行上。
     * 标签只用字母与数字（36 进制）：组织编码与角色编码都要求首字符是字母。
     */
    private static String uniqueTag() {
        return "E" + Long.toString(System.nanoTime(), 36) + UUID.randomUUID().toString().substring(0, 4);
    }

    private static String uniqueCode(String prefix) {
        return "E2E" + prefix + Long.toString(System.nanoTime(), 36);
    }

    /** 用户名的字符集比编码窄：只允许字母开头 + 字母数字与 {@code _.-} */
    private static String uniqueUsername(String prefix) {
        return "e2e" + prefix + Long.toString(System.nanoTime(), 36);
    }

}
