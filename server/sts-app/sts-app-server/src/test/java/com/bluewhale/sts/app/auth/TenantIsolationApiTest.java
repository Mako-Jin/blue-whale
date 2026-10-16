package com.bluewhale.sts.app.auth;

import com.bluewhale.sts.auth.common.AuthProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户内数据隔离：一个租户的管理员**看不到也改不了**另一个租户的数据（ADR-0074）。
 *
 * <p><b>起点是 {@code 0051}「未覆盖」的最后一条</b>：*"接口能不能指定别家（存在的、启用的）
 * 租户：仍是 {@code 0050} 未覆盖 1 的正题，需要产品决定（平台级管理台 vs 租户级管理台）。"*
 * 本片把它答了：**两者都要 —— 平台级是唯一顶级租户的成员，其余一律锁死在自己租户内。**
 *
 * <p><b>改前的形态是一整片全通。</b>auth 域五个业务域（用户 / 角色 / 组织 / 用户组 / 租户）
 * 的租户约束全部是"只验存在、不验归属"：
 * <ol>
 *   <li>四个域的 {@code resolveTenantId(requested)} 在 {@code requested != null} 时
 *       **照单全收**（只调 {@code tenantService.assertExists}）⇒ `GET /api/auth/users?tenantId=乙`
 *       列出乙的用户；`POST` 带 {@code tenantId: 乙} 直接在乙租户里建号；</li>
 *   <li>五处 {@code requireXxx(id)} 都是裸 {@code selectById} ⇒ 路径上的主键换成别家的，
 *       详情 / 修改 / 删除 / **重置口令**全部照做；</li>
 *   <li>租户域自己没有隔离 ⇒ 任何持 {@code auth:tenant:*} 的账号列出、修改、停用、删除**所有**租户。</li>
 * </ol>
 * 三条合起来的后果：**持有 {@code auth:user:update} 的甲租户管理员可以重置乙租户管理员的口令，
 * 然后以他的身份登录。** 这不是"多看了几行"，是把租户这道墙整体让掉。
 *
 * <p><b>判据沿用 `0025`（审计域已落地的那一套），而不是新发明一种：</b>
 * <ul>
 *   <li><b>列表 / 树</b>：非平台级操作者的租户条件**恒等于自己**，传入的 {@code tenantId}
 *       被**忽略**（{@code 0025} 第 1/3 条：租户条件不是"可关的写法"；一次越权尝试
 *       只该安静地返回他自己有权看的那一份 —— "被忽略"与"筛出空结果"在调用方看来同形）；</li>
 *   <li><b>详情 / 修改 / 删除 / 重置口令</b>：跨租户一律返回**该对象的 {@code 404}**，
 *       不是 {@code 403}（{@code 0025} 第 2 条）—— {@code 403} 等于承认"这条记录确实存在，
 *       只是不给你看"，而"它存在"这件事本身就是别人的信息。（{@code 0074} 当时把这条理由
 *       写成了"在抛出点统一换成一句带后缀的覆盖文案"，{@code 0075} 把那一半撤回：
 *       **覆盖文案本身就违反它要守的那条理由** —— 见第六节。）</li>
 *   <li><b>写入路径带别家 {@code tenantId}</b>：显式拒绝（{@code 40002} +
 *       与"引用的租户不存在"**逐字相同**的一句话）——
 *       这里**不能**用"静默忽略"那一支：把"我要求在乙租户建号"悄悄变成"在甲租户建了一个号"
 *       比拒绝更糟（调用方以为建成的地方与实际不符）。{@code tenantId} 在 query 里是**过滤条件**、
 *       在 create 里是**归属声明**，两者处置不同正是这条分界线。</li>
 * </ul>
 *
 * <p><b>「平台级」的判据是结构性的：操作者所在租户是顶级租户（{@code parent_id IS NULL}）。</b>
 * 本仓的形态就是种子租户那一个（{@code DefaultAdminSeeder} 写的 {@code tenantLevel = 1}、
 * {@code codePath = "/"}），而 {@code 0058} 早已把"另一个租户里持有 {@code auth:tenant:update}
 * 的账号（本仓的形态就是种子租户的管理员）"当作**既有的**跨租户恢复入口。
 * 不新增权限码：层级位置是**可从库里读出来的事实**，再加一个"平台管理员"码只会造出
 * 两个都必须对上的开关。闭合靠一条：**非平台级不能建顶级租户**（{@code 40302}），
 * 否则"建一个顶级租户 = 给自己升平台级"。
 *
 * <p><b>本片的夹具刻意都建在非顶级租户里</b>（A、B 都是种子租户的子租户）：种子账号是平台级，
 * 用它做操作者**测不出任何隔离** —— 改前改后都是 {@code code: 0}。
 * 所以每条用例的操作者都是一个"自建租户里的、被授予了所需权限码的"账号，
 * 它的权限是**真的**（不是靠 {@code @RequiresPermission} 拦出来的 403）。
 *
 * <p><b>平台级那一侧也要钉</b>（{@link #platformTenantStaysCrossTenantCapable()}）：
 * 少了它，一个"谁都别跨租户"的实现也能让全部用例通过 —— 而它会同时废掉
 * {@code 0058} 的恢复入口与全部跨租户预配（{@code TenantDisableApiTest} 的前两例就是那种用法）。
 *
 * <p><b>第六节补的是"这一族接口对外说什么"</b>（ADR-0075）。前五节全部只断 {@code code}，
 * 而 `0059` 第四节早已立过一条：{@code 404xx} 家族里**文案断言是"这个码要拆了"的哨兵**。
 * 本片把它用起来，断的不是"文案等于哪一句字面量"，而是一条更强的性质 ——
 * **非平台级读者分不出"不存在"与"存在但不属于当前租户"**：
 * 同一个码的两条分支必须给出**同一句话**（只差被带进文案的那个主键）。
 * **顺序不可反 —— 先"两支同码"，才谈得上"两支同话"。** 负责人族（{@code manager_id}）
 * 改前是"谁也不是 → 静默成功"对"别家的人 → {@code 40002}"：那时两支连结果都不同，
 * 光把后一支的文案改成前一支的形状是关不掉那个口子的。
 * 改前这两条分支给的是"用户不存在"与"用户不存在**或不属于当前租户**"，
 * 调用方比一下 {@code message} 就学到了"乙租户里真有这个 id" ——
 * 而"它存在"这件事本身就是别人的信息（`0025` 第 2 条）。
 * 平台级那一侧是**对照**（{@link #platformReaderKeepsTheDiagnosis()}）：
 * 他是唯一能合法跨租户的人，"这两个东西不在同一租户"对他是有用的诊断，不许顺手一起删掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 口令与种子账号信息**跟着配置走**，不在测试里再刻一份（见 {@code docs/decisions/0029}） */
    @Autowired
    private AuthProperties properties;

    /**
     * 一个**不存在**的主键。
     *
     * <p>取一个远大于任何自增序列的值，而不是"当前最大的 id + 1"：后者在并发建的用例
     * 之间会偶尔撞上一个真行，症状是这条用例**偶尔**失败 —— 那比缺一条用例更糟。
     * 与 {@code ReferenceMessageApiTest} 同一条理由。
     */
    private static final long ABSENT_ID = 999_999_999L;

    /* ==================== 夹具（每条用例共用的两租户 + 一个非平台级管理员） ==================== */

    /** A 租户的管理员令牌：非平台级，但被授予了本片需要的全部权限码 */
    private String adminTokenOfA;
    /** B 租户里的一个用户主键 —— 本片反复尝试从 A 去够它 */
    private long bUserId;
    private String bUsername;
    /** B 租户里的一个组织 / 一个用户组 —— 同样是从 A 去够的目标（覆盖组织域与用户组域） */
    private long bOrgId;
    private long bGroupId;
    /** A 租户里的一个角色 —— 第六节"引用不一致"的容器（成员族与父级族各用它一次） */
    private long aRoleId;
    /** A 租户里的另一个用户 —— 用来证明"列表返回的是 A 自己的数据"，而不是空列表 */
    private String aOtherUsername;
    private long tenantAId;
    private long tenantBId;
    private String tenantACode;
    private String tenantBCode;

    private void ensureFixture() throws Exception {
        if (adminTokenOfA != null) {
            return;
        }
        String platformToken = loginAsPlatformAdmin();
        long platformTenantId = currentTenantId(platformToken);

        // A、B 都挂在平台租户下 ⇒ parent_id != null ⇒ 非顶级 ⇒ 它们的成员不是平台级
        tenantACode = uniqueCode("isa");
        tenantBId = createTenant(platformToken, uniqueCode("isb"), platformTenantId);
        tenantBCode = JsonPath.read(tenantDetail(platformToken, tenantBId), "$.data.tenantCode");
        tenantAId = createTenant(platformToken, tenantACode, platformTenantId);

        aOtherUsername = "iso_" + Long.toString(System.nanoTime(), 36);
        createUser(platformToken, tenantAId, aOtherUsername);

        bUsername = "iso_" + Long.toString(System.nanoTime(), 36);
        bUserId = createUser(platformToken, tenantBId, bUsername);
        bOrgId = createOrg(platformToken, tenantBId, uniqueCode("isorg"));
        bGroupId = createUserGroup(platformToken, tenantBId, uniqueCode("isgrp"));

        // A 租户里的管理员：建号 → 建角色 → 授齐所需权限码 → 绑定 → 登录
        String adminUsername = "iso_" + Long.toString(System.nanoTime(), 36);
        long adminUserId = createUser(platformToken, tenantAId, adminUsername);
        long roleId = createRole(platformToken, tenantAId, uniqueCode("ISOROLE"));
        aRoleId = roleId;
        for (String code : List.of("auth:user:read", "auth:user:create", "auth:user:update",
                "auth:user:delete", "auth:tenant:read", "auth:tenant:update", "auth:tenant:create",
                "auth:org:read", "auth:org:create", "auth:role:update", "auth:user_group:read")) {
            grantResource(platformToken, roleId, resourceIdOf(platformToken, code));
        }
        bindRoleMember(platformToken, roleId, adminUserId);
        adminTokenOfA = login(tenantACode, adminUsername);
    }

    /* ==================== 一、读侧：列表与树 ==================== */

    @Test
    @DisplayName("列表：甲租户管理员带 ?tenantId=乙 → 拿到的是**自己租户**的那一份，不含乙的用户")
    void listIgnoresForeignTenant() throws Exception {
        ensureFixture();

        MvcResult result = mockMvc.perform(get("/api/auth/users")
                        .param("tenantId", String.valueOf(tenantBId))
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        List<String> usernames = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.records[*].username");
        assertFalse(usernames.contains(bUsername),
                "乙租户的用户不该出现在甲租户管理员的列表里（改前这里会列出他）");
        assertTrue(usernames.contains(aOtherUsername),
                "传入别家 tenantId 该被忽略成自己那一份，而不是筛出一个空结果");
    }

    /* ==================== 二、读侧：详情与反查 ==================== */

    @Test
    @DisplayName("详情：甲租户管理员查乙租户的用户 → 40401（不是 403 —— 连「存在」都不该泄漏）")
    void detailCrossTenantIsNotFound() throws Exception {
        ensureFixture();

        mockMvc.perform(get("/api/auth/users/" + bUserId)
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("反查角色：甲租户管理员查乙租户用户的角色来源 → 40401（这条改前会真的泄漏乙的角色）")
    void rolesOfForeignUserIsNotFound() throws Exception {
        ensureFixture();

        mockMvc.perform(get("/api/auth/users/" + bUserId + "/roles")
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("组织详情：甲租户管理员查乙租户的组织 → 40404（成员归属等四条路径同源，只改一处）")
    void foreignOrganizationIsNotFound() throws Exception {
        ensureFixture();

        mockMvc.perform(get("/api/auth/orgs/" + bOrgId)
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40404));
    }

    @Test
    @DisplayName("用户组详情：甲租户管理员查乙租户的用户组 → 40405")
    void foreignUserGroupIsNotFound() throws Exception {
        ensureFixture();

        mockMvc.perform(get("/api/auth/user-groups/" + bGroupId)
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40405));
    }

    /* ==================== 三、写侧：建 / 改 / 重置口令 / 删 ==================== */

    @Test
    @DisplayName("建号：甲租户管理员带 tenantId=乙 → 40002（显式拒绝，而不是悄悄建到自己租户）")
    void createInForeignTenantIsRejected() throws Exception {
        ensureFixture();

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminTokenOfA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"跨租户建号","password":"%s"}
                                """.formatted(tenantBId, "iso_" + Long.toString(System.nanoTime(), 36),
                                seedPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("改用户：甲租户管理员改乙租户的用户 → 40401（改前这里能把乙的人改名）")
    void updateForeignUserIsNotFound() throws Exception {
        ensureFixture();

        // 带上 username：它是 DTO 上的 @NotBlank（改的虽然是显示名，但这条规矩要求
        // 调用方把它原样带回来 —— 见 0030 未覆盖里那处"要求一个不被读的字段"）。
        // 少了它，改前会先被 DTO 挡成 40000，把本条测的东西盖掉
        mockMvc.perform(put("/api/auth/users/" + bUserId)
                        .header("Authorization", "Bearer " + adminTokenOfA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"被甲租户改掉的乙用户"}
                                """.formatted(bUsername)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("重置口令：甲租户管理员重置乙租户用户的口令 → 40401（改前可借此接管对方账号）")
    void resetPasswordOfForeignUserIsNotFound() throws Exception {
        ensureFixture();

        // 口令必须与乙方那个人**已有的**那一个不同：否则改前会被防重用先拒成 40914，
        // 把"跨租户"这件事盖掉（这条坑本片踩过一次，症状是断言拿到了另一个码）
        mockMvc.perform(post("/api/auth/users/" + bUserId + "/password")
                        .header("Authorization", "Bearer " + adminTokenOfA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(seedPassword() + "Iso#")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("删用户：甲租户管理员删乙租户的用户 → 40401")
    void deleteForeignUserIsNotFound() throws Exception {
        ensureFixture();

        // **本条自己建一个靶子**，不用共用的那一个：改前这一下会真的把人删掉（逻辑删除），
        // 之后所有查它的用例都会因为"查不到"而**假绿** —— 本片第一版就是这样，
        // 12 条里假绿了 2 条。一条会改数据的用例必须独占它的目标
        String platformToken = loginAsPlatformAdmin();
        long canaryUserId = createUser(platformToken, tenantBId, "iso_" + Long.toString(System.nanoTime(), 36));

        mockMvc.perform(delete("/api/auth/users/" + canaryUserId)
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));

        // 断言"没删掉"而不只是"回了这个码"：码对而记录也真被删了，是最坏的一种通过。
        // 问详情要换平台级令牌 —— 不然这里会被自己的隔离挡住，把断言变成同义反复
        mockMvc.perform(get("/api/auth/users/" + canaryUserId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 四、租户域：自己也是一种资源 ==================== */

    @Test
    @DisplayName("租户列表：非平台级只看到**自己那一个**（改前列出全库租户）")
    void tenantListIsLimitedToOwnTenant() throws Exception {
        ensureFixture();

        mockMvc.perform(get("/api/auth/tenants")
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) tenantAId));
    }

    @Test
    @DisplayName("租户详情 / 修改：非平台级够不到别家租户 → 40402（停用别人的租户也在这一片里）")
    void foreignTenantDetailAndUpdateAreNotFound() throws Exception {
        ensureFixture();

        mockMvc.perform(get("/api/auth/tenants/" + tenantBId)
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40402));

        mockMvc.perform(put("/api/auth/tenants/" + tenantBId)
                        .header("Authorization", "Bearer " + adminTokenOfA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"被甲租户改掉的乙","status":"DISABLED"}
                                """.formatted(tenantBCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40402));
    }

    @Test
    @DisplayName("建顶级租户 = 自升平台级 → 非平台级被拒（40302）；这是平台级判据闭合的那一半")
    void topLevelTenantCreationRequiresPlatformTenant() throws Exception {
        ensureFixture();

        mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + adminTokenOfA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"甲租户想建的顶级租户"}
                                """.formatted(uniqueCode("isx"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40302));
    }

    /* ==================== 五、反面：平台级照旧、自己的租户照旧 ==================== */

    @Test
    @DisplayName("平台级（顶级租户的成员）跨租户照旧 —— 少了这条，一个「谁都别跨」的实现也能全绿")
    void platformTenantStaysCrossTenantCapable() throws Exception {
        ensureFixture();
        String platformToken = loginAsPlatformAdmin();

        // 读别家的用户与租户：这是 0058 那条恢复入口与全部跨租户预配的基础
        mockMvc.perform(get("/api/auth/users/" + bUserId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/auth/tenants/" + tenantBId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(jsonPath("$.code").value(0));
        // 平台级带别家 tenantId 建号也照旧（TenantDisableApiTest 的两条前置就是这么做夹具的）
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"平台级跨租户建号","password":"%s"}
                                """.formatted(tenantBId, "iso_" + Long.toString(System.nanoTime(), 36),
                                seedPassword())))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("非平台级管自己租户照常（不带 tenantId 的读写都成功）—— 隔离不是「谁都不能用」")
    void ownTenantOperationsStillWork() throws Exception {
        ensureFixture();

        MvcResult result = mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<String> usernames = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.records[*].username");
        assertTrue(usernames.contains(aOtherUsername), "不带 tenantId 时列表应落在自己租户上");

        mockMvc.perform(get("/api/auth/tenants/" + tenantAId)
                        .header("Authorization", "Bearer " + adminTokenOfA))
                .andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 六、文案：这一族接口对外说什么 ==================== */

    /**
     * 断的不是"文案等于哪一句字面量"，而是**两条分支给的是同一句话**。
     *
     * <p>这是本组用例唯一关心的性质：只要两句话不同，调用方就能靠一次对比
     * 把"这个 id 在别家租户里存在"读出来 —— 而那正是 `0025` 第 2 条
     * 与 `0059` 第二节要挡的东西（"它存在"本身就是别人的信息）。
     * 断言写成"相等"而不是写成某一句字面量，还有一个好处：**具体那句话可以改**
     * （换措辞、换语言），而这条性质不许破。
     */
    @Test
    @DisplayName("文案：非平台级分不出「不存在」与「存在但不属于当前租户」（用户详情 / 租户详情）")
    void invisibleEntityMessageIsIndistinguishable() throws Exception {
        ensureFixture();

        assertEquals(
                messageOf(get("/api/auth/users/" + ABSENT_ID), adminTokenOfA, 40401),
                messageOf(get("/api/auth/users/" + bUserId), adminTokenOfA, 40401),
                "改前两句不同（「用户不存在」/「用户不存在或不属于当前租户」）⇒ "
                        + "比一下 message 就知道乙租户里有没有这个 id");

        assertEquals(
                messageOf(get("/api/auth/tenants/" + ABSENT_ID), adminTokenOfA, 40402),
                messageOf(get("/api/auth/tenants/" + tenantBId), adminTokenOfA, 40402),
                "租户域同形：改前是「租户不存在」与「租户不存在或不属于当前租户」");
    }

    /**
     * 写侧同形。**成员族与父级族分成两条用例**：它们走的是两处不同的实现
     * （`requireMember` / `requireSameTenantUser` 与三个 `resolveParent`），
     * 合在一条里的话前一行红了、后一行就不再执行 —— 那正是 `0055`"只钉一支等于没钉"。
     *
     * <p>断言里那个 {@link #masked} 是必要的：两句文案都会把主键写进去，
     * 所以"逐字相同"要读成"**除那个主键之外**逐字相同" —— 即文案是主键的函数。
     */
    @Test
    @DisplayName("文案：非平台级把别家的用户加进自己的角色 → 与「引用的用户不存在」同形（成员族）")
    void invisibleMemberReferenceMessageIsIndistinguishable() throws Exception {
        ensureFixture();

        assertEquals(
                masked(messageOf(bindMember(aRoleId, ABSENT_ID), adminTokenOfA, 40002), ABSENT_ID),
                masked(messageOf(bindMember(aRoleId, bUserId), adminTokenOfA, 40002), bUserId),
                "改前：前者「引用的用户不存在：…」，后者「该用户与角色不属于同一租户（…）」"
                        + "—— 后者等于告诉甲租户管理员「乙那边确实有这么个人」");
    }

    @Test
    @DisplayName("文案：非平台级把新组织挂到别家的部门下 → 与「引用的父组织不存在」同形（父级族）")
    void invisibleParentReferenceMessageIsIndistinguishable() throws Exception {
        ensureFixture();

        assertEquals(
                masked(messageOf(createOrgUnder(ABSENT_ID), adminTokenOfA, 40002), ABSENT_ID),
                masked(messageOf(createOrgUnder(bOrgId), adminTokenOfA, 40002), bOrgId),
                "改前是「引用的父组织不存在：…」与「父组织不属于同一租户，…」");
    }

    /**
     * 负责人族（`manager_id`）。**它是本片唯一一处"连码都不同"的落点** ——
     * 所以只统一文案是关不掉这个口子的，须先把两支都变成 {@code 40002}。
     *
     * <p>改前的两种可分辨结果：
     * <ul>
     *   <li>{@code managerId} 谁也不是 → **保存成功**，{@code manager_name} 写成 {@code null}；</li>
     *   <li>{@code managerId} 是别家的人 → {@code 40002} + 点名成因的那句话。</li>
     * </ul>
     * 于是"收到 {@code 40002}"这个事实本身就等于"这个 id 是别家的一个真人"。
     * 断言仍然写成"两句话相等"，于是它同时钉住了三件事：两支同码、两支同话、
     * 且"谁也不是"那一支不再静默成功。
     */
    @Test
    @DisplayName("文案：非平台级把别家用户设成本租户某组织的负责人 → 与「引用的负责人不存在」同形（负责人族）")
    void invisibleManagerReferenceMessageIsIndistinguishable() throws Exception {
        ensureFixture();

        assertEquals(
                masked(messageOf(createOrgWithManager(ABSENT_ID), adminTokenOfA, 40002), ABSENT_ID),
                masked(messageOf(createOrgWithManager(bUserId), adminTokenOfA, 40002), bUserId),
                "改前前者静默成功、后者 40002 且点名「不属于当前租户」——"
                        + "单看这个码就知道那个 id 是别家的一个真人");
    }

    /**
     * 上一条的**反方向**：平台级读者不许被一起收窄。
     *
     * <p>他是唯一能合法跨租户的人（`0074` 的判据），"这两个东西不在同一个租户、
     * 判定因此永远不生效"对他是一句**有用的诊断** —— 少了本条，
     * 把诊断文案一并删掉、让所有人都只看得到"不存在"也能全绿。
     */
    @Test
    @DisplayName("对照：平台级读者仍拿到诊断原文（「不属于同一租户」不许被一起删掉）")
    void platformReaderKeepsTheDiagnosis() throws Exception {
        ensureFixture();
        String platformToken = loginAsPlatformAdmin();

        assertTrue(messageOf(bindMember(aRoleId, bUserId), platformToken, 40002)
                        .contains("不属于同一租户"),
                "平台级跨租户预配时写错了租户，靠的就是这句话");
        assertTrue(messageOf(createOrgUnder(bOrgId), platformToken, 40002)
                        .contains("不属于同一租户"),
                "父级族同形");
        assertTrue(messageOf(createOrgWithManager(bUserId), platformToken, 40002)
                        .contains("不属于同一租户"),
                "负责人族同形 —— 它对平台级也拒绝（跨租户的负责人对谁都没用），"
                        + "但拒绝时要说清是租户错还是 id 错");
    }

    /**
     * 租户域自己那一处：`0074` 决策 5 写的是"`40402` + 与『租户不存在』**逐字相同**的文案"，
     * 而实现给的是带后缀的那句 —— **文档承诺了、代码没做到**，且没有任何东西会红。
     *
     * <p>修法只有一个方向：让那句话真的与"租户不存在"逐字相同。
     * 反方向（把文档改成"或不属于当前租户"）会留下同一件事的两种说法，
     * 而那正是本片在收的那条口径。
     */
    @Test
    @DisplayName("文案：非平台级引用别家租户作父级，与「租户不存在」说同一句话（决策 5 的字面承诺）")
    void foreignTenantParentMessageMatchesAbsentTenant() throws Exception {
        ensureFixture();

        assertEquals(
                messageOf(get("/api/auth/tenants/" + ABSENT_ID), adminTokenOfA, 40402),
                messageOf(createTenantUnder(tenantBId), adminTokenOfA, 40402),
                "改前实现给的是「租户不存在或不属于当前租户」—— ADR 说的是逐字相同");
    }

    /* ==================== 辅助 ==================== */

    /**
     * 发一次请求，断言业务码，把响应里的 {@code message} 取回来。
     *
     * <p>返回 `message` 本身而不是断言它在方法里，是因为本组用例的判据是
     * **两条分支之间的关系**（相等 / 同形），而不是某一条单独的值。
     */
    private String messageOf(MockHttpServletRequestBuilder request, String token, int expectedCode)
            throws Exception {
        MvcResult result = mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.message");
    }

    /**
     * 把文案里的那个主键换成 {@code #} —— 于是"同形"这句判据可以写成"逐字相同"。
     *
     * <p>用 {@code \b} 而不是裸的 {@code replace(String,String)}：主键是一串数字，
     * 裸替换会在"文案里还出现别的数字"时误伤（例如 id 为 `1` 而文案里有 `100`）。
     */
    private static String masked(String message, long id) {
        return message.replaceAll("\\b" + id + "\\b", "#");
    }

    /** 把某个用户加进某个角色 —— 成员族的"引用不一致"入口（`auth:role:update`） */
    private MockHttpServletRequestBuilder bindMember(long roleId, long memberId) {
        return post("/api/auth/roles/" + roleId + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"memberType":"USER","memberId":%d}
                        """.formatted(memberId));
    }

    /** 建一个挂在某个父组织下的组织 —— 父级族的"引用不一致"入口（`auth:org:create`） */
    private MockHttpServletRequestBuilder createOrgUnder(long parentId) {
        return post("/api/auth/orgs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orgCode":"%s","orgName":"文案核对组织","parentId":%d}
                        """.formatted(uniqueCode("isoM"), parentId));
    }

    /** 建一个挂在某个父租户下的租户 —— 租户域那处"引用不一致"的入口（`auth:tenant:create`） */
    private MockHttpServletRequestBuilder createTenantUnder(long parentId) {
        return post("/api/auth/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tenantCode":"%s","tenantName":"文案核对租户","parentId":%d}
                        """.formatted(uniqueCode("isoMT"), parentId));
    }

    /**
     * 建一个把某个用户设成负责人的组织 —— 负责人族的"引用不一致"入口（`auth:org:create`）。
     *
     * <p>负责人是**服务端解析姓名**的引用列（入参只收 `managerId`），所以这条入口
     * 一次请求里就能同时问出"这个 id 指得到人吗"与"指到的那个人同租户吗"。
     */
    private MockHttpServletRequestBuilder createOrgWithManager(long managerId) {
        return post("/api/auth/orgs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orgCode":"%s","orgName":"文案核对组织","managerId":%d}
                        """.formatted(uniqueCode("isoMg"), managerId));
    }

    private String seedPassword() {
        return properties.getSeedAdmin().getPassword();
    }

    /** 平台级操作者**就是种子账号**：它所在的是顶级租户 */
    private String loginAsPlatformAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(properties.getSeedAdmin().getTenantCode(),
                                properties.getSeedAdmin().getUsername(), seedPassword())))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private String login(String tenantCode, String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(tenantCode, username, seedPassword())))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    /** 从 {@code /me} 取当前登录者的租户，而不是写死 1 —— 写死会在种子策略变化时静默指向别的身份 */
    private long currentTenantId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number tenantId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.tenantId");
        return tenantId.longValue();
    }

    private String tenantDetail(String token, long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** 建一个租户；{@code parentId} 为 null 时是顶级租户（**不带这个键**，而不是带 null） */
    private long createTenant(String token, String tenantCode, Long parentId) throws Exception {
        String body = parentId == null
                ? """
                {"tenantCode":"%s","tenantName":"隔离测试租户 %s"}
                """.formatted(tenantCode, tenantCode)
                : """
                {"tenantCode":"%s","tenantName":"隔离测试租户 %s","parentId":%d}
                """.formatted(tenantCode, tenantCode, parentId);
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createUser(String token, long tenantId, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"隔离测试用户","password":"%s"}
                                """.formatted(tenantId, username, seedPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createOrg(String token, long tenantId, String orgCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"orgCode":"%s","orgName":"隔离测试组织 %s"}
                                """.formatted(tenantId, orgCode, orgCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createUserGroup(String token, long tenantId, String groupCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/user-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"groupCode":"%s","groupName":"隔离测试用户组 %s"}
                                """.formatted(tenantId, groupCode, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createRole(String token, long tenantId, String roleCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"roleCode":"%s","roleName":"隔离测试角色 %s"}
                                """.formatted(tenantId, roleCode, roleCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 这个权限码对应的资源主键 —— 授权接口按 {@code resourceId} 引用资源（资源是平台级的） */
    private long resourceIdOf(String platformToken, String resourceCode) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/resources")
                        .param("page", "1")
                        .param("size", "500")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<String> codes = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.records[*].resourceCode");
        List<Integer> ids = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.records[*].id");
        for (int i = 0; i < codes.size(); i++) {
            if (resourceCode.equals(codes.get(i))) {
                return ids.get(i).longValue();
            }
        }
        throw new IllegalStateException("资源目录里找不到权限码：" + resourceCode
                + "（夹具依赖它，缺了应当立刻失败而不是静默跳过）");
    }

    private void grantResource(String token, long roleId, long resourceId) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d}
                                """.formatted(resourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private ResultActions bindRoleMember(String token, long roleId, long userId) throws Exception {
        return mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"USER","memberId":%d}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /* 编码一律以字母开头：`Long.toString(n, 36)` 可能以数字开头，而编码首字符要求是字母 ——
       直接用纳秒时间戳会偶尔被自己的 DTO 校验拦下，变成一条"大部分时候通过"的假测试 */

    private static String uniqueCode(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

}
