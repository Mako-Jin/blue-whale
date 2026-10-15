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
 * 「搜索框发出去的 keyword，后端拿它怎么比」—— 那是 {@code LIKE '%kw%'}，
 * 一个**模糊匹配**，不是等值。
 *
 * <p><b>它此前没有被决定过</b>，于是由列排序规则代答 —— 而三个方言的默认值不同：
 * MySQL 上 {@code '%ADMIN%'} 能匹配到 {@code admin}、H2 上不能（当次实测见
 * {@code decisions/0066}）。也就是说，同一个搜索框在两个 profile 下给**不同的结果**。
 * 而 {@code mysql/V8} 把 MySQL 转成 {@code utf8mb4_0900_as_cs} 之后，
 * 两边**都**变成区分大小写了 —— 那是一次顺带的对齐，此前没有人写过、也没有用例钉过。
 *
 * <p><b>与 {@link TextEqualityApiTest} 的关系</b>：那一片钉的是**等值**
 * （{@code =} 与 {@code UNIQUE}），这一片钉的是**模糊匹配**（{@code LIKE}）。
 * 两者**不是同一个关系**，但敏感性同源（都由列排序规则决定）——
 * 所以"搜索跟着等值一起区分大小写"是 {@code 0066} 的决定，不是 {@code 0065} 的推论。
 *
 * <p>与那一片同形：**不 {@code @Transactional}、不按 profile 跳过** ——
 * 同一批断言在 h2（{@code server} job）与真实 MySQL（{@code server-mysql} job）
 * 上各跑一遍。这是本仓第二处**跨 profile 的语义断言**。
 *
 * <p><b>为什么"搜不到"那一支才是关键</b>：只断言"大小写一致时搜得到"的话，
 * 一个大小写**不敏感**的库也会绿（它的匹配集是超集）。要分开两种语义，
 * 必须把"**换个大小写就搜不到**"写出来 —— 那正是两种语义唯一不同的那一格。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchMatchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Test
    @DisplayName("搜索区分大小写：换一个大小写就一条都搜不到（LIKE 跟着列排序规则走）")
    void shouldSearchCaseSensitively() throws Exception {
        String token = loginAsAdmin();
        // 大小写混写，且登录名与邮箱共用同一段前缀 —— 两列在同一个 OR 组里、
        // 用同一张排序规则，所以一次断言同时盖住它们
        String name = "Szq" + Long.toString(System.nanoTime(), 36);
        long id = createUser(token, name, name + "@example.com");

        // 1 原样（大小写一致）：应当命中刚建的那一个
        MvcResult hit = search(token, name);
        List<Integer> ids = JsonPath.read(hit.getResponse().getContentAsString(), "$.data.records[*].id");
        assertTrue(ids.contains((int) id), "大小写一致时应当搜得到刚建的号");

        // 2 全小写：一条都不该命中 —— 它同时是"V8 到底改了什么"的证词
        //   （改前 MySQL 上这里不是 0）
        assertEquals(0, totalOf(search(token, name.toLowerCase())),
                "换个大小写应当一条都不匹配");
    }

    @Test
    @DisplayName("具体到用户会去搜的那个号：`admin` 搜得到、`ADMIN` 搜不到")
    void shouldNotFindTheSeedAdminUnderADifferentCase() throws Exception {
        String token = loginAsAdmin();
        // 种子账号本来就在库里（两个 profile 都种），不必自建夹具 ——
        // 而且它正是"人真的会去搜"的那个串
        String seedUsername = properties.getSeedAdmin().getUsername();

        assertTrue(totalOf(search(token, seedUsername)) >= 1,
                "按种子登录名原样应当搜得到它自己");

        // ★ 这一行是"搜索区分大小写"最直观的证词：曾经在 MySQL 上搜得到
        assertEquals(0, totalOf(search(token, seedUsername.toUpperCase())),
                "把登录名换成大写之后，不应当再匹配到任何一行");
    }

    /* ==================== 夹具 ==================== */

    /** 走前端那六个搜索框用的同一条真路径（{@code GET /api/auth/users?keyword=…}） */
    private MvcResult search(String token, String keyword) throws Exception {
        return mockMvc.perform(get("/api/auth/users")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    /**
     * 断 {@code total} 而不只断"结果里没有那个 id"：本片要证的是**这个关键字不匹配任何行**，
     * 而不是"匹配了、只是没翻到那一页"。
     */
    private int totalOf(MvcResult result) throws Exception {
        Number total = JsonPath.read(result.getResponse().getContentAsString(), "$.data.total");
        return total.intValue();
    }

    private String loginAsAdmin() throws Exception {
        return login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
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

    /**
     * 建一个号，返回主键。口令**直接取种子账号那一个**，不在本文件里再刻一份字面量 ——
     * 与 {@code TextEqualityApiTest} / {@code UniqueCodeReuseApiTest} 同一理由：
     * 口令策略调紧时本片不会单独变红。
     */
    private long createUser(String token, String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"搜索匹配测试用户","email":"%s","password":"%s"}
                                """.formatted(username, email, properties.getSeedAdmin().getPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

}
