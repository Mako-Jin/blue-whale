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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code 404xx} 家族的「点名」核对，与**六处此前没有任何断言的落点**的补齐（ADR-0059）。
 *
 * <p><b>本片领的是 {@code 0057}「未覆盖」的第 3 条</b>：那个码（{@code 40002}）核完之后
 * 留下的问题是"`40407` / `40409` / `40410` 这些'找不到'的码同样可能'点没点名'参差"。
 * 核完的结论是**那句话的前提不成立**，理由是两个家族的结构不同：
 *
 * <ul>
 *   <li>{@code 40002} **一个码覆盖许多个对象**（引用的用户 / 父组织 / 父角色 / 资源 …），
 *       所以"是哪个对象"这个信息**只能住在抛出点的文案里** —— 于是每一处各写一句，
 *       于是每一处都可能写错，于是"有没有点名"成为一个真问题。</li>
 *   <li>404 家族**一个码就是一个对象**（{@code 40401} 用户 / {@code 40402} 租户 /
 *       {@code 40404} 组织 / {@code 40406} 资源 …），名字住在**枚举**里。
 *       抛出点根本不写文案，也就写不出错的对象。写下本类时，本族的十几处落点里
 *       有**一处**覆盖了文案（{@code DataScopeService}），而那一处恰恰是这个码
 *       在说两件事的地方 —— `0075` 已把那一族撤回，见下。</li>
 * </ul>
 *
 * <p><b>但核出六处落点此前没有任何断言</b>（{@code 40402} / {@code 40404} ×2 /
 * {@code 40406} 三码整族为零，加上 {@code 40401} 与 {@code 40403} 各有一处）：
 *
 * <ul>
 *   <li>{@code 40401} —— {@code UserService#requireUser}：查 / 删一个不存在的用户；</li>
 *   <li>{@code 40402} —— {@code TenantService#requireTenant}：查 / 删一个不存在的租户；</li>
 *   <li>{@code 40403} —— {@code RoleMemberService#requireRole}：给不存在的角色挂 / 摘成员；</li>
 *   <li>{@code 40404} —— {@code OrganizationService#requireOrg} 与 {@code #tree} 的 {@code rootId}；</li>
 *   <li>{@code 40406} —— {@code ResourceService#requireResource}。</li>
 * </ul>
 *
 * <p>它们空着的原因不是判断有分歧，而是**没有人在那种接口上写过一条"查一个不存在的东西"** ——
 * 同域的 {@code 40403}（删完再查角色）与 {@code 40405}（删完再查用户组）都有人写过，
 * 这两个恰好写在了 {@code RoleApiTest} / {@code UserGroupApiTest} 里。
 *
 * <p><b>这一族该断什么，与 {@code 40002} 相反。</b>{@code 40002} 只断 {@code code} 等于没核
 * （每一处共用一个码，携带对象信息的只有文案，所以 {@code ReferenceMessageApiTest} 断的是文案）；
 * 404 家族**断 {@code code} 就是完整断言**，因为码本身就是对象。本类两样都断，
 * 而第二样的作用不是"点名"，是**证明两条分支说的是同一句话**：
 * 一旦哪一处开始覆盖文案（=`40002` 那种"一个码说两件事"的形态出现了），
 * 下面那条文案断言会先红。
 *
 * <p><b>「全族只有一处覆盖文案」这句话本片写下时是对的，但它在 `0075` 之后不再成立 ——
 * 是往好的方向变的：</b>本类写于 `0059`，当时 {@code DataScopeService#resolve} 是全仓
 * 唯一一处覆盖点（"用户不存在**或不属于当前租户**"）。`0074` 把它**推广成一族**
 * （`TenantService#assertVisible` 用 `code.getMessage() + "或不属于当前租户"` 生成文案），
 * 而 `0075` 把那两处**全部撤回**：覆盖之后，"真的不存在"那一支说的仍是裸文案，
 * 于是同一个码在两条分支上给出两句不同的话 —— 调用方比一下 {@code message} 就知道
 * "这条记录确实存在，只是在别家"。现在本类第一条用例断言的那句"文案未被覆盖"
 * 是**全族**的性质，而不只是一处；那条性质另由
 * {@code scripts/verify-notfound-messages.mjs} 机械守着，
 * 更强的形式（"两条分支同码同话"）由 {@code TenantIsolationApiTest} 第六节钉。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code ReferenceMessageApiTest} 同一理由：
 * 本类第二例问的正是"软删之后库里真正留下了什么"（{@code @TableLogic} 让被删的行在应用层
 * 等于不存在），测试事务会让未提交的写在同一事务内可见，把这个前提盖掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotFoundCodeApiTest {

    /**
     * 一个**不存在**的主键。
     *
     * <p>取一个远大于任何自增序列的值，而不是"当前最大的 id + 1"：后者在并发建的用例
     * 之间会偶尔撞上一个真行，症状是这条用例**偶尔**失败 —— 那比缺一条用例更糟。
     * 与 {@code ReferenceMessageApiTest} / {@code TenantReferenceApiTest} 同一条理由。
     */
    private static final long ABSENT_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /** 管理员的主键，在 {@link #loginAsAdmin()} 里从 {@code /me} 取 */
    private long adminUserId;

    /* ==================== 一、四个"实体不存在"的码各查一次 ==================== */

    @Test
    @DisplayName("查不存在的用户 / 租户 / 组织 / 资源 —— 各自返回自己的码，且文案未被覆盖")
    void shouldReturnItsOwnCodeForAbsentEntity() throws Exception {
        String token = loginAsAdmin();

        assertNotFound(token, get("/api/auth/users/" + ABSENT_ID), 40401, "用户不存在");
        assertNotFound(token, get("/api/auth/tenants/" + ABSENT_ID), 40402, "租户不存在");
        assertNotFound(token, get("/api/auth/orgs/" + ABSENT_ID), 40404, "组织不存在");
        assertNotFound(token, get("/api/auth/resources/" + ABSENT_ID), 40406, "资源不存在");
    }

    /* ==================== 二、软删之后再查：同一个码 ==================== */

    @Test
    @DisplayName("删掉之后再查 —— 租户 / 组织 / 资源仍返回各自那个码（@TableLogic 让它在应用层等于不存在）")
    void shouldKeepTheSameCodeAfterLogicalDelete() throws Exception {
        String token = loginAsAdmin();

        // 租户：删一个自己建的（种子租户删不掉，那由 40921 挡着，见 TenantDisableApiTest）
        long tenantId = createTenant(token, uniqueTenantCode());
        deleteTenant(token, tenantId);
        assertNotFound(token, get("/api/auth/tenants/" + tenantId), 40402, "租户不存在");

        long orgId = createOrg(token, uniqueOrgCode("gone"), "已删除的部门");
        deleteOrg(token, orgId);
        assertNotFound(token, get("/api/auth/orgs/" + orgId), 40404, "组织不存在");

        // 删的是软删：行还在库里，只是 @TableLogic 给每条查询补上了 deleted = 0。
        // 也就是说"查不到了"不是靠代码记得排除，而是靠注解 —— 这条用例正是它的证据
        long resourceId = createResource(token, uniqueResourceCode(), "已删除的资源");
        deleteResource(token, resourceId);
        assertNotFound(token, get("/api/auth/resources/" + resourceId), 40406, "资源不存在");
    }

    /* ==================== 三、同一个码的第二个落点 ==================== */

    @Test
    @DisplayName("组织树的 rootId 不存在 —— 与 requireOrg 是同一个码的另一条路径")
    void shouldReturnOrganizationNotFoundForAbsentTreeRoot() throws Exception {
        String token = loginAsAdmin();

        // OrganizationService#tree 与 #requireOrg 各有一处 throw，两处给的是同一个码。
        // 所以这条用例钉的不是"另一个对象"（它们说的是同一个对象），
        // 而是"这条路径也走这个码、而不是悄悄返回一棵空森林" ——
        // 空森林与"这个组织下面什么都没有"在界面上长得一模一样
        assertNotFound(token,
                get("/api/auth/orgs/tree").param("rootId", String.valueOf(ABSENT_ID)),
                40404, "组织不存在");
    }

    /* ==================== 四、成员的两种操作，角色不存在时是同一个码 ==================== */

    @Test
    @DisplayName("给不存在的角色挂 / 摘成员 —— 报的是角色不存在（40403），不是成员那侧的码")
    void shouldReturnRoleNotFoundWhenOperatingMembersOfAbsentRole() throws Exception {
        String token = loginAsAdmin();

        // 挂：RoleMemberService#bind 的第一个语句就是 requireRole，所以先撞上角色。
        // 用的成员是一个**真**用户，免得这条断言其实在钉"成员的校验顺序"——
        // 那会把一个顺序细节钉成期望行为
        assertNotFound(token,
                post("/api/auth/roles/" + ABSENT_ID + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"USER","memberId":%d}
                                """.formatted(adminUserId)),
                40403, "角色不存在");

        // 摘：同一条 requireRole。两个入口一个码，前端不必为了刷哪个列表分两种分支
        assertNotFound(token,
                delete("/api/auth/roles/" + ABSENT_ID + "/members/USER/" + adminUserId),
                40403, "角色不存在");
    }

    /* ==================== 夹具 ==================== */

    /**
     * 一次"找不到"的完整断言：{@code code} 与 {@code message} 两样都要对。
     *
     * <p>两样一起断的理由见类注释 —— 第一样钉"是哪个对象"，第二样钉
     * "这个码还只干一件事"。只断第一样的话，某处哪天开始覆盖文案
     * （例如把 {@code 40402} 也用来报"引用的租户已停用"）不会有任何东西变红，
     * 而那是 {@code 40002} 那种形态的头一步。
     */
    private void assertNotFound(String token, MockHttpServletRequestBuilder request,
                                int code, String message) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(containsString(message)));
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
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");

        // 主键从 /me 拿而不是写死 1：写死会在种子策略变化时静默指向别的身份
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        adminUserId = ((Number) JsonPath.read(me.getResponse().getContentAsString(),
                "$.data.userId")).longValue();
        return token;
    }

    private long createTenant(String token, String tenantCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"404 文案核对租户 %s"}
                                """.formatted(tenantCode, tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private void deleteTenant(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
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

    private void deleteOrg(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/orgs/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createResource(String token, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceCode":"%s","resourceName":"%s","resourceType":"PAGE"}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private void deleteResource(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/resources/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /* 租户 / 组织的编码：以字母开头 + 只用字母数字与下划线 —— `Long.toString(n, 36)`
       可能以数字开头，直接用时间戳会**偶尔**被自己的 DTO 校验拦下，
       变成一条"大部分时候通过"的假测试 */

    private static String uniqueTenantCode() {
        return "e2enft" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueOrgCode(String prefix) {
        return "E2ENFO_" + prefix + Long.toString(System.nanoTime(), 36);
    }

    /**
     * 资源编码的形态与上两个不同：必须是 {@code 域:资源:动作} 三段小写
     * （{@code ResourceSaveRequest} 的 {@code @Pattern}）。
     *
     * <p>第一次跑这条用例时它是一串下划线风格，于是**建资源那一步先红了** ——
     * 这不是被测代码的问题，是夹具没有照着那条唯一被校验得最严的编码写。
     * 中段加一个字母前缀，是因为 base36 可能整段都是数字。
     */
    private static String uniqueResourceCode() {
        return "e2enfr:x" + Long.toString(System.nanoTime(), 36) + ":read";
    }

}
