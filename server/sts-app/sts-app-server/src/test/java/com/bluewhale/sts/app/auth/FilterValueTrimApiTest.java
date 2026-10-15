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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「同一条过滤值，在 A 域搜得到、在 B 域也搜得到吗」—— <b>精确匹配</b>的过滤值
 * （{@code status} / {@code app} / {@code level} …）要不要吃掉两端空白，
 * 与关键词（{@code SearchKeywordTrimApiTest} · ADR-0068）是<b>同一件事的另一半</b>。
 *
 * <p><b>现状（写本类时实测）。</b>取值口径分三形态：
 * <ul>
 *   <li><b>枚举归一</b>（角色 {@code status} / 资源 {@code resourceType} / 成员 {@code memberType}）：
 *       先 {@code XxxType.parse(raw)}，认得出就返回规范的 {@code wireName}。而
 *       {@code parse} 内部是 {@code wire.equalsIgnoreCase(value.trim())} —— <b>它自己就 trim 了</b>，
 *       所以这一支对两端空白已经健壮；</li>
 *   <li><b>纯 trim</b>（组织 {@code status} / {@code orgType}、角色 {@code category}、资源 {@code parentCode}）：
 *       走本域私有的 {@code normalize()}（trim + 空串转 {@code null}），同样健壮；</li>
 *   <li><b>只判空</b>（用户 {@code status}、租户 {@code status}，以及日志 / 事件 / 审计三域的全部
 *       字符串过滤值）：{@code hasText()} 或 {@code != null && !isBlank()}，然后<b>把原值</b>
 *       交给 {@code eq} —— 两端带空白时<b>一条也匹配不到</b>。</li>
 * </ul>
 * 三类对 <b>null</b> 与<b>纯空白</b>的处置一致（都退化成"不过滤"），唯一的分叉在<b>两端带空白</b>时。
 *
 * <p><b>为什么这算缺陷而不是"三种风格"。</b>两个理由，第二个是硬的：
 * <ol>
 *   <li>与关键词同源：{@code status} 是同一套值（{@code ENABLED} / {@code DISABLED}）、
 *       同一个参数名，在组织域搜得到、在用户域搜不到 —— 前端只有一个下拉框，
 *       用户看不出这两个页面为什么不一样；</li>
 *   <li><b>库里的值不会有两端空白</b>（各域写库前都过了一遍 {@code normalize} 或 {@code parse}），
 *       所以 {@code ?status=%20ENABLED%20} 不是一个"想精确匹配带空格的"的意图 ——
 *       它<b>永远</b>匹配不到任何行。这一处的差异不承载任何区分能力。</li>
 * </ol>
 *
 * <p><b>以及一处真实的内部不一致。</b>日志 / 事件 / 审计三域的聚合统计走的是<b>手写 SQL</b>，
 * 那里的判空是 Ognl 的 {@code <if test="app != null and app != ''">}，而同一请求的列表走的是
 * Java 的 {@code hasText()}（{@code isBlank}）—— 两者对 <b>{@code "   "}</b> 的判定<b>相反</b>：
 * 前者认为"给了值"（于是 {@code AND app = '   '}），后者认为"没给"（于是不过滤）。
 * 症状是同一个请求里 {@code total} 与各级别条数之和对不上（见
 * {@code shouldKeepStatisticsInSyncWithPage}）。
 *
 * <p>与 {@code TextEqualityApiTest} / {@code SearchMatchApiTest} / {@code SearchWildcardApiTest} /
 * {@code SearchKeywordTrimApiTest} 同形：<b>不 {@code @Transactional}、不按 profile 跳过</b>，
 * 同一批断言在 h2 与真实 MySQL 两个 job 上各跑一遍。{@code trim()} 是 Java 侧的行为、
 * 本不依赖数据库，但判空口径一旦下沉到公共实现，两边必须给出同一份结果集。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FilterValueTrimApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Test
    @DisplayName("用户域：status 两端的空白按忽略处理 —— 该与不带空白同义")
    void shouldIgnoreSurroundingWhitespaceInUserStatus() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(userSearch(token, "ENABLED"), userSearch(token, "  ENABLED  "),
                "用户域：两端带空白的 status 应当与不带空白给出同一个结果集"
                        + "（不同 = 该域把原值直接交给了 eq，而组织 / 角色 / 用户组做了规范化）");
    }

    @Test
    @DisplayName("组织域：同一条 status 同样该与不带空白同义（与用户域口径一致）")
    void shouldIgnoreSurroundingWhitespaceInOrgStatus() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(orgSearch(token, "ENABLED"), orgSearch(token, "  ENABLED  "),
                "组织域：两端带空白的 status 应当与不带空白给出同一个结果集");
    }

    @Test
    @DisplayName("租户域：同一条 status 同样该与不带空白同义（与组织域口径一致）")
    void shouldIgnoreSurroundingWhitespaceInTenantStatus() throws Exception {
        String token = loginAsAdmin();

        assertSameResultSet(tenantSearch(token, "ENABLED"), tenantSearch(token, "  ENABLED  "),
                "租户域：两端带空白的 status 应当与不带空白给出同一个结果集");
    }

    @Test
    @DisplayName("日志域：app 两端的空白按忽略处理 —— 该与不带空白同义")
    void shouldIgnoreSurroundingWhitespaceInLogApp() throws Exception {
        String token = loginAsAdmin();
        String app = "it-filter-" + uniqueTag();
        report(token, app, 1);

        assertSameResultSet(logSearch(token, app), logSearch(token, "  " + app + "  "),
                "日志域：两端带空白的 app 应当与不带空白给出同一个结果集"
                        + "（该域此前用的是 hasText + 原值，不 trim）");
    }

    @Test
    @DisplayName("日志域：聚合的两条 SQL 路径判空口径必须一致（级别条数之和 = 总数）")
    void shouldKeepStatisticsInSyncWithPage() throws Exception {
        String token = loginAsAdmin();
        String app = "it-filter-sync-" + uniqueTag();
        report(token, app, 3);

        MvcResult list = logSearch(token, app);
        int lineTotal = totalOf(list);
        assertTrue(lineTotal > 0, "本租户至少应当有本次刚上报的日志，否则下面两条是平凡通过的");

        // 这一条不是"顺手断言一下"——它钉的是"列表走 lambda wrapper（Java 的 hasText）、
        // 分布走手写 SQL（Ognl 的 != ''）"这一对实现必须给出同一个判定。
        // 空白值是最容易让两者分道扬镳的输入：Java 说"没给"，Ognl 说"给了"。
        statsLevelsSumEqualsTotal(token, "   ", "纯空白 app：分布走手写 SQL、总行走 wrapper，两者必须同义");
        statsLevelsSumEqualsTotal(token, app, "正常 app:分布与总数必须对得上");
    }

    @Test
    @DisplayName("纯空白过滤值 = 不过滤：与完全不传的结果一样（三域同义）")
    void shouldTreatBlankFilterAsNoFilter() throws Exception {
        String token = loginAsAdmin();

        // 这一条钉的是"空白值不能变成'搜一个空格'"。三类形态在这一点上本来就一致，
        // 所以它是**回归钉**：把判空下沉到公共实现时，最容易顺手弄坏的就是这一支。
        int allUsers = totalOf(userSearch(token, null));
        assertTrue(allUsers > 0, "库里至少应当有种子账号，否则下面这条是平凡通过的");
        assertEquals(allUsers, totalOf(userSearch(token, "   ")),
                "用户域：纯空白 status 应当与不传 status 同义（不过滤）");

        int allOrgs = totalOf(orgSearch(token, null));
        assertTrue(allOrgs > 0, "库里至少应当有种子组织，否则下面这条是平凡通过的");
        assertEquals(allOrgs, totalOf(orgSearch(token, "   ")),
                "组织域：纯空白 status 应当与不传 status 同义（不过滤）");

        int allTenants = totalOf(tenantSearch(token, null));
        assertTrue(allTenants > 0, "库里至少应当有种子租户，否则下面这条是平凡通过的");
        assertEquals(allTenants, totalOf(tenantSearch(token, "   ")),
                "租户域：纯空白 status 应当与不传 status 同义（不过滤）");
    }

    /* ==================== 断言辅助 ==================== */

    /**
     * 断"两条请求给出同一个结果集"，并且**先确认它非空** ——
     * 否则一个"两边都是 0 条"的实现也会通过，而那恰恰是缺陷本身的样子。
     */
    private void assertSameResultSet(MvcResult clean, MvcResult padded, String message) throws Exception {
        int cleanTotal = totalOf(clean);
        assertTrue(cleanTotal > 0, message + "（干净值本来就 0 条，这条断言无从判定）");
        assertEquals(cleanTotal, totalOf(padded), message + "（干净值 " + cleanTotal + " 条，带空白 "
                + totalOf(padded) + " 条）");
        assertEquals(idsOf(clean), idsOf(padded), message);
    }

    /** 断"聚合里的级别分布之和 = 总数" —— 手写 SQL 与 lambda wrapper 判空口径的一致性守卫。 */
    private void statsLevelsSumEqualsTotal(String token, String app, String message) throws Exception {
        MvcResult stats = logStats(token, app);
        int total = ((Number) JsonPath.read(stats.getResponse().getContentAsString(), "$.data.total")).intValue();
        List<Integer> counts = JsonPath.read(stats.getResponse().getContentAsString(),
                "$.data.levels[*].levelCount");
        int sum = counts.stream().mapToInt(Integer::intValue).sum();
        assertEquals(total, sum, message + "（总数 " + total + " 条，各级别之和 " + sum + " 条）");
    }

    private int totalOf(MvcResult result) throws Exception {
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        return total.intValue();
    }

    private List<Integer> idsOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.records[*].id");
    }

    /* ==================== 请求夹具 ==================== */

    /** {@code value} 传 {@code null} 表示<b>不带这个参数</b>——与"带一个空串"不是同一件事。 */
    private MvcResult userSearch(String token, String status) throws Exception {
        return search(token, "/api/auth/users", "status", status);
    }

    private MvcResult orgSearch(String token, String status) throws Exception {
        return search(token, "/api/auth/orgs", "status", status);
    }

    private MvcResult tenantSearch(String token, String status) throws Exception {
        return search(token, "/api/auth/tenants", "status", status);
    }

    private MvcResult logSearch(String token, String app) throws Exception {
        return search(token, "/api/logs/client", "app", app);
    }

    private MvcResult logStats(String token, String app) throws Exception {
        return search(token, "/api/logs/client/stats", "app", app);
    }

    private MvcResult search(String token, String path, String paramName, String value) throws Exception {
        MockHttpServletRequestBuilder request = get(path)
                .header("Authorization", "Bearer " + token);
        if (value != null) {
            request = request.param(paramName, value);
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
     * 上报若干条同一 app 的日志并确认落库。带令牌上报 —— 这样日志才有归属，
     * 后面的查询才查得到（不带令牌也成功，但那批谁也查不出来）。
     */
    private void report(String token, String app, int count) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"level": "info", "message": "第 %d 条", "timestamp": %d}
                    """.formatted(i, i));
        }
        mockMvc.perform(post("/api/logs/client")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app\": \"" + app + "\", \"logs\": [" + items + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(count));
    }

    /**
     * 每次跑用一个新的短标签，用它把断言限定在本次造的那一行上：
     * 库里已有别的租户 / 用户 / 组织，靠"恰好 1 条"来判定就必须让这个词只属于一行。
     * 标签只用字母与数字（36 进制），因为 app 与组织编码都有形状约束。
     */
    private static String uniqueTag() {
        return "F" + Long.toString(System.nanoTime(), 36) + UUID.randomUUID().toString().substring(0, 4);
    }

}
