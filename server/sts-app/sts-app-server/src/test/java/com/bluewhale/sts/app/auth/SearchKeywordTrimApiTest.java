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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「同一条关键词，在用户域搜得到、在组织域也搜得到吗」—— 关键词的<b>规范化</b>
 * （要不要吃掉两端空白）此前<b>各域各写各的</b>，从没被统一决定过。
 *
 * <p><b>现状（写本类时实测）。</b>七个搜索入口分两种口径：
 * <ul>
 *   <li>组织 / 角色 / 用户组：取值前先过一遍本域私有的 {@code normalize()}——
 *       {@code null} 进 {@code null} 出、全空白也出 {@code null}、否则 {@code trim()} 后返回；</li>
 *   <li>用户 / 租户 / 资源 / 客户端日志：直接 {@code query.getKeyword()} 交给
 *       {@code KeywordLike.pattern()}，只判空<b>不 trim</b>。</li>
 * </ul>
 * 两种口径对 <b>null</b> 与<b>纯空白</b>的处置是一致的（都退化成"不过滤"），
 * 唯一的差别在<b>两端带空白</b>时：{@code "  error  "} 在前一种口径下等价于
 * {@code "error"}、在后一种口径下是"名字里真有一个空格"。
 *
 * <p><b>为什么这算缺陷而不是"两种风格"。</b>前端只有一个搜索框，
 * 用户输入 {@code "  error  "}（从别处复制粘贴来的几乎必经这一手）表达的是同一个意图；
 * 而 {@code LIKE '%…%'} 是包含匹配，<b>前后空白没有任何语义价值</b>——
 * 没有任何人能靠它表达"我要找以空格开头的那条"。也就是说这一处的差异
 * 不承载任何区分能力，只承载"在某几个页面上搜不到"。
 *
 * <p><b>为什么不能靠"前端已经 trim 了"兜住。</b>实测：五个分页搜索框
 * （用户 / 用户组 / 租户 / 角色 / 资源）确实在提交时 {@code trim()}，
 * 但<b>客户端日志那个搜索框一次都不 trim</b>（它是逐键上送、连提交中间态都没有），
 * 组织页则根本没有搜索框（有意只有树视图）。所以这条"前端兜住"的说法
 * 在第七个入口上当场不成立 —— 后端才是权威边界，不能用前端的顺手实现当代替。
 *
 * <p>与 {@code TextEqualityApiTest} / {@code SearchMatchApiTest} /
 * {@code SearchWildcardApiTest} 同形：<b>不 {@code @Transactional}、不按 profile 跳过</b>，
 * 同一批断言在 h2 与真实 MySQL 两个 job 上各跑一遍。{@code trim()} 是 Java 侧的行为、
 * 本不依赖数据库，但这批用例同时会走到 {@code LIKE} 上，
 * 而"关键词规范化之后长什么样"在两边必须一致，所以一并跨 profile 跑。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchKeywordTrimApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Test
    @DisplayName("用户域：关键词两端的空白按忽略处理 —— 该搜得到")
    void shouldIgnoreSurroundingWhitespaceInUserSearch() throws Exception {
        String token = loginAsAdmin();
        String tag = uniqueTag();
        long hit = createUser(token, "U" + tag, tag + "姓名");

        assertSoleMatch(userSearch(token, "  " + tag + "  "), hit,
                "用户域：两端带空白的关键词应当与不带空白同义"
                        + "（搜不到 = 该域没做规范化，而组织 / 角色 / 用户组做了）");
    }

    @Test
    @DisplayName("组织域：同一条关键词同样该搜得到（与用户域口径一致）")
    void shouldIgnoreSurroundingWhitespaceInOrgSearch() throws Exception {
        String token = loginAsAdmin();
        String tag = uniqueTag();
        long hit = createOrg(token, "E2E" + tag, tag + "部门", null);

        assertSoleMatch(orgSearch(token, "  " + tag + "  "), hit,
                "组织域：两端带空白的关键词应当与不带空白同义");
    }

    @Test
    @DisplayName("纯空白关键词 = 不过滤：与完全不传关键词的结果一样（两域同义）")
    void shouldTreatBlankKeywordAsNoFilter() throws Exception {
        String token = loginAsAdmin();

        // 这一条钉的是"空白关键词不能变成'搜一个空格'"。两种口径在这一点上本来就一致，
        // 所以它是**回归钉**：把规范化下沉到公共实现时，最容易顺手弄坏的就是这一支。
        int allUsers = totalOf(userSearch(token, null));
        assertTrue(allUsers > 0, "库里至少应当有种子账号，否则下面两条断言是平凡通过的");
        assertEquals(allUsers, totalOf(userSearch(token, "   ")),
                "用户域：纯空白关键词应当与不传关键词同义（不过滤）");

        int allOrgs = totalOf(orgSearch(token, null));
        assertTrue(allOrgs > 0, "库里至少应当有种子组织，否则下面这条断言是平凡通过的");
        assertEquals(allOrgs, totalOf(orgSearch(token, "   ")),
                "组织域：纯空白关键词应当与不传关键词同义（不过滤）");
    }

    @Test
    @DisplayName("只吃两端、不吃中间：关键词里的内部空格是它的一部分")
    void shouldKeepInnerWhitespace() throws Exception {
        String token = loginAsAdmin();
        String tag = uniqueTag();
        long hit = createUser(token, "U" + tag, tag + "a b");

        // 两条断言一起才钉得住"只 trim 两端"：先把内部空格当字面量搜到（第一条），
        // 再证明它没被吃掉（第二条）—— 只写第一条的话，一个把空白全部删掉的实现
        // （`replaceAll("\\s", "")`）也会绿。
        assertSoleMatch(userSearch(token, " " + tag + "a b "), hit,
                "两端空白被吃掉、中间的空格必须留着");
        assertEquals(0, totalOf(userSearch(token, tag + "ab")),
                "内部空格是关键词的一部分：把它删掉就变成搜另一个词了");
    }

    /* ==================== 断言辅助 ==================== */

    /** 断"恰好只命中这一条"，并核对命中的就是那一条。 */
    private void assertSoleMatch(MvcResult result, long expectedId, String message) throws Exception {
        assertEquals(1, totalOf(result), message + "（命中 " + totalOf(result) + " 条）");
        assertEquals(expectedId, idsOf(result).get(0).longValue(), message);
    }

    private int totalOf(MvcResult result) throws Exception {
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        return total.intValue();
    }

    private List<Integer> idsOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.records[*].id");
    }

    /* ==================== 请求夹具 ==================== */

    /**
     * 走用户搜索的真路径。{@code keyword} 传 {@code null} 表示<b>不带这个参数</b>——
     * 与"带一个空串"不是同一件事，两者都要能跑到，所以不能用空串代替代 null。
     */
    private MvcResult userSearch(String token, String keyword) throws Exception {
        return search(token, "/api/auth/users", keyword);
    }

    private MvcResult orgSearch(String token, String keyword) throws Exception {
        return search(token, "/api/auth/orgs", keyword);
    }

    private MvcResult search(String token, String path, String keyword) throws Exception {
        MockHttpServletRequestBuilder request = get(path)
                .header("Authorization", "Bearer " + token);
        if (keyword != null) {
            request = request.param("keyword", keyword);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
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
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /**
     * 建号并返回主键。口令取种子账号那一个，不在本文件里另刻字面量 ——
     * 口令策略收紧时本片不会单独变红（与 {@code SearchWildcardApiTest} 同一理由）。
     *
     * <p>空白放在 {@code displayName} 里：登录名有 {@code [a-zA-Z0-9_.-]} 的形状约束、
     * 装不下空格，而姓名是自由文本、也正是搜索会去比的三列之一。
     */
    private long createUser(String token, String username, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","password":"%s"}
                                """.formatted(username, displayName, properties.getSeedAdmin().getPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createOrg(String token, String code, String name, Long parentId) throws Exception {
        StringBuilder json = new StringBuilder("{\"orgCode\":\"").append(code)
                .append("\",\"orgName\":\"").append(name).append('"');
        if (parentId != null) {
            json.append(",\"parentId\":").append(parentId);
        }
        json.append('}');

        MvcResult result = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /**
     * 每次跑用一个新的短标签，用它把断言限定在本次造的那一行上：
     * 库里已有别的租户 / 用户 / 组织，靠"恰好 1 条"来判定就必须让这个词只属于一行。
     * 标签只用字母与数字（36 进制），因为组织编码有"字母开头"的形状约束。
     */
    private static String uniqueTag() {
        return "Tk" + Long.toString(System.nanoTime(), 36);
    }

}
