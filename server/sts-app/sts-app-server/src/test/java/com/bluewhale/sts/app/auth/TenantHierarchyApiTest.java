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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户树的**层级判据**：删除守卫，以及父级 {@code hasChild} 的重算（ADR-0053）。
 *
 * <p><b>本片存在的理由是把两件此前无人钉住的事钉住。</b>
 *
 * <p>一是 <b>删除守卫的 40913 在租户域零用例</b>：{@code HIERARCHY_CYCLE}
 * 在主代码里有 13 处用法（8 处"成环／自挂" + 5 处"删除守卫"），而在用例里此前一处都没有
 * 碰到**租户**那一处 —— 五个域的删除守卫形状完全相同，
 * 组织（{@code OrganizationApiTest}「有下级（40913）或有成员归属（40919）时拒绝删除」）、
 * 资源（{@code ResourceApiTest}「有子资源时拒绝删除（40913）」）、
 * 用户组（{@code UserGroupApiTest}「删除守卫：仍有成员 40918；仍有子组 40913」）三个域有断言，
 * <b>角色与租户两个域都没有</b>（{@code RoleApiTest} 里 {@code 40913} 零命中）。
 * 租户侧唯一被用过的那次是 {@code TenantReferenceApiTest} 的夹具"删一个租户"，
 * 而那条路的期望是**成功**。角色那条**不在本片射程**（角色树没有 {@code has_child} 列），
 * 见 ADR-0053 的未覆盖。
 *
 * <p>二是 <b>父级的 {@code hasChild} 会陈旧</b>：租户侧此前只在父级从 0 变 1 时标记，
 * **反向不重算** —— 建一个子租户、再把它删掉，父级会永远停在 {@code hasChild = 1}，
 * 出参从此对一个不存在的子节点说"是"。本片的第二条用例就是这次改动的断言。
 *
 * <p><b>它钉的不是"删完会不会归零"，而是"归零是不是按真值算的"。</b>
 * 两个子节点删掉一个之后，父级**必须仍是 1** —— 一个"删除时顺手置 0"的实现能通过
 * 只测一个子节点的用例，却会把"还有兄弟节点"这件事说成没有。所以这条用例建两个。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code TenantReferenceApiTest} 同一理由：
 * 要看的是"库里真正留下了什么" —— {@code hasChild} 是一次独立的 {@code update} 写进去的，
 * 测试事务回滚会把它与外层读到的值一起抹平，恰好掩盖本片要查的东西。
 *
 * <p><b>碰的是自己新建的租户</b>：本片不动种子租户，也不假设它的 {@code hasChild}
 * 是几（种子租户下有没有子租户不是本片的判据）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantHierarchyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 删除守卫：有子租户就删不掉 ==================== */

    @Test
    @DisplayName("有子租户时删父级 → 40913（这条守卫此前在租户域零用例）")
    void shouldRejectDeletingTenantWithChildren() throws Exception {
        String token = loginAsAdmin();
        long parentId = createTenant(token, uniqueTenantCode(), null);
        createTenant(token, uniqueTenantCode(), parentId);

        mockMvc.perform(delete("/api/auth/tenants/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40913))
                // 文案点名"子租户"：含糊的"层级关系不合法"会把调用方引去查父级链，
                // 而这里的原因只有一个（与 40913 的另外几个用法——环、自挂——区分开）
                .andExpect(jsonPath("$.message").value(containsString("子租户")));
    }

    /* ==================== 父级 hasChild：按真值重算，两个方向都要对 ==================== */

    @Test
    @DisplayName("父级 hasChild 按真值重算：删掉一个子租户仍为 1，删掉最后一个才归 0")
    void shouldRecomputeParentHasChildOnEveryDelete() throws Exception {
        String token = loginAsAdmin();
        long parentId = createTenant(token, uniqueTenantCode(), null);
        long firstChildId = createTenant(token, uniqueTenantCode(), parentId);
        long secondChildId = createTenant(token, uniqueTenantCode(), parentId);

        // 建完两个子租户：0 → 1
        assertHasChild(token, parentId, 1, "挂上第一个子租户之后");

        deleteTenant(token, firstChildId);

        // 关键的一次：还有兄弟节点，所以必须是 1 —— 这一条能把
        // "删除时把父级顺手置 0"的实现当场打红（那种实现在只建一个子节点的用例里看不出来）
        assertHasChild(token, parentId, 1, "删掉两个子租户中的一个之后（父级仍有下级）");

        deleteTenant(token, secondChildId);

        // 1 → 0：本次改动之前，这里会停在 1（父级从此对一个不存在的子节点说"是"）
        assertHasChild(token, parentId, 0, "删掉最后一个子租户之后");
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

    /** 建一个租户；{@code parentId} 为 null 时是顶级租户（**不带这个键**，而不是带 null） */
    private long createTenant(String token, String tenantCode, Long parentId) throws Exception {
        String body = parentId == null
                ? """
                {"tenantCode":"%s","tenantName":"层级测试租户 %s"}
                """.formatted(tenantCode, tenantCode)
                : """
                {"tenantCode":"%s","tenantName":"层级测试租户 %s","parentId":%d}
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

    private void deleteTenant(String token, long id) throws Exception {
        mockMvc.perform(delete("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 断言某个租户的 {@code hasChild}，失败时把"在哪个时刻"与期望值一起写进消息。
     *
     * <p>读的是详情接口而不是列表：{@code hasChild} 是 {@code TenantVO} 的字段，
     * 两个接口都会带出来，但详情是"这一行现在的值"最直接的问法，
     * 不受分页与排序影响。
     *
     * <p>用 {@code assertEquals} 而不是 {@code jsonPath(...).value(...)}：
     * 三个断言点问的是同一件事、只差发生时刻，失败时必须一眼看出是哪一步错的 ——
     * 否则"删掉之后"与"删掉之前"两条消息长得一模一样。
     */
    private void assertHasChild(String token, long tenantId, int expected, String when) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number actual = JsonPath.read(result.getResponse().getContentAsString(), "$.data.hasChild");
        assertEquals(expected, actual.intValue(), when + "，父级 hasChild 应为 " + expected);
    }

    /** 编码一律以字母开头：{@code Long.toString(n, 36)} 可能以数字开头，而编码首字符要求是字母 */
    private static String uniqueTenantCode() {
        return "e2eth" + Long.toString(System.nanoTime(), 36);
    }

}
