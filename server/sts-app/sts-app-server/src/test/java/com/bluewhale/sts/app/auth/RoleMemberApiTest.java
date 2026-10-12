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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色成员关系（把角色授给用户 / 用户组）的端到端测试。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code RoleApiTest} / {@code ResourceApiTest}
 * 同一理由：本类里最有价值的几条用例 —— 解除后复活同一行、成员类型不匹配时不能误撤、
 * 组链路的二级跳转 —— 验证的都是"库里真正留下了什么、判定真的读到了什么"。
 * 测试事务会让未提交的写在同一事务内可见，把"根本没写进库"这类缺陷完全掩盖。
 *
 * <p><b>与 {@code RoleApiTest} 的关键区别：这里建立成员关系走的是**接口**。</b>
 * 上一片里 {@code RoleApiTest.bind()} 是测试自己经 Mapper 插一行，因为当时
 * 没有接口能把角色授给人；本片之后那条注释里的"测试的取巧"不再必要 ——
 * "授权是否生效"与"成员关系接口是否可用"现在可以一次验证完，
 * 而且**校验链也参与进来**（不存在的主键、跨租户的成员都会被接口挡掉），
 * 这是直接写库永远覆盖不到的。
 *
 * <p><b>本类里没有任何一处直接写库。</b>用户组的管理接口与角色成员关系的接口
 * 现在都已在（前者见 {@code UserGroupApiTest}），所以连"建一个组"这种前置条件
 * 也走接口 —— 上一片里那段"组链路要等用户组管理落地"的注释因此可以删掉了。
 * 前置条件经接口建立还有一个额外收益：**校验链也参与进来**
 * （不存在的主键、跨租户的成员、ID 撞号的类型错配都会被接口挡掉），
 * 这是直接写库永远覆盖不到的。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleMemberApiTest {

    /** 满足口令策略（长度 ≥ 8、含大小写与数字）的测试口令 */
    private static final String TEST_PASSWORD = "Zx9Lmn7Qw2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private ResourceMapper resourceMapper;

    /** 管理员的主键，在 {@link #loginAsAdmin()} 里从 /me 取 */
    private long adminUserId;

    /* ==================== 生命周期与幂等 ==================== */

    @Test
    @DisplayName("授予成员 → 列表可见 → 改时间窗不堆行 → 解除 → 再解除 40408")
    void shouldManageMemberLifecycle() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("mem"), "有成员的角色", "CUSTOM", "ALL");

        // 授予：memberName 由另一张表解析出来（admin 的 displayName）
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", adminUserId, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.memberType").value("USER"))
                .andExpect(jsonPath("$.data.memberTypeLabel").value("用户"))
                .andExpect(jsonPath("$.data.memberId").value((int) adminUserId))
                .andExpect(jsonPath("$.data.memberName")
                        .value(properties.getSeedAdmin().getDisplayName()))
                .andExpect(jsonPath("$.data.effectiveNow").value(true));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].memberType").value("USER"))
                .andExpect(jsonPath("$.data.records[0].effectiveNow").value(true));

        // 同一对 (角色, 成员) 再授一次：必须是 upsert（改这一条的时间窗），不是插出第二条。
        // 若堆成两行，读表的人再也说不清这个人是什么时候被加进来的，
        // 而列表上看起来完全正常
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", adminUserId,
                                System.currentTimeMillis() + 3_600_000L, null)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.effectiveNow").value(false));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].effectiveNow").value(false));

        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/USER/" + adminUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));

        // 解除一段本来就不存在的成员关系必须报 40408：返回成功会让调用方以为"已经撤掉了"，
        // 而真相是从来没授过 —— 两种情况在前端看起来一样，排查方向会被带偏
        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/USER/" + adminUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40408));

        deleteRole(token, roleId);
    }

    /**
     * 解除后再授 —— 复用那一行，而不是留下两条只有 {@code deleted} 不同的记录。
     *
     * <p>这是"先有已软删的行挡着"这一族的第三个实例（资源目录的编码、角色编码、
     * 角色的资源授权），但表现不同：前几处是 **insert 撞唯一键**，这里表上
     * **没有唯一索引**，所以不会报错 —— 它只会安静地多出一行，让
     * {@code total} 慢慢偏离真实成员数。没有断言就发现不了。
     */
    @Test
    @DisplayName("解除后重新授予复用原行，主键不变（不新增记录）")
    void shouldReviveReleasedMemberRelation() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("revm"), "复活成员的角色", "CUSTOM", "ALL");

        MvcResult first = mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", adminUserId, null, null)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long firstGrantId = ((Number) JsonPath.read(
                first.getResponse().getContentAsString(), "$.data.grantId")).longValue();

        unbindMember(token, roleId, "USER", adminUserId);

        MvcResult again = mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", adminUserId, null, null)))
                .andExpect(jsonPath("$.code").value(0))
                // 复活出来的必须是"未删"状态：内存里的 deleted 没清干净时，
                // 后续 UPDATE 的 WHERE 会带上 deleted = 0，影响 0 行且不报错
                .andExpect(jsonPath("$.data.effectiveNow").value(true))
                .andReturn();
        long secondGrantId = ((Number) JsonPath.read(
                again.getResponse().getContentAsString(), "$.data.grantId")).longValue();

        assertEquals(firstGrantId, secondGrantId, "应当复活原行，而不是插入新行");

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1));

        unbindMember(token, roleId, "USER", adminUserId);
        deleteRole(token, roleId);
    }

    /**
     * 成员类型必须参与定位 —— 本类最要紧的一条回归。
     *
     * <p>{@code member_id} 只在它自己所指向的那张表里有意义：用户与用户组是两个
     * **独立的自增序列**，撞号是常态。若撤销只按 {@code memberId} 定位，
     * "撤销给了 ID=5 的用户"会连带把"ID=5 的用户组"也撤掉 ——
     * 而"撤错了"表现为**权限凭空消失**，既没有报错也没有痕迹，回查极难。
     */
    @Test
    @DisplayName("撤销按 (类型, 主键) 定位：类型不匹配时不误撤，且返回 40408")
    void shouldNotRevokeAcrossMemberTypes() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("mix"), "类型区分角色", "CUSTOM", "ALL");
        bindMember(token, roleId, "USER", adminUserId, null, null);

        // 同一个主键、换成 GROUP 类型去撤 —— 那条关系不存在，必须 40408
        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/GROUP/" + adminUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40408));

        // 关键断言：用户那条关系必须**原封不动**
        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].memberType").value("USER"));

        unbindMember(token, roleId, "USER", adminUserId);
        deleteRole(token, roleId);
    }

    /* ==================== 校验 ==================== */

    @Test
    @DisplayName("成员类型非法 / 缺失、成员不存在、时间区间写反都被挡下")
    void shouldRejectBadMemberRequests() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("badm"), "校验角色", "CUSTOM", "ALL");

        // 类型不合法：服务层归一后返回 40001，并把合法取值列进文案
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("ROBOT", adminUserId, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("成员类型")));

        // 类型缺失：@NotBlank 拦在绑定层，因此是公共参数码 40000 而不是域内的 40001 ——
        // 与 resourceId 缺失（@NotNull）走同一条路，这里明确断言以免将来被"统一"掉
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":%d}
                                """.formatted(adminUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        // 成员不存在：多态表刻意没有外键，存在性只能由应用层保证
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", 999_999_999L, null, null)))
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message", containsString("用户")));

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("GROUP", 999_999_999L, null, null)))
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message", containsString("用户组")));

        // 起始晚于结束 = 一段永远不会生效的成员关系，而它在列表里看起来完全正常
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", adminUserId, 2_000_000_000_000L, 1_000_000_000_000L)))
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("起始时间")));

        // 撤销路径上的类型同样要归一 —— 路径变量不过 Bean Validation，只能靠服务层
        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/ROBOT/" + adminUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40001));

        deleteRole(token, roleId);
    }

    /**
     * 跨租户的成员关系必须被拒绝。
     *
     * <p>判定 SQL 的**两侧**都限定 {@code tenant_id}（{@code rm.tenant_id} 与
     * {@code ro.tenant_id}），所以一段跨租户的成员关系**永远匹配不上** ——
     * 它不会报错，只会安静地躺在库里：列表里看得见、权限里没有。
     * 这种"静默不可达的数据"正是接口最不该制造的，与角色"父角色必须同租户"是同一原则。
     */
    @Test
    @DisplayName("跨租户的成员被拒绝（40002），文案点名租户而不是含糊的『不存在』")
    void shouldRejectCrossTenantMember() throws Exception {
        String token = loginAsAdmin();
        long otherTenantId = createTenant(token, uniqueTenantCode());
        long otherUserId = createUserInTenant(token, uniqueUsername(), otherTenantId);
        long roleId = createRole(token, uniqueRoleCode("xten"), "本租户角色", "CUSTOM", "ALL");

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", otherUserId, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                // "不存在"会把调用方引去查主键，而主键是对的 —— 必须点名租户
                .andExpect(jsonPath("$.message", containsString("租户")));

        /* 收尾：用户 → 租户，避免给同一构建里的其它用例留下额外租户 */
        mockMvc.perform(delete("/api/auth/users/" + otherUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/auth/tenants/" + otherTenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        deleteRole(token, roleId);
    }

    /* ==================== 判定链路：成员关系真的生效吗 ==================== */

    /**
     * 成员关系的最终验证 —— 必须落到"这个人到底能不能调那个接口"。
     *
     * <p>本用例第一次让这条链路从**接口**上完整跑通，覆盖三件事：
     * <ol>
     *   <li><b>接口授出的成员关系能赋予权限</b>（此前只能靠种子或测试写库）；</li>
     *   <li><b>时态在成员关系上生效</b>：{@code effective_from / effective_to} 三张表都有，
     *       但此前只有角色-资源那一张真的进过判定 —— 成员关系上的时间窗是**本片才第一次
     *       被验证**的，而它是"临时代理负责人"这个用法的全部依据；</li>
     *   <li><b>解除后立即失效</b>，不需要等任何定时任务。</li>
     * </ol>
     *
     * <p>用真实的 {@code auth:tenant:read} 与真实的 {@code GET /api/auth/tenants}：
     * 只有走完 {@code @RequiresPermission} → {@code AuthInterceptor} → {@code PermissionChecker}
     * 整条链，测到的才是判定，而不是"接口返回了一份列表"。
     */
    @Test
    @DisplayName("接口授出的成员关系赋予权限；时态在成员关系上生效；解除后立即失效")
    void shouldGrantPermissionViaMemberApi() throws Exception {
        String adminToken = loginAsAdmin();
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        String username = uniqueUsername();
        long userId = createUser(adminToken, username);
        String userToken = login(properties.getSeedAdmin().getTenantCode(), username, TEST_PASSWORD);

        String roleCode = uniqueRoleCode("pmem");
        long roleId = createRole(adminToken, roleCode, "只给读租户", "CUSTOM", "ALL");
        grant(adminToken, roleId, resourceId, "ALLOW");

        // 授出之前：没有权限
        expectTenantList(userToken, 40301);

        // 经**接口**把角色授给这个用户：权限立刻到位，/me 里也能看到角色编码
        bindMember(adminToken, roleId, "USER", userId, null, null);
        expectTenantList(userToken, 0);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.data.permissions", hasItem(AuthPermissions.TENANT_READ)))
                .andExpect(jsonPath("$.data.roles", hasItem(roleCode)));

        // 改成一段未来的区间：成员关系还在（列表里看得见），但判定必须当它不存在。
        // 这一条正是"三张表都有时态列、却只有一张真的进过判定"的回归点
        bindMember(adminToken, roleId, "USER", userId,
                System.currentTimeMillis() + 3_600_000L, null);
        expectTenantList(userToken, 40301);

        // 改回长期有效：权限必须回来（清时间窗靠逐列显式 set，updateById 的 NOT_NULL 策略清不掉）
        bindMember(adminToken, roleId, "USER", userId, null, null);
        expectTenantList(userToken, 0);

        // 解除：立刻失效
        unbindMember(adminToken, roleId, "USER", userId);
        expectTenantList(userToken, 40301);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.data.roles", not(hasItem(roleCode))));

        deleteRole(adminToken, roleId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 用户组链路 —— **二级跳转此前从未被验证过**。
     *
     * <p>判定 SQL 里有一段 {@code member_type = 'GROUP' AND member_id IN (SELECT ... FROM
     * bw_auth_rel_user_group_member ...)}：用户 → 用户组 → 角色 → 资源。
     * 它写在那里很久了，但从来没有任何东西走过它 —— 用户组的接口不存在，
     * 角色成员关系的接口也不存在，所以这段子查询一直没有真实入口。
     * 本片补上了后一半，这条链才第一次可以被端到端断言。
     *
     * <p>用户组与组-用户关系**现在都经接口建立**（建组、加成员都是真实调用），
     * 上一片里"用户组管理是另一片的事、只好硬写夹具"的理由已经不存在了。
     * 断言仍然落在**判定链路**上：这个组最终有没有让那个人拿到权限。
     * 用户组接口自身的用例在 {@code UserGroupApiTest}，两边分工不重复。
     */
    @Test
    @DisplayName("组链路：用户 → 用户组 → 角色 → 资源；组关系过期同样立即失效")
    void shouldGrantPermissionViaUserGroup() throws Exception {
        String adminToken = loginAsAdmin();
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        String username = uniqueUsername();
        long userId = createUser(adminToken, username);
        String userToken = login(properties.getSeedAdmin().getTenantCode(), username, TEST_PASSWORD);

        long groupId = createGroup(adminToken, "读租户组");
        bindGroupMember(adminToken, groupId, userId, null, null);

        long roleId = createRole(adminToken, uniqueRoleCode("grp"), "组角色", "CUSTOM", "ALL");
        grant(adminToken, roleId, resourceId, "ALLOW");
        // 成员关系走接口，成员是一个**用户组** —— 组级授权是本片才第一次能配的东西
        bindMember(adminToken, roleId, "GROUP", groupId, null, null);

        // 二级跳转生效：这个用户自己没有角色，权限是从组里来的
        expectTenantList(userToken, 0);

        // 组-用户关系过期：判定里那段子查询同样有时态过滤，权限必须消失
        long now = System.currentTimeMillis();
        bindGroupMember(adminToken, groupId, userId, now - 7_200_000L, now - 3_600_000L);
        expectTenantList(userToken, 40301);

        /* 收尾：先解除角色成员关系才能删角色（有成员时删除被拒绝，这正是角色侧的守卫） */
        unbindMember(adminToken, roleId, "GROUP", groupId);
        deleteRole(adminToken, roleId);
        unbindGroupMember(adminToken, groupId, userId);
        deleteGroup(adminToken, groupId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("被停用的角色即使持有成员也不产生权限（成员关系还在，权限没了）")
    void shouldNotProducePermissionFromDisabledRole() throws Exception {
        String adminToken = loginAsAdmin();
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        String username = uniqueUsername();
        long userId = createUser(adminToken, username);
        String userToken = login(properties.getSeedAdmin().getTenantCode(), username, TEST_PASSWORD);

        String roleCode = uniqueRoleCode("dis");
        long roleId = createRole(adminToken, roleCode, "将被停用", "CUSTOM", "ALL");
        grant(adminToken, roleId, resourceId, "ALLOW");
        bindMember(adminToken, roleId, "USER", userId, null, null);
        expectTenantList(userToken, 0);

        mockMvc.perform(put("/api/auth/roles/" + roleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"将被停用","category":"CUSTOM",
                                 "dataScope":"ALL","status":"DISABLED"}
                                """.formatted(roleCode)))
                .andExpect(jsonPath("$.code").value(0));
        expectTenantList(userToken, 40301);

        // 成员关系本身**没有**被改动：列表里仍在，且时态上仍然生效 ——
        // "角色停用"与"成员关系失效"是两件事，混在一起会让"为什么没权限"无从查起
        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].effectiveNow").value(true));

        unbindMember(adminToken, roleId, "USER", userId);
        deleteRole(adminToken, roleId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 权限守卫 ==================== */

    @Test
    @DisplayName("审计员没有角色成员权限：读也读不到，写更是 40301")
    void shouldDenyAuditor() throws Exception {
        String adminToken = loginAsAdmin();
        long roleId = createRole(adminToken, uniqueRoleCode("aud"), "审计员不可见", "CUSTOM", "ALL");
        String auditorToken = loginAsAuditor();

        // 成员列表等于"这个权限载体发给了谁"，比角色列表更敏感，
        // 与角色列表本身一样不默认对只读账号开放
        mockMvc.perform(get("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", adminUserId, null, null)))
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/USER/" + adminUserId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(jsonPath("$.code").value(40301));

        deleteRole(adminToken, roleId);
    }

    /* ==================== 辅助 ==================== */

    private String loginAsAdmin() throws Exception {
        String token = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
        // 管理员的主键从 /me 拿，而不是写死 1：写死会在种子策略变化时
        // 静默指向别的身份，让"给角色挂成员"这个前置条件悄悄失效
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String bodyText = me.getResponse().getContentAsString();
        adminUserId = ((Number) JsonPath.read(bodyText, "$.data.userId")).longValue();
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

    /** 断言一次真实的准入结果：{@code code=0} 表示放行，非 0 表示被 {@code AuthInterceptor} 拦下 */
    private void expectTenantList(String token, int expectedCode) throws Exception {
        mockMvc.perform(get("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode));
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

    private long createUser(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"成员关系测试用户","password":"%s"}
                                """.formatted(username, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createUserInTenant(String token, String username, long tenantId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"别租户用户","password":"%s"}
                                """.formatted(tenantId, username, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createTenant(String token, String tenantCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"成员关系测试租户"}
                                """.formatted(tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
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

    private long bindMember(String token, long roleId, String memberType, long memberId,
                            Long from, Long to) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody(memberType, memberId, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.grantId");
        return id.longValue();
    }

    private void unbindMember(String token, long roleId, String memberType, long memberId)
            throws Exception {
        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/members/" + memberType + "/" + memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 建一个用户组（经接口）。用户组管理落地之后，夹具不再必要 */
    private long createGroup(String token, String groupName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupCode":"%s","groupName":"%s"}
                                """.formatted(uniqueGroupCode(), groupName)))
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

    /** 把用户加进组。传一段过去的区间就是"组成员关系已过期"这一场景 */
    private void bindGroupMember(String token, long groupId, long userId, Long from, Long to)
            throws Exception {
        mockMvc.perform(post("/api/auth/user-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody("USER", userId, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void unbindGroupMember(String token, long groupId, long userId) throws Exception {
        mockMvc.perform(delete("/api/auth/user-groups/" + groupId + "/members/USER/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long resourceIdOf(String resourceCode) {
        ResourceEntity resource = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, resourceCode));
        assertNotNull(resource, "种子资源应当存在：" + resourceCode);
        return resource.getId();
    }

    private static String bindBody(String memberType, long memberId, Long from, Long to) {
        return """
                {"memberType":"%s","memberId":%d,"effectiveFrom":%s,"effectiveTo":%s}
                """.formatted(memberType, memberId,
                from == null ? "null" : from.toString(),
                to == null ? "null" : to.toString());
    }

    /**
     * 合法且唯一的角色编码：{@code E2E_<前缀><base36>}。
     *
     * <p>前缀以字母开头、整体只用字母数字与下划线：{@code Long.toString(n, 36)}
     * 可能以数字开头，而角色编码要求首字符是字母 —— 直接用时间戳会偶尔被自己的
     * DTO 校验拦下，变成一个"大部分时候通过、偶尔失败"的假测试。
     */
    private static String uniqueRoleCode(String prefix) {
        return "E2E_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    /** 合法且唯一的用户名：首字符必须是字母 */
    private static String uniqueUsername() {
        return "e2emem" + Long.toString(System.nanoTime(), 36);
    }

    /** 合法且唯一的租户编码：首字符必须是字母，长度 ≥ 2 */
    private static String uniqueTenantCode() {
        return "e2et" + Long.toString(System.nanoTime(), 36);
    }

    /** 合法且唯一的用户组编码：首字符必须是字母 */
    private static String uniqueGroupCode() {
        return "E2EGRP" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

}
