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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「引用了不存在的租户 → 40002」这条规则在**带 {@code tenant_id} 的四个域里是否一致**（ADR-0050）。
 *
 * <p><b>本片存在的理由是一处只在用户域缺位的校验。</b>
 * {@code bw_auth_user} / {@code bw_auth_role} / {@code bw_auth_organization} /
 * {@code bw_auth_user_group} 都带 {@code tenant_id}，而 DDL 里**没有任何外键**
 * （{@code V3__create_bw_auth_tables.sql} 里零 {@code FOREIGN KEY}）——
 * "建不出属于不存在租户的行"这件事只能由应用层负责。此前四个域各写一遍同名的私有
 * {@code resolveTenantId}，其中**用户域那一份没有校验**：{@code POST /api/auth/users}
 * 带一个不存在的 {@code tenantId} 会**成功地**建出一个账号，它永远登不进来
 * （登录先按租户编码找租户）、永远匹配不上任何判定，却永久占着
 * {@code uk_bw_auth_user_tenant_username} 那个位置。
 *
 * <p><b>这条差异在代码里是被"引用了三次、却从没被谁负责过"的。</b>
 * 另外三个域的注释都写着"与 {@code UserService.resolveTenantId} 的『只取不校验』
 * 有意不同"—— 差异是真的，但**三处都只说了"不同"，没有一处写下"为什么可以不同"**，
 * 而且 {@code docs/} 里对此零记录（{@code resolveTenantId} 与"只取不校验"在
 * {@code docs/} 下都不命中）。真实的错法不是笔误：四次复制里有一次少了一步，
 * 而另外三份的注释把这个缺口**追认**成了设计。
 *
 * <p><b>所以本片断言的不只是"用户域补上了"，而是"四个域给的是同一个答案"。</b>
 * 只测用户域的话，规则搬回各自域里再漂移一次，症状与这次一模一样。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code RoleApiTest} /
 * {@code DefaultRoleOnCreateApiTest} 同一理由：本片要验证"库里真正留下了什么" ——
 * 用户域那两条正例必须反查到落库的 {@code tenantId}，测试事务会把"其实没写进去"掩盖掉。
 *
 * <p><b>不建任何新租户去当"存在的租户"之外的东西</b>：本片唯一会建租户的地方是正例，
 * 且建完只用来放一个账号，不触碰种子租户的既有数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantReferenceApiTest {

    /**
     * 一个**不存在**的租户主键。
     *
     * <p>取一个远大于任何自增序列的值，而不是"最后一个 id + 1"：后者在并发建的用例
     * 之间会偶尔撞上一个真租户，症状是这条用例**偶尔**失败 —— 那比缺一条用例更糟。
     */
    private static final long ABSENT_TENANT_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 缺陷本身：用户域 ==================== */

    @Test
    @DisplayName("用户：指定不存在的租户建号 → 40002（此前会成功建出一个登不进来的账号）")
    void shouldRejectUserCreateWithAbsentTenant() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(ABSENT_TENANT_ID, uniqueUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                // 文案点名"租户"：含糊的"引用的关联数据不存在"会把调用方引去查别的字段，
                // 而这里只有 tenantId 一个可能（与"跨租户的成员"那条同一条理由）
                .andExpect(jsonPath("$.message").value(containsString("租户")));
    }

    @Test
    @DisplayName("用户：列表带不存在的租户 → 40002（空列表会让「这个租户没人」与「没有这个租户」同形）")
    void shouldRejectUserListWithAbsentTenant() throws Exception {
        String token = loginAsAdmin();

        // 返回空列表也是"能跑通"的，但它把一件输入错误伪装成了一个合法结果 ——
        // 与建号那条同一个判据，只是失败方式从"写了脏数据"变成"读了个不存在的东西"
        mockMvc.perform(get("/api/auth/users")
                        .queryParam("tenantId", String.valueOf(ABSENT_TENANT_ID))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    /* ==================== 四个域给同一个答案 ==================== */

    @Test
    @DisplayName("角色 / 组织 / 用户组：同一个 40002 —— 这条规则只有一个属主")
    void shouldRejectAbsentTenantInEveryOtherDomain() throws Exception {
        String token = loginAsAdmin();

        // 三个域此前各自内联这一段（内容相同、格式不同），现在都问 TenantService。
        // 断言同一个码，是因为"分成四处写"正是这次漂移发生的地方：
        // 任意一处再长出一份，这一条就会先红
        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(ABSENT_TENANT_ID, uniqueRoleCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));

        mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(ABSENT_TENANT_ID, uniqueOrgCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));

        mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody(ABSENT_TENANT_ID, uniqueGroupCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    /* ==================== 两条正例：安全默认值与跨租户运营都没被误伤 ==================== */

    @Test
    @DisplayName("不传租户：账号落在当前登录者租户（安全默认值没被误伤）")
    void shouldFallBackToCurrentTenantWhenAbsent() throws Exception {
        String token = loginAsAdmin();
        long ownTenantId = currentTenantId(token);

        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBodyWithoutTenant(uniqueUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        // 从响应里读落库的租户，而不是只看"没报错"：默认值错了恰恰是**不会报错**的那种错
        Number tenantId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.tenantId");
        assertEquals(ownTenantId, tenantId.longValue(),
                "不传 tenantId 时必须落到当前登录者的租户");
    }

    @Test
    @DisplayName("显式指定一个存在的租户：账号落在那个租户，也能按租户查回来")
    void shouldHonorExplicitExistingTenant() throws Exception {
        String token = loginAsAdmin();
        long otherTenantId = createTenant(token, uniqueTenantCode());
        String username = uniqueUsername();

        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(otherTenantId, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Number tenantId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.tenantId");
        assertEquals(otherTenantId, tenantId.longValue(),
                "显式指定的租户必须被尊重 —— 补上校验不该把「跨租户建号」一起挡掉");

        // 反查一侧：列表按那个租户查得到它。这一条同时钉住"列表的租户条件真的生效了"，
        // 而不是"碰巧种子租户里也只有它一个"
        mockMvc.perform(get("/api/auth/users")
                        .queryParam("tenantId", String.valueOf(otherTenantId))
                        .queryParam("keyword", username)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].username").value(username));
    }

    /* ==================== 夹具 ==================== */

    /**
     * 建号用的口令：**直接取种子账号那一个**，不在本文件里再刻一份字面量。
     *
     * <p>{@code application-h2.yml} 是口令的真值来源（仓库里的校验脚本与登录页提示
     * 都从它取），种子口令满足默认策略（≥ 8 位且四类字符齐全），
     * 因此用它建的账号能过 {@code PasswordChanger.assertStrength}。
     * 这也顺带保证了本片不会因为"策略调紧了"而单独变红 —— 它没有第二份需要跟着改的值。
     */
    private String seedPassword() {
        return properties.getSeedAdmin().getPassword();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(properties.getSeedAdmin().getTenantCode(),
                                properties.getSeedAdmin().getUsername(), seedPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /** 从 {@code /me} 取当前登录者的租户，而不是写死 1 —— 写死会在种子策略变化时静默指向别的身份 */
    private long currentTenantId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number tenantId = JsonPath.read(me.getResponse().getContentAsString(), "$.data.tenantId");
        return tenantId.longValue();
    }

    private long createTenant(String token, String tenantCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"租户引用测试租户 %s"}
                                """.formatted(tenantCode, tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 带租户的建号体。其余字段取最短合法值 —— 让**租户**成为唯一的失败可能 */
    private String userBody(long tenantId, String username) {
        return """
                {"tenantId":%d,"username":"%s","displayName":"租户引用测试用户","password":"%s"}
                """.formatted(tenantId, username, seedPassword());
    }

    /** **整个不出现 tenantId 这个键**。写成 {@code "tenantId":null} 测的是另一件事 */
    private String userBodyWithoutTenant(String username) {
        return """
                {"username":"%s","displayName":"租户引用测试用户","password":"%s"}
                """.formatted(username, seedPassword());
    }

    private String roleBody(long tenantId, String roleCode) {
        return """
                {"tenantId":%d,"roleCode":"%s","roleName":"租户引用测试角色",\
                "category":"CUSTOM","dataScope":"ALL"}
                """.formatted(tenantId, roleCode);
    }

    private String orgBody(long tenantId, String orgCode) {
        return """
                {"tenantId":%d,"orgCode":"%s","orgName":"租户引用测试组织","orgType":"DEPT"}
                """.formatted(tenantId, orgCode);
    }

    private String groupBody(long tenantId, String groupCode) {
        return """
                {"tenantId":%d,"groupCode":"%s","groupName":"租户引用测试用户组"}
                """.formatted(tenantId, groupCode);
    }

    /* 编码一律以字母开头：`Long.toString(n, 36)` 可能以数字开头，而编码首字符要求是字母 ——
       直接用纳秒时间戳会偶尔被自己的 DTO 校验拦下，变成一条"大部分时候通过"的假测试 */

    private static String uniqueUsername() {
        return "e2etr" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueTenantCode() {
        return "e2etrt" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueRoleCode() {
        return "E2ETR_" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueOrgCode() {
        return "E2ETRO_" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueGroupCode() {
        return "E2ETRG_" + Long.toString(System.nanoTime(), 36);
    }

}
