package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.enums.MemberType;
import com.bluewhale.sts.auth.relation.RelRoleMemberEntity;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import com.bluewhale.sts.auth.resource.ResourceMapper;
import com.bluewhale.sts.auth.role.RoleEntity;
import com.bluewhale.sts.auth.role.RoleMapper;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色管理与角色资源授权的端到端测试。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code ResourceApiTest} / {@code PasswordHardeningApiTest}
 * 同一理由：本类里最有价值的两条用例 —— "编码被软删角色占用时复活"与"DENY 跨角色生效" ——
 * 验证的都是"库里真正留下了什么、判定真的读到了什么"。测试事务会让未提交的写在同一事务内可见，
 * 把前者的缺陷（表现为 500 而不是业务码）完全掩盖。
 *
 * <p>代价是用例会真的写库（h2 内存库，随测试进程消失），因此编码 / 用户名一律带时间戳后缀，
 * 并仍然满足各自 DTO 上的形状约束 —— 否则测到的是校验注解，不是被测逻辑。
 *
 * <p><b>本类里有一条"测试自己写库"的地方</b>：给角色挂成员。角色成员关系
 * （{@code bw_auth_rel_role_member}）的接口不在本切片范围内（属用户组 / 组织那一批），
 * 但"授权到底生效没有"只能通过"某个人最终有哪些权限"来回答。所以这里直接经 Mapper
 * 插一条成员关系 —— 与 {@code DefaultAdminSeeder} 的做法一致，且这是**故意的**：
 * 它把"角色管理接口"与"授权是否真的影响判定"这两件事分开验证，
 * 后者失败时不会被前者的实现细节干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleApiTest {

    /** 满足口令策略（长度 ≥ 8、含大小写与数字）的测试口令 */
    private static final String TEST_PASSWORD = "Zx9Lmn7Qw2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private RelRoleMemberMapper relRoleMemberMapper;

    /** 管理员的主键与租户，在 {@link #loginAsAdmin()} 里从 /me 取 */
    private long adminUserId;
    private long adminTenantId;

    /* ==================== 角色 CRUD ==================== */

    @Test
    @DisplayName("建角色 → 查详情 → 改名 → 分页可见 → 删除")
    void shouldManageRoleLifecycle() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueRoleCode("biz");

        long id = createRole(token, code, "业务角色", "BUSINESS", "SELF");

        mockMvc.perform(get("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value(code))
                .andExpect(jsonPath("$.data.roleName").value("业务角色"))
                .andExpect(jsonPath("$.data.category").value("BUSINESS"))
                .andExpect(jsonPath("$.data.dataScope").value("SELF"))
                // 顶层角色按约定落到 1，而不是留空 —— 留空的层级列排序时会跑到最前
                .andExpect(jsonPath("$.data.roleLevel").value(1))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        // 改名，同时在请求体里塞一个**不同的 roleCode** —— 它必须被忽略。
        // 这条断言是刻意的：roleCode 是对外稳定标识，接口"顺手"写进去就会变成一个
        // 能改编码的接口，而改编码造成的失效是静默的（外部引用还在用旧编码）
        mockMvc.perform(put("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueRoleCode("other"), "改过名的角色", "BUSINESS", "SELF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value(code))
                .andExpect(jsonPath("$.data.roleName").value("改过名的角色"));

        MvcResult pageResult = mockMvc.perform(get("/api/auth/roles")
                        .param("keyword", code)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        // 用 Jayway 读回来再比对，而不是把数字塞进 MockMvc 的 jsonPath 表达式：
        // 数字在 JSON 里是 Integer 还是 Long 取决于大小，两边类型不一致时断言会假失败
        List<Integer> pageIds = JsonPath.read(pageResult.getResponse().getContentAsString(),
                "$.data.records[*].id");
        assertTrue(pageIds.contains((int) id), "分页结果应包含刚建的角色，实际：" + pageIds);

        mockMvc.perform(delete("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 删完之后详情应当 40403，而不是"还在但看不见"
        mockMvc.perform(get("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40403));
    }

    @Test
    @DisplayName("编码相同的角色重复创建返回 40906，而不是撞唯一键落到兜底的 40900")
    void shouldRejectDuplicateRoleCode() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueRoleCode("dup");
        createRole(token, code, "重复角色", "CUSTOM", "ALL");

        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "另一个名字", "CUSTOM", "ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40906));
    }

    /**
     * 软删复活 —— 本类最重要的一条回归。
     *
     * <p>唯一索引 {@code uk_bw_auth_role_tenant_code} 建在 {@code (tenant_id, role_code)} 上、
     * 不含 {@code deleted}，而 MyBatis-Plus 给所有自动查询追加 {@code deleted = 0}：
     * 常规判重看不见软删行 → 判重通过 → insert 撞唯一键 → 落到兜底的 40900
     * （{@code ADR-0024} 之前是 500）。
     *
     * <p>复活还必须**带上"没有权限"这个语义**：删角色时会一并撤销它的授权，
     * 复活出来的角色不能把旧权限静默带回来。
     */
    @Test
    @DisplayName("软删过的编码可以复用：复活原行，且不带回旧授权（回归点）")
    void shouldReviveSoftDeletedRoleCode() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueRoleCode("revive");

        long firstId = createRole(token, code, "将会被删的角色", "CUSTOM", "ALL");
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);
        grant(token, firstId, resourceId, "ALLOW");

        mockMvc.perform(get("/api/auth/roles/" + firstId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(delete("/api/auth/roles/" + firstId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        // 同一个编码再建 —— 必须成功，且是"复活"同一行（主键不变）
        MvcResult revived = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "复活后的角色", "CUSTOM", "ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number revivedId = JsonPath.read(revived.getResponse().getContentAsString(), "$.data.id");
        assertEquals(firstId, revivedId.longValue(), "应当复活原行，而不是插入新行");

        // 复活出来的必须是空角色：旧授权不能被静默带回来
        mockMvc.perform(get("/api/auth/roles/" + firstId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("角色仍被成员持有时拒绝删除（40916）")
    void shouldRejectDeleteWhenRoleHasMembers() throws Exception {
        String token = loginAsAdmin();
        long id = createRole(token, uniqueRoleCode("held"), "有成员的角色", "CUSTOM", "ALL");

        RelRoleMemberEntity member = new RelRoleMemberEntity();
        member.setTenantId(currentTenantId());
        member.setRoleId(id);
        member.setMemberId(currentUserId());
        member.setMemberType(MemberType.USER.wireName());
        relRoleMemberMapper.insert(member);

        mockMvc.perform(delete("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40916))
                .andExpect(jsonPath("$.message", containsString("成员")));

        // 收尾：先解除成员关系，再删角色，避免残留角色影响同一构建里的其它用例
        relRoleMemberMapper.delete(Wrappers.<RelRoleMemberEntity>lambdaQuery()
                .eq(RelRoleMemberEntity::getRoleId, id));
        deleteRole(token, id);
    }

    @Test
    @DisplayName("有子角色时拒绝删除（40913），删掉子角色后同一个父级立刻可删")
    void shouldRejectDeleteWhenRoleHasChildren() throws Exception {
        String token = loginAsAdmin();
        long parentId = createRole(token, uniqueRoleCode("prt"), "父角色", "BUSINESS", "ALL");
        long childId = createRoleUnder(token, uniqueRoleCode("chd"), "子角色", parentId);

        mockMvc.perform(delete("/api/auth/roles/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message", containsString("子角色")));

        // 删掉最后一个子角色之后，**同一个父级必须立刻可删**。
        // 少了这一半，用例证明的只是"删除有时会失败"，而不是"拒绝的理由是子角色" ——
        // 一个"看到 parentId 非空就拒绝"的实现也能通过前半段。
        deleteRole(token, childId);
        deleteRole(token, parentId);
    }

    @Test
    @DisplayName("成环的两条路径都拒绝（40913），而合法的移动仍成功")
    void shouldRejectSelfOrDescendantParent() throws Exception {
        String token = loginAsAdmin();
        long grandId = createRole(token, uniqueRoleCode("gprt"), "祖父角色", "BUSINESS", "ALL");
        long childId = createRoleUnder(token, uniqueRoleCode("gchd"), "子角色", grandId);

        // ① 挂到自己下面 —— 守卫里 `parent.getId().equals(self.getId())` 那一支
        mockMvc.perform(put("/api/auth/roles/" + grandId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithParent(uniqueRoleCode("gprt"), "祖父角色", "BUSINESS", "ALL", grandId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message", containsString("自己")));

        // ② 挂到自己的下级下面 —— 沿 parent_id 上溯那一支。
        // 这一支才是"环"本身：① 只是自指，② 才需要走完整条祖先链才判得出来
        mockMvc.perform(put("/api/auth/roles/" + grandId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithParent(uniqueRoleCode("gprt"), "祖父角色", "BUSINESS", "ALL", childId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message", containsString("下级")));

        // ③ 合法移动：把子角色挪到另一个**无关的**顶层角色下面，必须成功。
        // 少了这一半，前两条只能证明"带上 parentId 的修改就会失败"。
        long otherId = createRole(token, uniqueRoleCode("goth"), "另一个顶层角色", "BUSINESS", "ALL");
        mockMvc.perform(put("/api/auth/roles/" + childId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithParent(uniqueRoleCode("gchd"), "子角色", "BUSINESS", "ALL", otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.parentId").value(otherId));

        // 收尾：先子后父，避免残留角色影响同一构建里的其它用例
        deleteRole(token, childId);
        deleteRole(token, otherId);
        deleteRole(token, grandId);
    }

    @Test
    @DisplayName("系统内置角色不可删除、不可停用、不可降级分类（40917）")
    void shouldProtectSystemRole() throws Exception {
        String token = loginAsAdmin();
        RoleEntity systemRole = firstSystemRole();
        assertNotNull(systemRole, "种子数据应当有 category=SYSTEM 的角色");

        mockMvc.perform(delete("/api/auth/roles/" + systemRole.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40917));

        mockMvc.perform(put("/api/auth/roles/" + systemRole.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s","category":"SYSTEM","dataScope":"ALL","status":"DISABLED"}
                                """.formatted(systemRole.getRoleCode(), systemRole.getRoleName())))
                .andExpect(jsonPath("$.code").value(40917));

        // 改分类本身也是一条绕过路径：先降级为 CUSTOM，就可以接着删除或停用
        mockMvc.perform(put("/api/auth/roles/" + systemRole.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s","category":"CUSTOM","dataScope":"ALL","status":"ENABLED"}
                                """.formatted(systemRole.getRoleCode(), systemRole.getRoleName())))
                .andExpect(jsonPath("$.code").value(40917));
    }

    @Test
    @DisplayName("数据范围与标志位的非法取值返回 40001，而不是静默写入")
    void shouldRejectInvalidEnumerations() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"坏档位","dataScope":"EVERYTHING"}
                                """.formatted(uniqueRoleCode("bad"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("数据范围")));

        // isDefault=7 这类值放行不会报错，只会静默改变语义 —— 必须在入口挡住
        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"坏标志位","isDefault":7}
                                """.formatted(uniqueRoleCode("bad"))))
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("isDefault")));
    }

    /* ==================== 角色资源授权 ==================== */

    @Test
    @DisplayName("授权 → 列表可见 → 重复授权改效果而不是堆行 → 撤销 → 再撤 40407")
    void shouldGrantRevokeAndListResources() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("grant"), "授权角色", "CUSTOM", "ALL");
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(resourceId, "ALLOW", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resourceCode").value(AuthPermissions.TENANT_READ))
                // API 类型参与后端准入；这个派生字段让"授了菜单怎么还是没权限"不再费解
                .andExpect(jsonPath("$.data.requestScoped").value(true))
                .andExpect(jsonPath("$.data.effectiveNow").value(true));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].resourceCode").value(AuthPermissions.TENANT_READ))
                .andExpect(jsonPath("$.data[0].effect").value("ALLOW"));

        // 同键再授一次：应当是 upsert（改这一条），不是插出第二条。
        // 若插成第二条，判定会按资源聚合、让历史的那条 DENY 复活，
        // 把"改成拒绝"的效果反转成"这条授权忽然不生效"
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(resourceId, "DENY", null, null)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.effect").value("DENY"));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].effect").value("DENY"));

        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/resources/" + resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 撤一条本来就没授过的授权必须报 40407：返回成功会让调用方以为"已经撤掉了"，
        // 而真相是从来没授过 —— 两种情况在前端看起来一样，排查方向会被带偏
        mockMvc.perform(delete("/api/auth/roles/" + roleId + "/resources/" + resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40407));

        deleteRole(token, roleId);
    }

    @Test
    @DisplayName("授权校验：资源不存在 40002、时间区间写反 40001、效果非法 40001")
    void shouldRejectBadGrantRequests() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("chk"), "校验角色", "CUSTOM", "ALL");

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(999_999_999L, "ALLOW", null, null)))
                .andExpect(jsonPath("$.code").value(40002));

        // 起始晚于结束 = 一条永远不会生效的授权，而它在列表里看起来完全正常
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(resourceIdOf(AuthPermissions.TENANT_READ), "ALLOW",
                                2_000_000_000_000L, 1_000_000_000_000L)))
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(resourceIdOf(AuthPermissions.TENANT_READ), "MAYBE", null, null)))
                .andExpect(jsonPath("$.code").value(40001));

        deleteRole(token, roleId);
    }

    @Test
    @DisplayName("未生效的授权在列表里被标为 effectiveNow=false")
    void shouldMarkInactiveGrant() throws Exception {
        String token = loginAsAdmin();
        long roleId = createRole(token, uniqueRoleCode("win"), "时态角色", "CUSTOM", "ALL");
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(resourceId, "ALLOW", System.currentTimeMillis() + 3_600_000L, null)))
                .andExpect(jsonPath("$.data.effectiveNow").value(false));

        mockMvc.perform(get("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].effectiveNow").value(false));

        deleteRole(token, roleId);
    }

    /* ==================== 判定链路：授权真的生效吗 ==================== */

    /**
     * 授权的最终验证 —— 必须落到"这个人到底能不能调那个接口"。
     *
     * <p>本用例覆盖三件本切片才真正成立的事：
     * <ol>
     *   <li><b>授权能赋予权限</b>（此前只能靠种子数据）；</li>
     *   <li><b>DENY 跨角色优先</b>：角色 A 允许、角色 B 拒绝，结果是拒绝。
     *       旧实现只把 DENY <b>行</b>从 JOIN 里滤掉，A 那一行照样通过，
     *       于是"显式拒绝"只在同一个角色内成立 —— 而跨角色才是它唯一的用途；</li>
     *   <li><b>停用的角色与过期的授权都不再产生权限</b>：这两条都是"点了按钮但没效果"，
     *       不落到真实请求上根本看不出来。</li>
     * </ol>
     *
     * <p>用真实的 {@code auth:tenant:read} 与真实的 {@code GET /api/auth/tenants}，
     * 而不是随便造一个资源标识去断言 {@code /me} 的内容：只有走完
     * {@code @RequiresPermission} → {@code AuthInterceptor} → {@code PermissionChecker}
     * 整条链，测到的才是判定，而不是"接口返回了一份列表"。
     */
    @Test
    @DisplayName("授权赋予权限；DENY 跨角色优先；停用角色与过期授权立即失效")
    void shouldHonorGrantDenyAndRoleStatus() throws Exception {
        String adminToken = loginAsAdmin();
        long resourceId = resourceIdOf(AuthPermissions.TENANT_READ);

        String username = uniqueUsername();
        long userId = createUser(adminToken, username);
        String userToken = login(properties.getSeedAdmin().getTenantCode(), username, TEST_PASSWORD);

        long allowRoleId = createRole(adminToken, uniqueRoleCode("allow"), "允许读租户", "CUSTOM", "ALL");
        long denyRoleId = createRole(adminToken, uniqueRoleCode("deny"), "拒绝读租户", "CUSTOM", "ALL");
        grant(adminToken, allowRoleId, resourceId, "ALLOW");

        // 只挂允许角色：能读
        bind(allowRoleId, MemberType.USER, userId);
        expectTenantList(userToken, 0);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.data.permissions", hasItem(AuthPermissions.TENANT_READ)))
                .andExpect(jsonPath("$.data.roles", hasItem(roleCodeOf(allowRoleId))));

        // 再挂一个"同资源显式拒绝"的角色：结果必须是拒绝（DENY 跨角色优先）
        grant(adminToken, denyRoleId, resourceId, "DENY");
        bind(denyRoleId, MemberType.USER, userId);
        expectTenantList(userToken, 40301);

        // 撤掉那条 DENY：立刻恢复
        mockMvc.perform(delete("/api/auth/roles/" + denyRoleId + "/resources/" + resourceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
        expectTenantList(userToken, 0);

        // 把允许改成一段已经过去的区间：时态过滤应当在 SQL 里生效，而不是只写在字段上
        grant(adminToken, allowRoleId, resourceId, "ALLOW",
                System.currentTimeMillis() - 3_600_000L, System.currentTimeMillis() - 60_000L);
        expectTenantList(userToken, 40301);

        // 恢复长期有效，再停用角色：停用的角色同样不能继续产生权限。
        // 这一条曾经漏掉 —— 角色管理接口落地后"停用角色"成了一个可点的操作，
        // 漏掉它就意味着点完之后权限照旧
        grant(adminToken, allowRoleId, resourceId, "ALLOW");
        expectTenantList(userToken, 0);
        mockMvc.perform(put("/api/auth/roles/" + allowRoleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"允许读租户","category":"CUSTOM",
                                 "dataScope":"ALL","status":"DISABLED"}
                                """.formatted(roleCodeOf(allowRoleId))))
                .andExpect(jsonPath("$.code").value(0));
        expectTenantList(userToken, 40301);

        /* 收尾：解除成员关系后才能删角色（有成员时删除会被拒绝，这正是本类的守卫） */
        unbind(allowRoleId, MemberType.USER, userId);
        unbind(denyRoleId, MemberType.USER, userId);
        deleteRole(adminToken, allowRoleId);
        deleteRole(adminToken, denyRoleId);
        mockMvc.perform(delete("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("审计员没有角色管理权限：读也读不到（40301）")
    void shouldDenyAuditor() throws Exception {
        String token = loginAsAuditor();

        // 角色列表等价于"系统里有哪些权限载体"，与资源目录同级敏感，
        // 不该默认对只读账号开放 —— 与 ResourceApiTest 对资源目录的处理一致
        mockMvc.perform(get("/api/auth/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueRoleCode("deny"), "越权新建", "CUSTOM", "ALL")))
                .andExpect(jsonPath("$.code").value(40301));
    }

    /* ==================== 辅助 ==================== */

    private String loginAsAdmin() throws Exception {
        String token = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
        // 管理员的主键与租户从 /me 拿，而不是写死 1 / 查库推。
        // 写死会在种子策略变化（换用户名、多租户种子）时静默指向别的身份，
        // 让"给角色挂成员"这个前置条件悄悄失效，而失败点会跑到很远的地方
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String bodyText = me.getResponse().getContentAsString();
        adminUserId = ((Number) JsonPath.read(bodyText, "$.data.userId")).longValue();
        adminTenantId = ((Number) JsonPath.read(bodyText, "$.data.tenantId")).longValue();
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
                        .content(body(code, name, category, dataScope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 建一个挂在 {@code parentId} 下面的角色 —— 层级守卫的两条用例都要先摆出父子关系 */
    private long createRoleUnder(String token, String code, String name, long parentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithParent(code, name, "BUSINESS", "ALL", parentId)))
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
                                {"username":"%s","displayName":"角色授权测试用户","password":"%s"}
                                """.formatted(username, TEST_PASSWORD)))
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
                        .content(grantBody(resourceId, effect, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void grant(String token, long roleId, long resourceId, String effect,
                       Long from, Long to) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(resourceId, effect, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 直接经 Mapper 建立成员关系。
     *
     * <p>成员关系的接口不在本切片内（属用户组 / 组织那一批），但"授权是否真的生效"
     * 只能通过"某个人最终有哪些权限"回答。这里刻意不绕过判定链路去断言数据库 ——
     * 写的是成员关系（输入），断言的是接口的准入结果（输出）。
     */
    private void bind(long roleId, MemberType memberType, long memberId) {
        RelRoleMemberEntity member = new RelRoleMemberEntity();
        member.setTenantId(currentTenantId());
        member.setRoleId(roleId);
        member.setMemberId(memberId);
        member.setMemberType(memberType.wireName());
        relRoleMemberMapper.insert(member);
    }

    private void unbind(long roleId, MemberType memberType, long memberId) {
        relRoleMemberMapper.delete(Wrappers.<RelRoleMemberEntity>lambdaQuery()
                .eq(RelRoleMemberEntity::getRoleId, roleId)
                .eq(RelRoleMemberEntity::getMemberType, memberType.wireName())
                .eq(RelRoleMemberEntity::getMemberId, memberId));
    }

    private long resourceIdOf(String resourceCode) {
        ResourceEntity resource = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, resourceCode));
        assertNotNull(resource, "种子资源应当存在：" + resourceCode);
        return resource.getId();
    }

    private String roleCodeOf(long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        assertNotNull(role, "角色应当存在：" + roleId);
        return role.getRoleCode();
    }

    private RoleEntity firstSystemRole() {
        List<RoleEntity> roles = roleMapper.selectList(Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getCategory, "SYSTEM")
                .orderByAsc(RoleEntity::getId));
        assertTrue(!roles.isEmpty(), "种子数据应当有 category=SYSTEM 的角色");
        return roles.get(0);
    }

    private Long currentTenantId() {
        return adminTenantId;
    }

    private Long currentUserId() {
        return adminUserId;
    }

    private static String body(String code, String name, String category, String dataScope) {
        return """
                {"roleCode":"%s","roleName":"%s","category":"%s","dataScope":"%s"}
                """.formatted(code, name, category, dataScope);
    }

    /**
     * 带 {@code parentId} 的请求体。
     *
     * <p>{@code roleCode} 在修改路径上**会被忽略**（对外稳定标识），但注解校验仍会跑，
     * 所以这里照旧填一个形状合法的编码 —— 与 {@link #shouldManageRoleLifecycle}
     * 里"塞一个不同的 roleCode 验证它被忽略"是同一件事。
     */
    private static String bodyWithParent(String code, String name, String category,
                                         String dataScope, long parentId) {
        return """
                {"roleCode":"%s","roleName":"%s","category":"%s","dataScope":"%s","parentId":%d}
                """.formatted(code, name, category, dataScope, parentId);
    }

    private static String grantBody(long resourceId, String effect, Long from, Long to) {
        return """
                {"resourceId":%d,"effect":"%s","effectiveFrom":%s,"effectiveTo":%s}
                """.formatted(resourceId, effect,
                from == null ? "null" : from.toString(),
                to == null ? "null" : to.toString());
    }

    /**
     * 合法且唯一的角色编码：{@code E2E_<前缀><base36>}。
     *
     * <p>前缀刻意以字母开头、整体只用字母数字与下划线：{@code Long.toString(n, 36)}
     * 可能以数字开头，而角色编码要求首字符是字母 —— 直接用时间戳会偶尔被自己的
     * DTO 校验拦下，变成一个"大部分时候通过、偶尔失败"的假测试。
     */
    private static String uniqueRoleCode(String prefix) {
        return "E2E_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    /** 合法且唯一的用户名：首字符必须是字母 */
    private static String uniqueUsername() {
        return "e2euser" + Long.toString(System.nanoTime(), 36);
    }

}
