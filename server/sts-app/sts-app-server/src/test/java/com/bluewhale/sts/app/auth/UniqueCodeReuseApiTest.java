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
 * 「一个唯一编码被**软删行**占着时怎么办」—— 六个列，六个答案，而此前只写下了四个（ADR-0056）。
 *
 * <p><b>这六个列是同一条 DDL 决定的。</b>唯一索引一律**不含 {@code deleted}**
 * （见 {@code V3__create_bw_auth_tables.sql}），于是软删行仍然占着那个值；
 * 而 MyBatis-Plus 给每条自动生成的查询都追加 {@code deleted = 0}，
 * 常规判重**看不见**占位者 —— 判重通过、insert 撞键。
 * 六个这样的列各自必须显式回答一句"软删行算不算占着"：
 *
 * <table border="1">
 *   <caption>六个唯一编码列与它们的答案</caption>
 *   <tr><th>列</th><th>答案</th><th>钉在哪</th></tr>
 *   <tr><td>{@code bw_auth_resource.resource_code}</td><td>复活原行</td>
 *       <td>{@code ResourceApiTest}「软删后重建同一标识」</td></tr>
 *   <tr><td>{@code bw_auth_role.(tenant_id, role_code)}</td><td>复活原行（且不带回旧授权）</td>
 *       <td>{@code RoleApiTest}「软删过的编码可以复用」</td></tr>
 *   <tr><td>{@code bw_auth_user_group.(tenant_id, group_code)}</td><td>复活原行（且清空成员）</td>
 *       <td>{@code UserGroupApiTest}「软删后同码重建是复活原行」</td></tr>
 *   <tr><td>{@code bw_auth_organization.(tenant_id, org_code)}</td><td>复活原行（且清空归属）</td>
 *       <td>{@code OrganizationApiTest}「同码软删重建」</td></tr>
 *   <tr><td>{@code bw_auth_tenant.tenant_code}</td><td><b>拒绝</b>（{@code 40904}）</td>
 *       <td>本片</td></tr>
 *   <tr><td>{@code bw_auth_user.(tenant_id, username)}</td><td><b>拒绝</b>（{@code 40901}）</td>
 *       <td>本片</td></tr>
 * </table>
 *
 * <p><b>前四行与后两行的分界不是偏好，是一条可以被检查的性质：复活是否安全，
 * 取决于那一行的删除守卫有没有挡住活边。</b>资源 / 角色 / 组织 / 用户组在删除时都拒绝了
 * "仍被引用"（还有子节点 {@code 40913}、还有成员 {@code 40915} / {@code 40916} /
 * {@code 40918} / {@code 40919}），所以那条软删行一定是**没有活边的空壳**，
 * 放回来不会让任何人多出一样东西。租户与用户不是这样：
 * {@code TenantService#delete} 只拒绝"仍有子租户"，**不拒绝"还有人"**；
 * {@code UserService#delete} 只拒绝"删自己"。复活它们会把**一批人的登录/权限**
 * 一起放回来，而调用方以为自己只是新建了一个同名的东西。
 *
 * <p><b>本片钉的是后两行 —— 它们此前没有答案，而是掉进了兜底码。</b>
 * 判重看不见软删行 → insert 撞 {@code uk_*} → {@code GlobalExceptionHandler}
 * 翻成 {@code 40900}「数据已存在或与已有记录冲突」。故障不显形（不是 500），
 * 但文案退化成一句不含主语的兜底话，而这句话原本的用途是"并发下的第二道防线"。
 *
 * <p><b>两条反例是刻意加的，防的是"顺手一起挡掉"这种更省事的写法。</b>
 * 没有唯一索引的列（{@code tenant_name} 等）**必须**让软删释放掉那个值 ——
 * 软删本来就该等于"它不在了"；只有被索引逼着的六个列才需要额外回答一句。
 * 而"删掉之后换一个新值"必须照常能建：否则一个"看见任何软删行就拒绝"的实现
 * 也能让上面两条通过。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code TenantReferenceApiTest} 同一理由：
 * 本片问的是"库里真正留下了什么" —— 判重的依据就是软删行还在不在，
 * 测试事务把它回滚掉，测的就成了另一件事。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UniqueCodeReuseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 后两行：软删行占着编码 → 明确拒绝 ==================== */

    @Test
    @DisplayName("租户编码：活行占着是 40904，软删行占着**也是** 40904（此前是兜底的 40900）")
    void shouldRejectReusingSoftDeletedTenantCode() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueTenantCode();
        long id = createTenant(token, code, "编码复用测试租户 " + code);

        // 第一段：活行占着 —— 这一支此前就对，写在这里是为了让"两支给同一个答案"成为断言，
        // 而不是一句注释。两支不分码：调用方要做的事完全一样（换一个编码）
        mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody(code, "编码复用测试租户（另一个名字）")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40904))
                // 文案必须点名"租户编码"：兜底那句"数据已存在或与已有记录冲突"也含"已存在"，
                // 只断言"已存在"的话两个码长得一样，这条用例就分不出自己落在哪一支
                .andExpect(jsonPath("$.message").value(containsString("租户编码")));

        deleteTenant(token, id);

        // 第二段：软删行占着 —— 唯一索引不含 deleted，那一行仍然占着这个编码
        mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody(code, "编码复用测试租户（复活？）")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40904))
                .andExpect(jsonPath("$.message").value(containsString("租户编码")));
    }

    @Test
    @DisplayName("用户名：活行占着是 40901，软删行占着**也是** 40901（此前是兜底的 40900）")
    void shouldRejectReusingSoftDeletedUsername() throws Exception {
        String token = loginAsAdmin();
        String username = uniqueUsername();
        long id = createUser(token, username);

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value(containsString("用户名")));

        deleteUser(token, id);

        // 索引是 (tenant_id, username)：删掉的人身上可能还挂着角色成员关系，
        // 复活它会把这些权限一起放回来 —— 所以这里与租户同侧，选拒绝而不是复活
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value(containsString("用户名")));
    }

    /* ==================== 反例：不该被顺手一起挡掉的两件事 ==================== */

    @Test
    @DisplayName("租户名称没有唯一索引：软删之后可以复用（软删本该释放这个值）")
    void shouldStillAllowReusingTenantNameWithoutUniqueIndex() throws Exception {
        String token = loginAsAdmin();
        String name = "无索引列测试租户 " + uniqueTenantCode();
        long id = createTenant(token, uniqueTenantCode(), name);
        deleteTenant(token, id);

        // 走上一条是为了把"建了再删"与"删了再建"分开；这里换编码、**留名字**。
        // 如果哪天有人把名称判重也改成"看得见软删行"，这条会红 —— 那正是它该做的事：
        // 十个列里只有六个被索引逼着要额外回答一句，另外那些照旧按"软删即释放"走
        createTenant(token, uniqueTenantCode(), name);
    }

    @Test
    @DisplayName("删掉之后换一个新编码 / 新用户名：照常能建（拒绝的是**那个值**，不是这个动作）")
    void shouldStillAllowAFreshCodeAfterDelete() throws Exception {
        String token = loginAsAdmin();

        long tenantId = createTenant(token, uniqueTenantCode(), "换新编码测试租户");
        deleteTenant(token, tenantId);
        createTenant(token, uniqueTenantCode(), "换新编码测试租户");

        long userId = createUser(token, uniqueUsername());
        deleteUser(token, userId);
        createUser(token, uniqueUsername());
    }

    /* ==================== 夹具 ==================== */

    /**
     * 建号用的口令：**直接取种子账号那一个**，不在本文件里再刻一份字面量。
     *
     * <p>{@code application-h2.yml} 是口令的真值来源；种子口令满足默认策略，
     * 因此本片不会因为"策略调紧了"而单独变红 —— 它没有第二份需要跟着改的值。
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

    /** 建一个顶级租户，返回主键 */
    private long createTenant(String token, String tenantCode, String tenantName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBody(tenantCode, tenantName)))
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

    /**
     * 在当前登录者的租户里建一个号，返回主键。
     *
     * <p><b>不出现 {@code tenantId} 这个键</b>（而不是写成 {@code null}）：缺省落在自己的租户，
     * 这样本片就不必先建一个租户 —— 而它建出来的租户个个带唯一编码，
     * 与本片自己要查的那件事搅在一起会盖住真正的失败原因。
     */
    private long createUser(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username)))
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

    private String tenantBody(String tenantCode, String tenantName) {
        return """
                {"tenantCode":"%s","tenantName":"%s"}
                """.formatted(tenantCode, tenantName);
    }

    private String userBody(String username) {
        return """
                {"username":"%s","displayName":"编码复用测试用户","password":"%s"}
                """.formatted(username, seedPassword());
    }

    /* 编码一律以字母开头：`Long.toString(n, 36)` 可能以数字开头，而编码首字符要求是字母 ——
       直接用纳秒时间戳会偶尔被自己的 DTO 校验拦下，变成一条"大部分时候通过"的假测试 */

    private static String uniqueTenantCode() {
        return "e2euc" + Long.toString(System.nanoTime(), 36);
    }

    private static String uniqueUsername() {
        return "e2eucu" + Long.toString(System.nanoTime(), 36);
    }

}
