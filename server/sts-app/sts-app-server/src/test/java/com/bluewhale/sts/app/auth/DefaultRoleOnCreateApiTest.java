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

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 建号时按租户「默认角色」自动授予（ADR-0049）的端到端测试。
 *
 * <p><b>为什么需要一整片：{@code bw_auth_role.is_default} 从建表起就在，它的数据库注释
 * 原文是"是否默认角色（新用户自动授予）"，写入、入口校验、契约、VO、种子一应俱全 ——
 * 唯独"读它去授予"这一步没写。</b>也就是说，这片用例补的不是一个开关的接线，
 * 而是那个开关**从没有人验证过的那个后果**。它此前也不可能有用例：没有任何一行代码
 * 会因为它而改变行为，用例无从断言。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code RoleApiTest} / {@code RoleMemberApiTest}
 * 同一理由：本片最有价值的两条 —— "自动授予真的落到了关系表里"（反查才看得到）
 * 与"跨租户的默认角色不会被挂过来"—— 验证的都是"库里真正留下了什么、判定真的读到了什么"。
 * 测试事务会让未提交的写在同一事务内可见，把"其实没写进去"这类缺陷完全掩盖。
 *
 * <p><b>全部用例都跑在新建的租户里，一处也不动种子租户。</b>
 * 默认角色是**租户级**配置：把种子租户的某个角色标成默认，会让同一份上下文里
 * 别的测试类所建的账号凭空多出角色 —— 那种失败会出现在别的文件里，
 * 指向一个与原因毫无关系的断言。用新租户隔离，是让这片用例**无法**影响别人。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DefaultRoleOnCreateApiTest {

    /** 满足口令策略（长度 ≥ 8、含大小写与数字）的测试口令 */
    private static final String TEST_PASSWORD = "Kd5Nrw9Pz4";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 自动授予 ==================== */

    @Test
    @DisplayName("租户配了默认角色：建号即挂上 —— 响应与反查两侧都看得到")
    void shouldGrantDefaultRoleOnCreate() throws Exception {
        String token = loginAsAdmin();
        long tenantId = createTenant(token, uniqueTenantCode());
        long roleId = createRole(token, tenantId, uniqueRoleCode("dflt"), "默认角色甲", 1, "ENABLED");

        MvcResult created = createUser(token, uniqueUsername(), tenantId);

        // 一侧：建号响应直接说清"授了什么" —— 管理台那句通知靠它才说得对
        assertEquals(List.of("默认角色甲"),
                JsonPath.read(created.getResponse().getContentAsString(), "$.data.defaultRoleNames"));

        // 另一侧：反查 —— 关系真的落到表里了，而且来源是 DIRECT（不是经组）
        long userId = ((Number) JsonPath.read(created.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        mockMvc.perform(get("/api/auth/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].roleId").value((int) roleId))
                .andExpect(jsonPath("$.data[0].source").value("DIRECT"));
    }

    @Test
    @DisplayName("多个默认角色全部挂上，响应按角色主键升序")
    void shouldGrantEveryDefaultRole() throws Exception {
        String token = loginAsAdmin();
        long tenantId = createTenant(token, uniqueTenantCode());
        createRole(token, tenantId, uniqueRoleCode("d1"), "角色一", 1, "ENABLED");
        createRole(token, tenantId, uniqueRoleCode("d2"), "角色二", 1, "ENABLED");
        createRole(token, tenantId, uniqueRoleCode("d3"), "角色三", 1, "ENABLED");
        // 非默认的那一个不该混进来 —— 它是最容易因为"少一个条件"而被一起授出去的
        createRole(token, tenantId, uniqueRoleCode("no"), "非默认角色", 0, "ENABLED");

        MvcResult created = createUser(token, uniqueUsername(), tenantId);

        // 顺序按主键升序（服务端 orderByAsc(id)）：名字的顺序就是授权的顺序，
        // 换个顺序不影响正确性，但会让"响应与列表对不上"这类排查多绕一圈
        assertEquals(List.of("角色一", "角色二", "角色三"),
                JsonPath.read(created.getResponse().getContentAsString(), "$.data.defaultRoleNames"));
    }

    /* ==================== 不授予的三种情况 ==================== */

    @Test
    @DisplayName("租户没有默认角色：响应是空数组（不是 null），账号一个角色都没有")
    void shouldNotGrantWhenTenantHasNoDefaultRole() throws Exception {
        String token = loginAsAdmin();
        long tenantId = createTenant(token, uniqueTenantCode());
        // 有角色，但没有一个是默认的
        createRole(token, tenantId, uniqueRoleCode("plain"), "普通角色", 0, "ENABLED");

        MvcResult created = createUser(token, uniqueUsername(), tenantId);
        String body = created.getResponse().getContentAsString();

        // **空数组而不是 null**：管理台要能区分"这次没授出去任何东西"（要提示下一步）
        // 与"这条接口不表达这件事"（列表 / 详情 / 更新三条路返回 null）。
        // 把两者混成一个 null，那句"该账号默认没有任何菜单码"就失去了判据
        assertTrue(((List<?>) JsonPath.read(body, "$.data.defaultRoleNames")).isEmpty(),
                "没有默认角色时应当是空数组");

        long userId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mockMvc.perform(get("/api/auth/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("默认角色已被停用：建号跳过它，且如实说出\"一个都没挂上\"")
    void shouldSkipDisabledDefaultRole() throws Exception {
        String token = loginAsAdmin();
        long tenantId = createTenant(token, uniqueTenantCode());
        // 标记仍是 isDefault = 1 —— 这里要证明的是"停用本身让它退出默认集合"，
        // 而不是有人把标记清掉了才生效。顺带这也说明：想暂时不给新账号角色，
        // 取消勾选才是对的做法（停用会连带让所有已持有者一起失去权限）
        long roleId = createRole(token, tenantId, uniqueRoleCode("off"), "停用的默认角色", 1, "DISABLED");

        MvcResult created = createUser(token, uniqueUsername(), tenantId);

        assertTrue(((List<?>) JsonPath.read(created.getResponse().getContentAsString(),
                "$.data.defaultRoleNames")).isEmpty(), "停用的默认角色不该被授予");

        long userId = ((Number) JsonPath.read(created.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        mockMvc.perform(get("/api/auth/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 反过来确认那个角色确实还在、只是被停用了 —— 否则上一条断言可能只是
        // 因为角色压根没建成功而"通过"，那就成了一条自我实现的白名单
        mockMvc.perform(get("/api/auth/roles/" + roleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.isDefault").value(1));
    }

    @Test
    @DisplayName("别的租户的默认角色不会被挂过来")
    void shouldNotGrantDefaultRoleFromAnotherTenant() throws Exception {
        String token = loginAsAdmin();
        long otherTenantId = createTenant(token, uniqueTenantCode());
        createRole(token, otherTenantId, uniqueRoleCode("x"), "别租户的默认角色", 1, "ENABLED");

        // 本租户一个默认角色都没有；如果漏了 tenant_id 限定，上面那个就会被挂过来
        long tenantId = createTenant(token, uniqueTenantCode());
        MvcResult created = createUser(token, uniqueUsername(), tenantId);
        String body = created.getResponse().getContentAsString();

        assertTrue(((List<?>) JsonPath.read(body, "$.data.defaultRoleNames")).isEmpty(),
                "跨租户的默认角色不该被授予");

        long userId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mockMvc.perform(get("/api/auth/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    /* ==================== 从默认改为非默认 ==================== */

    @Test
    @DisplayName("把默认角色改成非默认之后，新建的账号不再自动获得（已建的账号不受影响）")
    void shouldStopGrantingOnceFlagCleared() throws Exception {
        String token = loginAsAdmin();
        long tenantId = createTenant(token, uniqueTenantCode());
        String roleCode = uniqueRoleCode("flip");
        long roleId = createRole(token, tenantId, roleCode, "先默认后不默认", 1, "ENABLED");

        MvcResult first = createUser(token, uniqueUsername(), tenantId);
        assertEquals(List.of("先默认后不默认"),
                JsonPath.read(first.getResponse().getContentAsString(), "$.data.defaultRoleNames"));

        // 取消勾选：契约里 isDefault 是 Integer，**必须显式传 0** ——
        // 传 null（= 不带这个键）在更新语义里表示"不改这一列"，会让取消静默失效。
        // roleCode 是必填字段（虽然 update 根本不读它），照样带上
        mockMvc.perform(put("/api/auth/roles/" + roleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"先默认后不默认","category":"CUSTOM",\
                                "dataScope":"ALL","status":"ENABLED","isDefault":0}
                                """.formatted(roleCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.isDefault").value(0));

        MvcResult second = createUser(token, uniqueUsername(), tenantId);
        assertTrue(((List<?>) JsonPath.read(second.getResponse().getContentAsString(),
                "$.data.defaultRoleNames")).isEmpty(), "取消默认之后不该再自动授予");

        // 已经建好的那个账号不受影响 —— "改配置只影响之后"这条边界要能被验证，
        // 否则没人说得清"我改了默认角色，老账号会不会被一起改"
        long firstUserId = ((Number) JsonPath.read(first.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        mockMvc.perform(get("/api/auth/users/" + firstUserId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    /* ==================== 辅助 ==================== */

    private String loginAsAdmin() throws Exception {
        String token = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        return token;
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

    private long createTenant(String token, String tenantCode) throws Exception {
        // 名称也必须唯一（`40905`）—— 一片用例里要建好几个租户，
        // 写死一个名字会让第二条起就撞 `TENANT_NAME_EXISTS`，
        // 而那个报错落在 `createTenant` 里、与用例想验证的东西毫无关系
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"默认角色测试租户 %s"}
                                """.formatted(tenantCode, tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 在指定租户建一个角色；{@code isDefault} 与 {@code status} 都要显式给 */
    private long createRole(String token, long tenantId, String code, String name,
                            int isDefault, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"roleCode":"%s","roleName":"%s","category":"CUSTOM",\
                                "dataScope":"ALL","status":"%s","isDefault":%d}
                                """.formatted(tenantId, code, name, status, isDefault)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 建号并**保留整个响应**：本片多数断言要看的就是响应里那个 {@code defaultRoleNames} */
    private MvcResult createUser(String token, String username, long tenantId) throws Exception {
        return mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"默认角色测试用户",\
                                "password":"%s"}
                                """.formatted(tenantId, username, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();
    }

    private static String uniqueRoleCode(String prefix) {
        return "E2E_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueUsername() {
        return "e2edr" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueTenantCode() {
        return "e2edt" + Long.toString(System.nanoTime(), 36);
    }
}
