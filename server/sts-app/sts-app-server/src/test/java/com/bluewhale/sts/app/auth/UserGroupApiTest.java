package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import com.bluewhale.sts.auth.resource.ResourceMapper;
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
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户组管理与用户角色反查的端到端测试。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code RoleApiTest} /
 * {@code RoleMemberApiTest} 同一理由：本类最有价值的几条用例 ——
 * 软删复活、判定链路随"停用用户组"实时变化、反查能看到两条来源 ——
 * 验证的都是"库里真正留下了什么、判定真的读到了什么"。测试事务会让未提交的写
 * 在同一事务内可见，把"根本没写进库"这类缺陷完全掩盖。
 *
 * <p><b>与 {@code RoleMemberApiTest} 的分工：</b>那边验证的是角色侧的成员关系
 * （把角色授给组之后链路是否走通），这边验证的是**用户组自身的接口**
 * （组能不能建、成员能不能配、配完之后判定是否跟着变）。
 * 组链路的断言两边各有一处，不是重复：一侧改了夹具、另一侧改了被测对象，
 * 任何一边坏了都会有一条测试挂掉并指出是哪一边。
 *
 * <p><b>本类里有一条断言是本切片的核心</b>：{@code shouldHonorGroupEnabledInJudgement} ——
 * 停用用户组之后，组里那个人的权限必须立刻消失。在本片之前，
 * {@code is_enabled} 是一个纯摆设（判定 SQL 从不连 {@code bw_auth_user_group}），
 * 而这条断言就是"它现在真的接进判定了吗"的证明。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserGroupApiTest {

    /** 满足口令策略（长度 ≥ 8、含大小写与数字）的测试口令 */
    private static final String TEST_PASSWORD = "Zx9Lmn7Qw2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private ResourceMapper resourceMapper;

    /** 管理员的主键与租户，在 {@link #loginAsAdmin()} 里从 /me 取 */
    private long adminUserId;
    private long adminTenantId;

    /* ==================== 用户组生命周期 ==================== */

    @Test
    @DisplayName("建组 → 详情 → 改名 → 列表可见 → 删组 → 再查 40405")
    void shouldManageGroupLifecycle() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueGroupCode("life");
        long groupId = createGroup(token, code, "生命周期组", null, null, null);

        mockMvc.perform(get("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groupCode").value(code))
                .andExpect(jsonPath("$.data.category").value("STATIC"))
                // 缺省启用：不传 isEnabled 时必须落在 1 而不是 null ——
                // null 在判定里走的是"视为启用"那一支，但列表上会显示成未知状态
                .andExpect(jsonPath("$.data.isEnabled").value(1))
                .andExpect(jsonPath("$.data.isDefault").value(0));

        mockMvc.perform(put("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"改名后的组","isEnabled":0}
                                """.formatted(code)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groupName").value("改名后的组"))
                .andExpect(jsonPath("$.data.isEnabled").value(0));

        // 列表里能按编码搜到（不断言 total：同一个 h2 库里还有别的用例建的组）
        MvcResult page = mockMvc.perform(get("/api/auth/user-groups?keyword=" + code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andReturn();
        List<Integer> ids = JsonPath.read(page.getResponse().getContentAsString(), "$.data.records[*].id");
        assertTrue(ids.contains((int) groupId), "列表里应当能查到刚建的组");

        mockMvc.perform(delete("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40405));
    }

    @Test
    @DisplayName("编码重复 40910；软删后同码重建是复活原行（主键不变、成员被清空）")
    void shouldReviveSoftDeletedGroupWithSameCode() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueGroupCode("rev");
        long first = createGroup(token, code, "复活组", null, null, null);

        mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody(code, "撞码的组")))
                .andExpect(jsonPath("$.code").value(40910));

        // 先加一个成员，再删组。删组会因为"有成员"被拒绝 —— 这正是守卫，
        // 因此先移出成员；但这里要验证的是复活语义，所以改用**先加成员再改库删组**
        // 不现实，直接走守卫允许的路径：无人 → 删 → 同码重建
        mockMvc.perform(delete("/api/auth/user-groups/" + first)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        MvcResult result = mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody(code, "复活出来的组")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groupName").value("复活出来的组"))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        assertTrue(id.longValue() == first,
                "同码重建必须复活原行（主键不变），否则唯一索引会把 insert 打成 500 —— "
                        + "first=" + first + ", revived=" + id.longValue());

        mockMvc.perform(get("/api/auth/user-groups/" + first + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(delete("/api/auth/user-groups/" + first)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 被显式拒绝的三种配置 ==================== */

    @Test
    @DisplayName("组嵌套被显式拒绝：memberType=GROUP 返回 40001，而不是被静默接受")
    void shouldRejectNestedGroupMember() throws Exception {
        String token = loginAsAdmin();
        long outer = createGroup(token, uniqueGroupCode("out"), "外层组", null, null, null);
        long inner = createGroup(token, uniqueGroupCode("in"), "内层组", null, null, null);

        // 组嵌套在判定链路里只跳一层 —— 判定 SQL 把 gm.member_type 钉成了 'USER'。
        // 接受这条关系会得到一个成员列表里看得见、却谁也拿不到权限的"配置"
        mockMvc.perform(post("/api/auth/user-groups/" + outer + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody("GROUP", inner, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("只接受 USER")));

        // 走到这一步说明库里不该有那条关系
        mockMvc.perform(get("/api/auth/user-groups/" + outer + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));

        deleteGroup(token, outer);
        deleteGroup(token, inner);
    }

    @Test
    @DisplayName("DYNAMIC 分类被拒绝（动态成员求值未实现）；非法 isEnabled 被拒绝")
    void shouldRejectUnsupportedCategoryAndFlag() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"动态组","category":"DYNAMIC"}
                                """.formatted(uniqueGroupCode("dyn"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("STATIC")));

        // isDefault=7 这类值放行不会报错，只会静默改变语义 —— 必须在入口挡住
        mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"坏标志位","isEnabled":7}
                                """.formatted(uniqueGroupCode("flag"))))
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("isEnabled")));
    }

    @Test
    @DisplayName("把用户组挂到自己下面返回 40913；父组跨租户返回 40002（文案点名租户）")
    void shouldGuardGroupHierarchy() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueGroupCode("cyc");
        long groupId = createGroup(token, code, "成环组", null, null, null);

        mockMvc.perform(put("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"成环组","parentId":%d}
                                """.formatted(code, groupId)))
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message", containsString("自己")));

        // 挂到自己的**下级**下面：与自挂是守卫里两条不同的分支 ——
        // 自指只需比一次主键，这一支要沿 parent_id 上溯整条祖先链才判得出来。
        // 这条分支在补齐之前**没有任何用例**（自挂那一支有），
        // 而"漏测一条分支"与"漏测整个守卫"看起来一样绿。
        long childId = createGroup(token, uniqueGroupCode("cyc2"), "成环组的子组",
                ",\"parentId\":" + groupId, null, null);
        mockMvc.perform(put("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"成环组","parentId":%d}
                                """.formatted(code, childId)))
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message", containsString("下级")));

        // 跨租户的父组：数据确实存在，只是放错了租户 —— 文案必须点名租户，
        // 否则调用方会去查主键有没有写错
        long otherTenant = createTenant(token, uniqueTenantCode());
        long foreignGroup = createGroup(token, uniqueGroupCode("frn"), "别租户的组",
                ",\"tenantId\":" + otherTenant, null, null);

        mockMvc.perform(put("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"跨租户父组","parentId":%d}
                                """.formatted(code, foreignGroup)))
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message", containsString("租户")));

        // 先子后父：上面造出的子组会让父组的删除守卫（40913）拦下来
        deleteGroup(token, childId);
        deleteGroup(token, groupId);
        deleteGroup(token, foreignGroup);
        mockMvc.perform(delete("/api/auth/tenants/" + otherTenant)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 成员关系 ==================== */

    @Test
    @DisplayName("加成员 → 列表可见 → 改时间窗不堆行 → 移出 → 再移出 40409；跨租户成员 40002")
    void shouldManageGroupMembers() throws Exception {
        String token = loginAsAdmin();
        long groupId = createGroup(token, uniqueGroupCode("mem"), "成员组", null, null, null);

        String username = uniqueUsername();
        long userId = createUser(token, username);

        mockMvc.perform(post("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody("USER", userId, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.memberType").value("USER"))
                .andExpect(jsonPath("$.data.memberTypeLabel").value("用户"))
                .andExpect(jsonPath("$.data.memberName").value("成员关系测试用户"))
                .andExpect(jsonPath("$.data.effectiveNow").value(true));

        // 同一对 (组, 成员) 再绑一次：必须是 upsert（改这一条的时间窗），不是插出第二条
        mockMvc.perform(post("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody("USER", userId,
                                System.currentTimeMillis() + 3_600_000L, null)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.effectiveNow").value(false));

        mockMvc.perform(get("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].effectiveNow").value(false));

        mockMvc.perform(delete("/api/auth/user-groups/" + groupId + "/members/USER/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/auth/user-groups/" + groupId + "/members/USER/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40409));

        // 跨租户的成员：数据确实存在，只是放错了租户 —— 文案必须点名租户，
        // 否则调用方会去查主键有没有写错
        long otherTenant = createTenant(token, uniqueTenantCode());
        long foreignUser = createUserInTenant(token, uniqueUsername(), otherTenant);
        mockMvc.perform(post("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody("USER", foreignUser, null, null)))
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message", containsString("租户")));

        mockMvc.perform(delete("/api/auth/users/" + foreignUser)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/auth/tenants/" + otherTenant)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        deleteGroup(token, groupId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("删除守卫：仍有成员 40918；仍有子组 40913")
    void shouldRefuseDeleteWhenInUse() throws Exception {
        String token = loginAsAdmin();
        long groupId = createGroup(token, uniqueGroupCode("del"), "被引用组", null, null, null);
        String username = uniqueUsername();
        long userId = createUser(token, username);

        bindGroupMember(token, groupId, "USER", userId, null, null);
        mockMvc.perform(delete("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40918))
                .andExpect(jsonPath("$.message", containsString("成员")));

        revokeGroupMember(token, groupId, "USER", userId);

        // 子组：parentId 不产生权限，但它是一列会被展示的层级信息，
        // 成孤儿之后就再也修不回来了（没有接口能把 parent_id 指回已删的行）
        long childId = createGroup(token, uniqueGroupCode("kid"), "子组",
                ",\"parentId\":" + groupId, null, null);
        mockMvc.perform(delete("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40913));

        deleteGroup(token, childId);
        deleteGroup(token, groupId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 判定链路（本切片的核心） ==================== */

    /**
     * 用户 → 用户组 → 角色 → 资源，**并且停用用户组必须立刻断掉它**。
     *
     * <p>前半段验证接口把整条链配起来了；后半段是本切片修掉的那处静默缺陷的证明：
     * 在此之前 {@code is_enabled} 从没进过判定，把这个组停用对权限毫无影响 ——
     * 也就是说"停用"是一个假开关。断言必须落在**真实接口的准入结果**上
     * （{@code GET /api/auth/tenants}），而不是"读库看到 is_enabled=0"。
     */
    @Test
    @DisplayName("组链路：用户 → 组 → 角色 → 资源；停用组权限立即消失，重新启用恢复")
    void shouldHonorGroupEnabledInJudgement() throws Exception {
        String token = loginAsAdmin();
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        String username = uniqueUsername();
        long userId = createUser(token, username);
        String userToken = login(properties.getSeedAdmin().getTenantCode(), username, TEST_PASSWORD);

        String groupCode = uniqueGroupCode("jdg");
        long groupId = createGroup(token, groupCode, "判定组", null, 1, null);
        bindGroupMember(token, groupId, "USER", userId, null, null);

        long roleId = createRole(token, uniqueRoleCode("jdg"), "判定角色", "CUSTOM", "ALL");
        grant(token, roleId, resourceId, "ALLOW");
        bindRoleMember(token, roleId, "GROUP", groupId, null, null);

        // 二级跳转生效：这个用户自己没有角色，权限是从组里来的
        expectTenantList(userToken, 0);

        // 停用组 —— 判定侧必须连表读到它。这一条在本片之前是**不成立**的
        updateGroup(token, groupId, groupCode, "判定组", 0);
        expectTenantList(userToken, 40301);

        // 重新启用：权限必须回来（同时是"改回来的字段真的写进库了"的回归点）
        updateGroup(token, groupId, groupCode, "判定组", 1);
        expectTenantList(userToken, 0);

        // 组-用户关系过期：判定里那段子查询同样有时态过滤
        bindGroupMember(token, groupId, "USER", userId,
                System.currentTimeMillis() - 7_200_000L, System.currentTimeMillis() - 3_600_000L);
        expectTenantList(userToken, 40301);

        unbindRoleMember(token, roleId, "GROUP", groupId);
        deleteRole(token, roleId);
        revokeGroupMember(token, groupId, "USER", userId);
        deleteGroup(token, groupId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 反查 ==================== */

    /**
     * 反查接口：两条来源都要出现，而且**停用/过期的行不能被过滤掉** ——
     * 它们正是"为什么没有权限"的答案。
     *
     * <p>断言不写成 {@code $.data[0].xxx}：返回的是一组**无序语义**的行，
     * 按下标断言会把"排序恰好如此"当成契约（改一次 ORDER BY 就会假失败）。
     * 这里按 {@code roleCode} 找那一行再断言它的字段。
     */
    @Test
    @DisplayName("反查：直接授予与经组授予两条来源都在；组停用、组成员过期都照原样返回并带派生标记")
    void shouldExposeRoleSourcesOfUser() throws Exception {
        String token = loginAsAdmin();
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        String username = uniqueUsername();
        long userId = createUser(token, username);

        String directCode = uniqueRoleCode("dir");
        long directRole = createRole(token, directCode, "直授角色", "CUSTOM", "ALL");
        bindRoleMember(token, directRole, "USER", userId, null, null);

        String groupCode = uniqueGroupCode("src");
        long groupId = createGroup(token, groupCode, "来源组", null, 1, null);
        bindGroupMember(token, groupId, "USER", userId, null, null);

        String groupRoleCode = uniqueRoleCode("grt");
        long groupRole = createRole(token, groupRoleCode, "组角色", "CUSTOM", "ALL");
        grant(token, groupRole, resourceId, "ALLOW");
        bindRoleMember(token, groupRole, "GROUP", groupId, null, null);

        List<Map<String, Object>> rows = roleSources(token, userId);
        assertTrue(rows.size() == 2, "应当有两条来源，实际：" + rows);

        Map<String, Object> direct = rowOf(rows, directCode);
        assertTrue("DIRECT".equals(direct.get("source")), "直授那一行的 source 应为 DIRECT：" + direct);
        assertTrue("直接授予".equals(direct.get("sourceLabel")), "：" + direct);
        assertTrue(Boolean.TRUE.equals(direct.get("effectiveNow")), "长期有效应当此刻生效：" + direct);
        assertTrue(direct.get("groupId") == null, "DIRECT 行不该有来源组：" + direct);
        assertTrue(direct.get("groupCode") == null, "DIRECT 行不该有来源组：" + direct);

        Map<String, Object> viaGroup = rowOf(rows, groupRoleCode);
        assertTrue("GROUP".equals(viaGroup.get("source")), "经组那一行的 source 应为 GROUP：" + viaGroup);
        assertTrue("经用户组授予".equals(viaGroup.get("sourceLabel")), "：" + viaGroup);
        assertTrue(groupCode.equals(viaGroup.get("groupCode")), "：" + viaGroup);
        assertTrue("来源组".equals(viaGroup.get("groupName")), "：" + viaGroup);
        assertTrue(Integer.valueOf(1).equals(viaGroup.get("groupEnabled")), "：" + viaGroup);
        assertTrue(Boolean.TRUE.equals(viaGroup.get("effectiveNow")), "：" + viaGroup);
        assertTrue(Boolean.TRUE.equals(viaGroup.get("groupMemberEffectiveNow")), "：" + viaGroup);

        // 停用组：这一行必须仍在列表里，只是 groupEnabled=0 ——
        // 如果反查按判定条件过滤，这一行会消失，"我明明授了权怎么没有"就没人能回答
        updateGroup(token, groupId, groupCode, "来源组", 0);
        rows = roleSources(token, userId);
        assertTrue(rows.size() == 2, "停用组后这一行不该消失：" + rows);
        viaGroup = rowOf(rows, groupRoleCode);
        assertTrue(Integer.valueOf(0).equals(viaGroup.get("groupEnabled")), "：" + viaGroup);
        assertTrue(Boolean.TRUE.equals(viaGroup.get("groupMemberEffectiveNow")), "：" + viaGroup);

        // 组成员关系过期：同样保留，只是派生标记变了
        bindGroupMember(token, groupId, "USER", userId,
                System.currentTimeMillis() - 7_200_000L, System.currentTimeMillis() - 3_600_000L);
        viaGroup = rowOf(roleSources(token, userId), groupRoleCode);
        assertTrue(Boolean.FALSE.equals(viaGroup.get("groupMemberEffectiveNow")),
                "过期的组成员关系应当被标为不生效：" + viaGroup);
        assertTrue(Boolean.TRUE.equals(viaGroup.get("effectiveNow")),
                "角色成员那条边自己没过期，不该被连带标成不生效：" + viaGroup);

        // 用户不存在时 40401，而不是空列表 —— 空列表会让调用方以为"这个人没有任何角色"
        mockMvc.perform(get("/api/auth/users/99999999/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40401));

        // 收尾
        unbindRoleMember(token, directRole, "USER", userId);
        deleteRole(token, directRole);
        unbindRoleMember(token, groupRole, "GROUP", groupId);
        deleteRole(token, groupRole);
        revokeGroupMember(token, groupId, "USER", userId);
        deleteGroup(token, groupId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 权限守卫 ==================== */

    @Test
    @DisplayName("审计员没有用户组权限：读也读不到，写更是 40301")
    void shouldDenyAuditor() throws Exception {
        String token = loginAsAdmin();
        long groupId = createGroup(token, uniqueGroupCode("aud"), "审计员不可见", null, null, null);
        String auditorToken = loginAsAuditor();

        mockMvc.perform(get("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        // 成员列表等于"这个组里有谁"，比组列表更敏感 —— 与角色成员列表一样不默认开放
        mockMvc.perform(get("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(delete("/api/auth/user-groups/" + groupId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(jsonPath("$.code").value(40301));

        // 反查走的是 auth:user:read，**审计员持有它**（它是只读权限）——
        // 这条断言刻意留着：它说明"反查"属于用户读，而不是用户组读，
        // 两者的权限归属是分开决定的
        mockMvc.perform(get("/api/auth/users/" + adminUserId + "/roles")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        deleteGroup(token, groupId);
    }

    /* ==================== 辅助 ==================== */

    private String loginAsAdmin() throws Exception {
        String token = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
        // 管理员的主键与租户从 /me 拿，而不是写死 1：写死会在种子策略变化时静默指向别的身份
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = me.getResponse().getContentAsString();
        adminUserId = ((Number) JsonPath.read(body, "$.data.userId")).longValue();
        adminTenantId = ((Number) JsonPath.read(body, "$.data.tenantId")).longValue();
        return token;
    }

    private String loginAsAuditor() throws Exception {
        return login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAuditor().getUsername(),
                properties.getSeedAuditor().getPassword());
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

    /** 断言一次真实的准入结果：{@code code=0} 表示放行，非 0 表示被 AuthInterceptor 拦下 */
    private void expectTenantList(String token, int expectedCode) throws Exception {
        mockMvc.perform(get("/api/auth/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private long createGroup(String token, String code, String name,
                             String extraFields, Integer isEnabled, Integer isDefault) throws Exception {
        // 直接拼 JSON 而不是"在字符串末尾插字段"：文本块会带上结尾换行，
        // 按 length()-1 插入会插到 `}` 的**后面**，得到一个语法错误的请求体 ——
        // 而它返回的是 40000（请求体格式不正确），看起来像是服务端不认这些字段。
        StringBuilder body = new StringBuilder("{\"groupCode\":\"").append(code)
                .append("\",\"groupName\":\"").append(name).append('"');
        if (extraFields != null) {
            body.append(extraFields);
        }
        if (isEnabled != null) {
            body.append(",\"isEnabled\":").append(isEnabled);
        }
        if (isDefault != null) {
            body.append(",\"isDefault\":").append(isDefault);
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

    private void updateGroup(String token, long id, String code, String name, int isEnabled)
            throws Exception {
        mockMvc.perform(put("/api/auth/user-groups/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"%s","isEnabled":%d}
                                """.formatted(code, name, isEnabled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void deleteGroup(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/user-groups/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void bindGroupMember(String token, long groupId, String memberType, long memberId,
                                 Long from, Long to) throws Exception {
        mockMvc.perform(post("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(memberType, memberId, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void revokeGroupMember(String token, long groupId, String memberType, long memberId)
            throws Exception {
        mockMvc.perform(delete("/api/auth/user-groups/" + groupId + "/members/"
                        + memberType + "/" + memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createRole(String token, String code, String name,
                            String category, String dataScope) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s","category":"%s","dataScope":"%s"}
                                """.formatted(code, name, category, dataScope)))
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

    private void grant(String token, long roleId, long resourceId, String effect) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"effect":"%s"}
                                """.formatted(resourceId, effect)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void bindRoleMember(String token, long roleId, String memberType, long memberId,
                                Long from, Long to) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberBody(memberType, memberId, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void unbindRoleMember(String token, long roleId, String memberType, long memberId)
            throws Exception {
        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/" + memberType + "/" + memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
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
                                {%s"username":"%s","displayName":"成员关系测试用户","password":"%s"}
                                """.formatted(tenantField, username, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createTenant(String token, String tenantCode) throws Exception {
        // 租户名称也必须唯一：TenantService 会判重（40905），
        // 用固定名字会让第二个用例失败 —— 而且失败信息看起来与"租户"毫无关系
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"%s"}
                                """.formatted(tenantCode, "组测试租户" + tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long resourceIdOf(String resourceCode) {
        ResourceEntity resource = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, resourceCode));
        assertNotNull(resource, "种子资源应当存在：" + resourceCode);
        return resource.getId();
    }

    /** 调反查接口并把 {@code data} 读成一组行，便于按字段而不是按下标断言 */
    private List<Map<String, Object>> roleSources(String token, long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data");
    }

    private static Map<String, Object> rowOf(List<Map<String, Object>> rows, String roleCode) {
        return rows.stream()
                .filter(row -> roleCode.equals(row.get("roleCode")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("反查结果里没有角色 " + roleCode + "：" + rows));
    }

    private static String groupBody(String code, String name) {
        return """
                {"groupCode":"%s","groupName":"%s"}
                """.formatted(code, name);
    }

    private static String memberBody(String memberType, long memberId, Long from, Long to) {
        return """
                {"memberType":"%s","memberId":%d,"effectiveFrom":%s,"effectiveTo":%s}
                """.formatted(memberType, memberId,
                from == null ? "null" : from.toString(),
                to == null ? "null" : to.toString());
    }

    /**
     * 合法且唯一的用户组编码：{@code E2EG_<前缀><base36>}。
     *
     * <p>前缀以字母开头 —— {@code Long.toString(n, 36)} 可能以数字开头，
     * 而编码要求首字符是字母。直接用时间戳会偶尔被自己的 DTO 校验拦下，
     * 变成一个"大部分时候通过、偶尔失败"的假测试（这条坑在角色侧已经踩过一次）。
     */
    private static String uniqueGroupCode(String prefix) {
        return "E2EG_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueRoleCode(String prefix) {
        return "E2E_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    /** 合法且唯一的用户名：首字符必须是字母 */
    private static String uniqueUsername() {
        return "e2egrp" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueTenantCode() {
        return "e2egt" + Long.toString(System.nanoTime(), 36);
    }

}
