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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据范围求值的端到端测试（ADR-0025）。
 *
 * <p>要证明的核心是两件事：
 * <ol>
 *   <li><b>合并规则是"取最宽"</b> —— 一个人可能持有多个角色，各带各的 {@code data_scope}，
 *       结果不能被最窄的那个拖回去。这条规则如果写反（取最窄），表现是"给他加了个新角色，
 *       他反而看得更少了"，而单角色用例全都会通过；</li>
 *   <li><b>"已配置"与"已生效"被分开</b> —— {@code DEPT} 这一类档位会被如实标记为
 *       {@code precise = false}，而不是安静地返回一个看起来正常的结果。
 *       {@code ALL} / {@code SELF} 则必须 {@code precise = true}，因为它们是自足的。</li>
 * </ol>
 *
 * <p><b>用 {@code @Transactional}：本类不涉及审计</b>（审计走独立事务，不受回滚影响，
 * 那是 {@code AuditApiTest} 要处理的问题）。这里的角色 / 授权关系全部随测试回滚，
 * 因此不需要唯一化"给上一次构建留下的数据"擦屁股 —— 唯一编码仍然照给，
 * 因为同一次构建里的多个用例互相之间不受事务隔离保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DataScopeApiTest {

    private static final String TEST_PASSWORD = "Kx4Bt9Wm2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Test
    @DisplayName("没有任何角色时 scope 为 null —— 而不是悄悄给一个 ALL")
    void shouldReturnNullScopeWhenNoRoleHoldsOne() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, uniqueUsername());

        mockMvc.perform(get("/api/auth/users/" + userId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 无档位：scope 为空。**不能返回 ALL** —— 一个没配数据范围的人
                // 不该因此看见整个租户，"没配"与"配了全部"是两回事
                .andExpect(jsonPath("$.data.scope").value(nullValue()))
                .andExpect(jsonPath("$.data.allData").value(false))
                .andExpect(jsonPath("$.data.scopes", hasSize(0)))
                .andExpect(jsonPath("$.data.roleCodes", hasSize(0)));
    }

    @Test
    @DisplayName("超级管理员是 ALL：种子角色的数据范围经接口可观测")
    void shouldReportAdminAsAll() throws Exception {
        String token = loginAsAdmin();
        long adminId = meUserId(token);

        mockMvc.perform(get("/api/auth/users/" + adminId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andExpect(jsonPath("$.data.scopeLabel").value("全部"))
                .andExpect(jsonPath("$.data.allData").value(true))
                // ALL 是自足的一档，不需要组织归属或 filterJson 就能翻译成条件
                .andExpect(jsonPath("$.data.precise").value(true))
                .andExpect(jsonPath("$.data.roleCodes", containsInAnyOrder("SUPER_ADMIN")));
    }

    @Test
    @DisplayName("多角色取最宽：SELF 不该把 ALL 拖回去")
    void shouldTakeWidestWhenMultipleRoles() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, uniqueUsername());

        long selfRole = createRole(token, "DS_SELF_" + shortId(), "只读自己", "CUSTOM", "SELF");
        long allRole = createRole(token, "DS_ALL_" + shortId(), "看全部", "CUSTOM", "ALL");
        bindRole(token, selfRole, userId);
        bindRole(token, allRole, userId);

        mockMvc.perform(get("/api/auth/users/" + userId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andExpect(jsonPath("$.data.allData").value(true))
                // 档位明细按由宽到窄排列，便于回答"这个结果是怎么来的"
                .andExpect(jsonPath("$.data.scopes", contains("ALL", "SELF")))
                .andExpect(jsonPath("$.data.roleCodes", hasSize(2)));
    }

    @Test
    @DisplayName("组织范围档位如实标记为不可精确求值 —— 而不是安静地当成 ALL")
    void shouldMarkOrgScopedAsNotPrecise() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, uniqueUsername());
        long role = createRole(token, "DS_DEPT_" + shortId(), "本部门", "CUSTOM", "DEPT_AND_CHILD");
        bindRole(token, role, userId);

        mockMvc.perform(get("/api/auth/users/" + userId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("DEPT_AND_CHILD"))
                .andExpect(jsonPath("$.data.allData").value(false))
                // 关键断言：它是一个"配了但还算不出来"的档位。
                // 少了这个标记，调用方无法区分"就是本部门"与"暂时只能按仅本人处理"
                .andExpect(jsonPath("$.data.precise").value(false))
                .andExpect(jsonPath("$.data.note").value(notNullValue()));
    }

    @Test
    @DisplayName("停用的角色不再贡献数据范围 —— 与权限走同一套生效判定")
    void shouldIgnoreDataScopeOfDisabledRole() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, uniqueUsername());
        String roleCode = "DS_OFF_" + shortId();
        long role = createRole(token, roleCode, "将被停用", "CUSTOM", "ALL");
        bindRole(token, role, userId);

        mockMvc.perform(get("/api/auth/users/" + userId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.scope").value("ALL"));

        // 停用之后必须回到"没有范围"。若这里仍然是 ALL，说明数据范围走的是
        // 一条不检查角色状态的查询 —— 于是"停用角色"只停掉了权限、没停掉范围，
        // 而后者恰恰是更危险的那一半（看得见全部数据却做不了操作）
        updateRoleStatus(token, role, roleCode, "DISABLED");

        mockMvc.perform(get("/api/auth/users/" + userId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value(nullValue()))
                .andExpect(jsonPath("$.data.allData").value(false));
    }

    @Test
    @DisplayName("反查归 auth:user:read：审计员可查，与用户组管理的 40301 形成对照")
    void shouldAllowAuditorToReverseLookupDataScope() throws Exception {
        String adminToken = loginAsAdmin();
        long adminId = meUserId(adminToken);

        String auditorToken = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAuditor().getUsername(), properties.getSeedAuditor().getPassword());

        mockMvc.perform(get("/api/auth/users/" + adminId + "/data-scope")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 同一个审计员去碰用户组管理则必须被拒 —— 两条路径的权限归属是分开决定的
        mockMvc.perform(get("/api/auth/user-groups").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("未认证 401；用户不存在或跨租户一律 40401")
    void shouldRejectUnknownOrForeignUser() throws Exception {
        String token = loginAsAdmin();
        long otherTenant = createTenant(token, uniqueTenantCode());
        long foreignUser = createUserInTenant(token, uniqueUsername(), otherTenant);

        mockMvc.perform(get("/api/auth/users/999999999/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));

        // 跨租户的用户必须表现为"不存在"，而不是"存在但无权"：
        // 后者会把"这个 id 上确实有人"这件事泄漏给另一个租户
        mockMvc.perform(get("/api/auth/users/" + foreignUser + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(get("/api/auth/users/1/data-scope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /* ==================== 辅助 ==================== */

    private String loginAsAdmin() throws Exception {
        return login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
    }

    private long meUserId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.data.userId")).longValue();
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

    private long createUser(String token, String username) throws Exception {
        return createUserInTenant(token, username, null);
    }

    private long createUserInTenant(String token, String username, Long tenantId) throws Exception {
        String tenantField = tenantId == null ? "" : "\"tenantId\":" + tenantId + ",";
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s"username":"%s","displayName":"数据范围测试用户","password":"%s"}
                                """.formatted(tenantField, username, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long createTenant(String token, String tenantCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"%s"}
                                """.formatted(tenantCode, "范围测试租户" + tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long createRole(String token, String code, String name, String category, String dataScope)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s","category":"%s","dataScope":"%s"}
                                """.formatted(code, name, category, dataScope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private void updateRoleStatus(String token, long roleId, String code, String status) throws Exception {
        mockMvc.perform(put("/api/auth/roles/" + roleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"将被停用","category":"CUSTOM",
                                 "dataScope":"ALL","status":"%s"}
                                """.formatted(code, status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void bindRole(String token, long roleId, long userId) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"USER","memberId":%d}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private static String uniqueUsername() {
        return "ds_probe_" + shortId();
    }

    private static String uniqueTenantCode() {
        return "ds_tenant_" + shortId();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
