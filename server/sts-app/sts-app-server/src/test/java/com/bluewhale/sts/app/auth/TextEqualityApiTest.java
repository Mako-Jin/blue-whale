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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「两个文本什么时候算相等」—— 这个判据此前**没有别的东西工具写过**，于是它由三个方言
 * 各自的默认排序规则代答，而三个默认值互不相同（ADR-0065）。
 *
 * <p><b>本片同时是那条决定的落点与它的守卫。</b>决定是"区分大小写、也区分重音"，
 * 落地方式是 mysql/V8 把 13 张表转成 {@code utf8mb4_0900_as_cs} —— 但
 * <b>一条只在一个 profile 上成立的断言等于没钉</b>（0005 的同一件事，见 0038 的教训）。
 * 所以本片刻意**不 @Transactional、不 @DisabledOnProfile**：同一批断言在默认的 h2
 * （{@code server} job）与真实 MySQL（{@code server-mysql} job）上各跑一遍，
 * 两个 profile 都绿才算数。它是本仓第一处**跨 profile 的语义断言**。
 *
 * <p><b>断言的是"行为"，不是"排序规则的名字"</b>：本片一个字也没提
 * {@code _ai_ci} / {@code _as_cs} / {@code PAD SPACE}。原因很简单 ——
 * 那三者是**手段**，而 H2 那边根本无手段可写（H2 的默认值恰好就是目标值），
 * 只能靠行为对账。顺带也避免了"改个排序规则名字就绿"这种假通过。
 *
 * <p><b>为什么大小写这一支要写成断言，而不是留给"反正没人用小写邮箱"</b>：
 * 登录就是 {@code WHERE tenant_id = ? AND username = ?}（{@code AuthService#login}
 * 与 {@code UserMapper#selectAnyByTenantAndUsername}），所以
 * "case 算不算同一个"直接决定 <b>{@code ADMIN} 能不能登进 {@code admin} 的账号</b>。
 * 它是一句用户可见的话，不是存储层细节。
 *
 * <p><b>刻意不测的两支</b>（尾空格 / 重音）：入参上有 {@code @Email} 与
 * {@code @Size}，{@code "a@b.com "} 与 {@code "á@b.com"} 会先在 DTO 那一层被拦成
 * 参数错误 —— 走不到判重。它们在 SQL 层量过（见 mysql/V8 与 ADR-0065 的探针表），
 * 这里不重复一遍那条量不出来的路。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextEqualityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 邮箱：完全一样要挡，只差大小写不能挡 ==================== */

    @Test
    @DisplayName("邮箱完全一样：40902，且文案点名「邮箱」（不是兜底的 40900）")
    void shouldRejectExactlyDuplicateEmail() throws Exception {
        String token = loginAsAdmin();
        String email = uniqueUsername() + "@example.com";
        createUser(token, uniqueUsername(), email, null);

        // 断言文案而不只断言码：兜底那句"数据已存在或与已有记录冲突"也含"已存在"，
        // 只比对数字的话，判重有没有真的走到这一支是分不出来的
        expectCreateRejected(token, uniqueUsername(), email, null, "40902", "邮箱");
    }

    @Test
    @DisplayName("邮箱只差大小写：**不算重复** —— 与登录名同一条规则（ADR-0065）")
    void shouldAllowEmailDifferingOnlyInCase() throws Exception {
        String token = loginAsAdmin();
        String base = uniqueUsername();
        long first = createUser(token, base, base + "@example.com", null);

        // 逐字符只在大小写上不同：`A@B.com` 与 `a@b.com`
        long second = createUser(token, uniqueUsername(), base.toUpperCase() + "@EXAMPLE.COM", null);

        // 两条不同的记录，而不是"判重没生效、插进去了同一条"
        assertNotEquals(first, second, "大小写不同的邮箱是两条记录");
    }

    /* ==================== 手机号：同一套判重（40903） ==================== */

    @Test
    @DisplayName("手机号完全一样：40903")
    void shouldRejectExactlyDuplicatePhone() throws Exception {
        String token = loginAsAdmin();
        // 刻意用 `% 10000` 之外的写法：`nanoTime()` 可负，取模之后会拼出带负号的串，
        // 虽然仍是合法入参，但一个"看起来像手机号却不是"的值会让失败时更难读
        String phone = "138" + Math.abs(System.nanoTime() % 100000000L);
        createUser(token, uniqueUsername(), null, phone);

        expectCreateRejected(token, uniqueUsername(), null, phone, "40903", "手机号");
    }

    /* ==================== 登录名：既走判重，也走唯一索引 ==================== */

    @Test
    @DisplayName("登录名只差大小写：能建 —— 唯一索引也按同一条规则放行")
    void shouldAllowUsernameDifferingOnlyInCase() throws Exception {
        String token = loginAsAdmin();
        String lower = uniqueUsername();

        long first = createUser(token, lower, null, null);
        // 这一支比邮箱那支多验一层：登录名上**有唯一索引**
        // （`uk_bw_auth_user_tenant_username`），所以它同时验了服务层判重与库上约束
        long second = createUser(token, lower.toUpperCase(), null, null);

        assertNotEquals(first, second, "大小写不同的登录名是两条记录");
    }

    @Test
    @DisplayName("登录名完全一样：40901（活行与软删行同码，见 UniqueCodeReuseApiTest）")
    void shouldRejectExactlyDuplicateUsername() throws Exception {
        String token = loginAsAdmin();
        String username = uniqueUsername();
        createUser(token, username, null, null);

        expectCreateRejected(token, username, null, null, "40901", "用户名");
    }

    /* ==================== 这条决定落到用户可见处：登录 ==================== */

    @Test
    @DisplayName("登录区分大小写：换一个大小写登不进去（这一句是本片真正的用户可见面）")
    void shouldNotAcceptALoginThatDiffersOnlyInCase() throws Exception {
        String token = loginAsAdmin();
        String username = uniqueUsername();
        createUser(token, username, null, null);

        // 先证明这个号本身能登：否则下面那次失败可能只是"号没建成"
        login(seedTenantCode(), username, seedPassword());

        // 再换大小写：同一个口令、同一个租户，只差 case
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(seedTenantCode(), username.toUpperCase(), seedPassword())))
                .andExpect(status().isOk())
                // 40101 而不是 404 / 500：账号"看起来存在"但凭据不匹配
                .andExpect(jsonPath("$.code").value(40101));
    }

    /* ==================== 改号走的是同一套判重 ==================== */

    @Test
    @DisplayName("改号时同一套规则：撞上别人已经占着的邮箱是 40902（create 与 update 不许分叉）")
    void shouldApplyTheSameRuleOnUpdate() throws Exception {
        String token = loginAsAdmin();
        String taken = uniqueUsername() + "@example.com";
        createUser(token, uniqueUsername(), taken, null);

        String username = uniqueUsername();
        long id = createUser(token, username, uniqueUsername() + "@example.com", null);

        // 只改邮箱，撞上第一个人占着的那个值
        mockMvc.perform(put("/api/auth/users/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"文本相等测试用户","email":"%s","password":""}
                                """.formatted(username, taken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value(containsString("邮箱")));
    }

    /* ==================== 夹具 ==================== */

    /**
     * 建号用的口令：**直接取种子账号那一个**，不在本文件里再刻一份字面量。
     *
     * <p>与 {@code UniqueCodeReuseApiTest} 同一理由：{@code application-h2.yml} 是口令的
     * 真值来源，种子口令满足默认策略，于是策略调紧时本片不会单独变红 ——
     * 它没有第二份需要跟着改的值。
     */
    private String seedPassword() {
        return properties.getSeedAdmin().getPassword();
    }

    private String seedTenantCode() {
        return properties.getSeedAdmin().getTenantCode();
    }

    private String loginAsAdmin() throws Exception {
        return login(seedTenantCode(), properties.getSeedAdmin().getUsername(), seedPassword());
    }

    private String login(String tenantCode, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(tenantCode, username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /**
     * 在当前登录者的租户里建一个号，返回主键。邮箱与手机号都可空 ——
     * 本片每次只想让**一个**维度参与判重，另一个留空免得两条断言互相掩盖。
     */
    private long createUser(String token, String username, String email, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username, email, phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /**
     * 断言这次建号被判重挡下。
     *
     * <p>{@code messagePart} 是必填的：三个码（40901 / 40902 / 40903）与兜底的 40900
     * 都是"HTTP 200 + 非零业务码"，只比数字会让"判重静默退化成兜底码"从缝里溜过去 ——
     * 而那正是这一族用例存在的理由（见 {@code UniqueCodeReuseApiTest} 的同一处教训）。
     */
    private void expectCreateRejected(String token, String username, String email, String phone,
                                      String expectedCode, String messagePart) throws Exception {
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username, email, phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Integer.parseInt(expectedCode)))
                .andExpect(jsonPath("$.message").value(containsString(messagePart)));
    }

    private String loginBody(String tenantCode, String username, String password) {
        return """
                {"tenantCode":"%s","username":"%s","password":"%s"}
                """.formatted(tenantCode, username, password);
    }

    /** 两个可空的维度分别序列化成 JSON null，避免"空字符串"与"没传"走成两条不同的路 */
    private String userBody(String username, String email, String phone) {
        return """
                {"username":"%s","displayName":"文本相等测试用户",\
                "email":%s,"phone":%s,"password":"%s"}
                """.formatted(username, jsonString(email), jsonString(phone), seedPassword());
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /* 登录名一律以字母开头：`Long.toString(n, 36)` 可能以数字开头，而登录名首字符要求是字母 ——
       直接用纳秒时间戳会偶尔被自己的 DTO 校验拦下，变成一条"大部分时候通过"的假测试 */

    private static String uniqueUsername() {
        return "e2eteu" + Long.toString(System.nanoTime(), 36);
    }

}
