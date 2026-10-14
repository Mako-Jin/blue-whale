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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code 40002}（{@code PARAM_REFERENCE_MISSING}）在全仓 **16 处**落点的核对（ADR-0057）。
 *
 * <p><b>本片领的是 {@code 0056} 的未覆盖第 1 条。</b>那个码与其它业务码的形状不同：
 * 别的码是"一处判据一个 {@code throw}"，而它有 16 处 —— 它们拦的都是同一件事
 * （引用的东西不存在，或存在但放错了租户），差别只在**文案点名了谁**。所以它值得的
 * 核对不是"按支拆"（只有一支），而是"每一处是否点名了正确的对象、是否都有断言"。
 *
 * <p><b>核对结果一：16 处文案全部点名正确。</b>逐处比对过"文案说的对象"与"代码实际
 * 查的那个东西"——一致，包括一处刻意的不对称（{@code ResourceService} 用父资源的
 * **编码**而非主键，因为资源树的父子关系就是以 {@code resource_code} 表达的）。
 *
 * <p><b>核对结果二：16 处里只有 7 处被既有用例钉住</b>（{@code TenantService#assertExists} ·
 * 用户组的父组跨租户 / 成员跨租户 · 角色成员的三种成员类型 · {@code ResourceService}
 * 的父资源）。**剩下 9 处此前没有任何断言**，其中两处（{@code OrganizationService}
 * 的父组织、{@code RoleService} 的授权资源）有用例但**只断言了 {@code code}** ——
 * 只断 {@code code} 的用例分不清"点在哪个对象上"，而这正是 40002 唯一需要说清的事。
 * 本片把 9 处补齐。
 *
 * <p><b>断言的是文案里那个具体的短语，而不是"用户"或"租户"两个字。</b>同一个域里
 * "引用的用户不存在"与"该用户与组织不属于同一租户"的文案**都含"用户"**，
 * 只断两个字的话，两条支长得一模一样 —— 用例不知道自己钉的是哪一支，
 * 换一支实现也照样绿。这与此前那批断言（"跨租户"那几条）是有意的分工：
 * 它们钉的是"有没有点名租户"，本片钉的是"点名的是哪个对象"。
 *
 * <p><b>对照表在 {@code server/README.md}「40002 的 16 处文案与它们的读者」</b>，
 * 不在本文件的注释里 —— 那份清单要能被查询，而注释只能被读。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReferenceMessageApiTest {

    /**
     * 一个**不存在**的主键。
     *
     * <p>取一个远大于任何自增序列的值，而不是"当前最大的 id + 1"：后者在并发建的用例
     * 之间会偶尔撞上一个真行，症状是这条用例**偶尔**失败 —— 那比缺一条用例更糟。
     * 与 {@code TenantReferenceApiTest} 同一条理由。
     */
    private static final long ABSENT_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 用户组：两处文案各自点名 ==================== */

    @Test
    @DisplayName("用户组：引用的用户不存在 / 引用的父用户组不存在 —— 各点名自己的对象")
    void shouldNameTheObjectInUserGroupDomain() throws Exception {
        String token = loginAsAdmin();
        long groupId = createGroup(token, uniqueGroupCode("ref"), "引用文案核对组", null);

        // ①「引用的用户不存在」（UserGroupService#bindMember）。多态成员表刻意没有外键，
        // 存在性只能由应用层保证 —— 这一支此前没有任何用例
        mockMvc.perform(post("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"USER","memberId":%d}
                                """.formatted(ABSENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("引用的用户不存在")));

        // ②「引用的父用户组不存在」（UserGroupService#resolveParent）
        mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"引用文案核对组","parentId":%d}
                                """.formatted(uniqueGroupCode("refp"), ABSENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("父用户组不存在")));

        deleteGroup(token, groupId);
    }

    /* ==================== 组织：父级的两种失败必须能被区分 ==================== */

    @Test
    @DisplayName("组织：父组织不存在 / 父组织不属于同一租户 —— 数据在不在，说法不同")
    void shouldNameTheObjectInOrganizationParent() throws Exception {
        String token = loginAsAdmin();

        // ①「引用的父组织不存在」。既有的那条用例只断言了 code=40002，
        // 而 40002 这个码本身不携带"是哪个引用错了"的信息 —— 只有文案携带
        mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(uniqueOrgCode("ref"), "引用文案核对组织", ABSENT_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("父组织不存在")));

        // ②「父组织不属于同一租户」。数据**确实存在**，只是放错了租户 ——
        // 这一支此前没有任何用例，而它与①的区别恰恰是调用方唯一需要知道的事
        long otherTenantId = createTenant(token, uniqueTenantCode());
        long foreignOrgId = createOrg(token, uniqueOrgCode("refx"), "别租户的部门", null, otherTenantId);

        mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(uniqueOrgCode("refc"), "跨租户父组织", foreignOrgId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("不属于同一租户")));

        deleteOrg(token, foreignOrgId);
        deleteTenant(token, otherTenantId);
    }

    /* ==================== 组织成员：同一对被拒绝的两种原因 ==================== */

    @Test
    @DisplayName("组织成员：引用的用户不存在 / 该用户与组织不属于同一租户")
    void shouldNameTheObjectInOrganizationMember() throws Exception {
        String token = loginAsAdmin();
        long orgId = createOrg(token, uniqueOrgCode("refm"), "引用文案核对部门", null, null);

        // ①「引用的用户不存在」（OrganizationMemberService#requireSameTenantUser）
        mockMvc.perform(post("/api/auth/orgs/" + orgId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":%d}
                                """.formatted(ABSENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("引用的用户不存在")));

        // ② 同一个方法里的下一格。判定两侧都限定 tenant_id，跨租户的归属
        // **永远算不出范围**，却不会报错 —— 只会安静地躺在库里
        long otherTenantId = createTenant(token, uniqueTenantCode());
        long foreignUserId = createUserInTenant(token, uniqueUsername("ref"), otherTenantId);

        mockMvc.perform(post("/api/auth/orgs/" + orgId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":%d}
                                """.formatted(foreignUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("不属于同一租户")));

        deleteUser(token, foreignUserId);
        deleteTenant(token, otherTenantId);
        deleteOrg(token, orgId);
    }

    /* ==================== 角色：三处落点，三种对象 ==================== */

    @Test
    @DisplayName("角色：引用的资源 / 父角色不存在 / 父角色跨租户 —— 三处各点名自己的对象")
    void shouldNameTheObjectInRoleDomain() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("ref"), "引用文案核对角色", null, null);

        // ①「引用的资源不存在」（RoleService#requireResource）。既有用例只断言了 code
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"effect":"ALLOW"}
                                """.formatted(ABSENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("引用的资源不存在")));

        // ②「引用的父角色不存在」
        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(uniqueRoleCode("refp"), "引用文案核对角色", ABSENT_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("父角色不存在")));

        // ③「父角色不属于同一租户」。角色上的 tenant_id 作用很强：跨租户的父角色
        // 会让整条层级判定永远匹配不上 —— 静默不可达的数据，接口不该制造
        long otherTenantId = createTenant(token, uniqueTenantCode());
        long foreignRoleId = createRole(token, uniqueRoleCode("refx"), "别租户的角色", null, otherTenantId);

        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(uniqueRoleCode("refc"), "跨租户父角色", foreignRoleId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("不属于同一租户")));

        deleteRole(token, foreignRoleId);
        deleteTenant(token, otherTenantId);
        deleteRole(token, roleId);
    }

    /* ==================== 夹具 ==================== */

    /**
     * 建号用的口令：**直接取种子账号那一个**，不在本文件里再刻一份字面量。
     *
     * <p>{@code application-h2.yml} 是口令的真值来源，而种子口令满足默认策略，
     * 因此用它建的账号能过 {@code PasswordChanger} 的强度校验。
     */
    private String seedAdminPassword() {
        return properties.getSeedAdmin().getPassword();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(properties.getSeedAdmin().getTenantCode(),
                                properties.getSeedAdmin().getUsername(), seedAdminPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createTenant(String token, String tenantCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"引用文案测试租户 %s"}
                                """.formatted(tenantCode, tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void deleteTenant(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createUserInTenant(String token, String username, long tenantId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"引用文案测试用户","password":"%s"}
                                """.formatted(tenantId, username, seedAdminPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void deleteUser(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/users/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 建一个用户组。
     *
     * <p>{@code parentId} 为 {@code null} 时**不写出这个键**（本域里"不传"与"传 null"
     * 语义不同），所以这里用拼接而不是文本块 —— 文本块拼不出"字段根本不存在"。
     */
    private long createGroup(String token, String code, String name, Long parentId) throws Exception {
        StringBuilder body = new StringBuilder("{\"groupCode\":\"").append(code)
                .append("\",\"groupName\":\"").append(name).append('"');
        if (parentId != null) {
            body.append(",\"parentId\":").append(parentId);
        }
        body.append('}');
        MvcResult result = mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void deleteGroup(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/user-groups/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createOrg(String token, String code, String name, Long parentId, Long tenantId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(code, name, parentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void deleteOrg(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/orgs/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createRole(String token, String code, String name, Long parentId, Long tenantId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(code, name, parentId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void deleteRole(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private static String orgBody(String code, String name, Long parentId, Long tenantId) {
        StringBuilder json = new StringBuilder("{\"orgCode\":\"").append(code)
                .append("\",\"orgName\":\"").append(name).append('"');
        if (parentId != null) {
            json.append(",\"parentId\":").append(parentId);
        }
        if (tenantId != null) {
            json.append(",\"tenantId\":").append(tenantId);
        }
        return json.append('}').toString();
    }

    private static String roleBody(String code, String name, Long parentId, Long tenantId) {
        StringBuilder json = new StringBuilder("{\"roleCode\":\"").append(code)
                .append("\",\"roleName\":\"").append(name)
                .append("\",\"category\":\"BUSINESS\",\"dataScope\":\"ALL\"");
        if (parentId != null) {
            json.append(",\"parentId\":").append(parentId);
        }
        if (tenantId != null) {
            json.append(",\"tenantId\":").append(tenantId);
        }
        return json.append('}').toString();
    }

    /* 编码一律以字母开头 + 只用字母数字与下划线：`Long.toString(n, 36)` 可能以数字开头，
       而编码要求首字符是字母 —— 直接用时间戳会偶尔被自己的 DTO 校验拦下，
       变成一条"大部分时候通过"的假测试 */

    private static String uniqueGroupCode(String prefix) {
        return "E2EREFG_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueOrgCode(String prefix) {
        return "E2EREFO_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueRoleCode(String prefix) {
        return "E2EREFR_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueTenantCode() {
        return "e2ereft" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueUsername(String prefix) {
        return "e2erefu" + prefix + Long.toString(System.nanoTime(), 36);
    }

}
