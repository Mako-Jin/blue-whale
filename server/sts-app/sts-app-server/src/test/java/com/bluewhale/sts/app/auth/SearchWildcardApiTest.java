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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「搜索框里输 {@code %} / {@code _} 会怎样」—— 它此前是 {@code LIKE '%kw%'} 把实参
 * <b>原样拼</b>进去，于是 {@code %} 匹配**全部行**、{@code _} 匹配任意单字符。
 *
 * <p><b>为什么这是缺陷而不是特性。</b>搜索框上写的是"按登录名 / 姓名 / 邮箱搜索"，
 * 用户输 {@code %} 的意思是"我要找带 % 的那条"（在日志正文里尤其常见：占位符、
 * 格式化串），不是一个没人知道的通配符语法。而 MyBatis-Plus 的 {@code like()}
 * 只做 {@code "%" + value + "%"}（其 {@code SqlUtils.concatLike} 的实现，
 * 反射看到的字节码里没有任何转义）—— 没有任何一层在转义。
 *
 * <p><b>修法是转义，且把转义符写进 SQL</b>（{@code LIKE {0} ESCAPE '!'}）：
 * 反斜杠那条路是 {@code LIKE} 的**默认**转义符，用它就等于把"转义是否生效"
 * 交给数据库默认值代答 —— 而 MySQL 开了 {@code NO_BACKSLASH_ESCAPES} 之后
 * 它**静默失效**（实测 {@code n=0}、不报错）。理由与取舍见 {@code decisions/0067}。
 *
 * <p><b>为什么每条断言都要有"该匹配的"与"不该匹配的"两半。</b>只断言"搜得到"的话，
 * 出事的那种实现（把 {@code %} 当通配符）**也会绿** —— 它的匹配集是超集
 * （{@code 0066} 那一轮已经吃过一次这个形状的亏）。所以每条都给一个**只差一个字符**的
 * 陪跑行：转义对了只命中一个，没转义就命中两个。
 *
 * <p>与 {@code TextEqualityApiTest} / {@code SearchMatchApiTest} 同形：
 * <b>不 {@code @Transactional}、不按 profile 跳过</b> —— 同一批断言在 h2 与真实 MySQL
 * 两个 job 上各跑一遍。转义符与 {@code ESCAPE} 子句在三方言上是否同义，正是这类事实里
 * 最该跨 profile 验的一种。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchWildcardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Test
    @DisplayName("输 % 是字面量：只命中真的含 % 的那条，而不是列出全表")
    void shouldTreatPercentAsLiteral() throws Exception {
        String token = loginAsAdmin();
        // 两个行只差一个字符：正主里是 `%`，陪跑里是 `x`。
        // 陪跑行的存在是这条用例能不能判定的关键 —— 没有它，"列出了全表"与
        // "只命中了正主"在读数上分不开。
        String tag = "Tk" + Long.toString(System.nanoTime(), 36);
        long hit = createUser(token, "U" + tag + "h", tag + "a%b");
        createUser(token, "U" + tag + "d", tag + "axb");

        assertSoleMatch(token, tag + "a%b", hit,
                "关键词里的 % 应当是字面量：只命中姓名里真写着 % 的那一个");
    }

    @Test
    @DisplayName("输 _ 是字面量：不会当「任意单字符」把陪跑行一起捞出来")
    void shouldTreatUnderscoreAsLiteral() throws Exception {
        String token = loginAsAdmin();
        String tag = "Tk" + Long.toString(System.nanoTime(), 36);
        long hit = createUser(token, "U" + tag + "h", tag + "a_b");
        createUser(token, "U" + tag + "d", tag + "axb");

        assertSoleMatch(token, tag + "a_b", hit,
                "关键词里的 _ 应当是字面量：陪跑行是 axb，不该被当成 a_b 命中");
    }

    @Test
    @DisplayName("输转义符本身（!）也是字面量：它不该把后面那个字符吃掉")
    void shouldTreatEscapeCharacterAsLiteral() throws Exception {
        String token = loginAsAdmin();
        String tag = "Tk" + Long.toString(System.nanoTime(), 36);
        String displayName = tag + "a!b";
        long id = createUser(token, "U" + tag + "h", displayName);

        // 这一条钉的是转义**自身**：若只转义 % 与 _ 而漏了 !，那么 `Tk…a!b` 里的
        // `!b` 会被当成"转义后的 b"，模式退化成 `…ab` —— 一个字面量里带 ! 的串
        // 就再也搜不到它自己了。这是"转义要不要连转义符一起转"的现场判据。
        assertSoleMatch(token, displayName, id,
                "关键词里的 ! 应当是字面量：它若是转义符而没被转义，正主就搜不到自己");
    }

    @Test
    @DisplayName("回归：不含通配符的关键词照常搜得到（转义不能把普通搜索弄坏）")
    void shouldStillFindByPlainSubstring() throws Exception {
        String token = loginAsAdmin();
        String tag = "Tk" + Long.toString(System.nanoTime(), 36);
        String username = "U" + tag;
        long id = createUser(token, username, tag + "普通姓名");

        MvcResult result = search(token, tag);
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.data.records[*].id");
        assertTrue(ids.contains((int) id), "不含通配符的关键词应当照常命中");
    }

    /* ==================== 夹具 ==================== */

    /** 断"恰好只命中这一条" —— 多于一条就说明通配符被放出去了。 */
    private void assertSoleMatch(String token, String keyword, long expectedId, String message) throws Exception {
        MvcResult result = search(token, keyword);
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        assertEquals(1, total.intValue(),
                message + "（关键词：" + keyword + "；多于 1 条 = 通配符没被转义）");
        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.data.records[*].id");
        assertEquals(expectedId, ids.get(0).longValue(), message);
    }

    /** 走前端那六个搜索框用的同一条真路径（{@code GET /api/auth/users?keyword=…}） */
    private MvcResult search(String token, String keyword) throws Exception {
        return mockMvc.perform(get("/api/auth/users")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + token))
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
     * 建一个号，返回主键。口令**直接取种子账号那一个**，不在本文件里再刻一份字面量 ——
     * 口令策略调紧时本片不会单独变红（与 {@code TextEqualityApiTest} 同一理由）。
     *
     * <p>通配符放在 {@code displayName} 里：登录名有 `[a-zA-Z0-9_.-]` 的形状约束、
     * 装不下 {@code %} 与 {@code !}，而姓名是自由文本、也正是搜索会去比的三列之一。
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

}
