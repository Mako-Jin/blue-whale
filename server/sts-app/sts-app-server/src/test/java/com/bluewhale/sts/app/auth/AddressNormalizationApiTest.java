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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「寻址列」的写入口与判重口必须说同一种写法 —— ADR-0071 把<b>过滤列</b>的写入口收进了
 * {@code FilterValue.text}，但 {@code bw_auth_user} 上那两列（{@code email} / {@code phone}）
 * 走的是<b>另一条路</b>：它们的读者不是 {@code eq} 过滤，而是 <b>判重</b>
 * （{@code existsEmail} / {@code existsPhone}）。判重同样是一次 {@code eq}
 * （见 {@code decisions/0062}「判重看语义不看索引」），所以它们落在<b>同一条判据</b>下、
 * 却住着<b>全仓最后一份"只判空、不 trim"的写入副本</b>（{@code UserService.blankToNull}，
 * 本轮删掉、改用 {@code FilterValue.text}）。
 *
 * <p><b>两列的命运并不相同 —— 这是本片的第一件事：</b>
 * <ul>
 *   <li>{@code phone} 上只有 {@code @Size(max = 32)}（量长度），于是
 *       {@code " 13800138000 "} <b>一路进到服务层</b>：写入原样入库、判重拿带空白的字面量去比。
 *       后果是一个真实的绕过 —— 库里已有 {@code 13800138000} 时，
 *       拿 {@code " 13800138000 "} 建号会<b>判重通过</b>（NO PAD 的排序规则下两者不相等，
 *       见 {@code decisions/0065}），于是同一个手机号可以有两个账号。</li>
 *   <li>{@code email} 上有 {@code @Email}，而它<b>不 trim</b>：
 *       带两端空白的邮箱在<b>进服务层之前</b>就被判成"格式不正确"（{@code 40000}）。
 *       所以这一列的写入侧本来就只会收到"干净的值或缺席"，改成归一是一次
 *       <b>等价变换</b> —— 有用例把这个前提钉住（否则"email 也归一了"只是说法）。</li>
 * </ul>
 *
 * <p>与 {@code TextEqualityApiTest} / {@code SearchMatchApiTest} / {@code SearchWildcardApiTest} /
 * {@code SearchKeywordTrimApiTest} / {@code FilterValueTrimApiTest} /
 * {@code EnumFilterNormalizationApiTest} / {@code WriteSideNormalizationApiTest} 同形
 * （本仓第八处）：<b>不 {@code @Transactional}、不按 profile 跳过</b>，
 * 同一批断言在 h2 与真实 MySQL 两个 job 上各跑一遍 ——
 * 本片问的正是"库里真正留下了什么、判重真正比到了什么"，测试事务会把这两件事都掩盖掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AddressNormalizationApiTest {

    /** 两端空白用同一个常量，免得某个用例悄悄用了别的字符（比如全角空格）而改变了结论 */
    private static final String PAD = "   ";

    /** 建号用的口令：本片只关心寻址列，口令照 {@code PasswordPolicy} 取一个必然合格的。 */
    private static final String PASSWORD = "Probe@12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    /* ==================== 一、写入侧：带空白进去，库里留下的必须是干净写法 ==================== */

    @Test
    @DisplayName("建号：phone 带两端空白，入库的必须是去掉空白的那一串")
    void shouldStoreNormalizedPhoneOnCreate() throws Exception {
        String token = loginAsAdmin();
        String phone = uniquePhone();

        long id = createUserOk(token, uniqueUsername(), PAD + phone + PAD, null);

        assertEquals(phone, stringOf(getUser(token, id), "$.data.phone"),
                "建号传 \"" + PAD + phone + PAD + "\" 之后，回显该是 \"" + phone + "\""
                        + "（回显带空白 = 写入侧没有归一，于是库里躺着一串谁也比不上的值）");
    }

    @Test
    @DisplayName("改号：update 传带两端空白的 phone，入库的同样必须是干净写法")
    void shouldStoreNormalizedPhoneOnUpdate() throws Exception {
        String token = loginAsAdmin();
        String username = uniqueUsername();
        String phone = uniquePhone();
        long id = createUserOk(token, username, phone, null);

        MvcResult updated = putUserAddress(token, id, username, PAD + phone + PAD, null)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        assertEquals(phone, stringOf(updated, "$.data.phone"),
                "改号传带空白的 phone 之后，回显该是干净的 —— create 与 update 不该有两种宽容度");
        assertEquals(phone, stringOf(getUser(token, id), "$.data.phone"), "落库的也该是干净写法");
    }

    /* ==================== 二、判重侧：带空白不该绕过"这个号已经有人用了" ==================== */

    @Test
    @DisplayName("判重：库里已有干净写法时，拿带空白的同一个号建号必须被拒（40903）")
    void shouldRejectPaddedPhoneAgainstCleanStoredPhone() throws Exception {
        String token = loginAsAdmin();
        String phone = uniquePhone();
        createUserOk(token, uniqueUsername(), phone, null);

        createUserRequest(token, uniqueUsername(), PAD + phone + PAD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value(containsString("手机号")));
        // 判重比的是字面量：库里是 13800138000、入参是 " 13800138000 "，在 NO PAD 的排序规则下
        // 两者不相等 ⇒ 判重通过 ⇒ 同一个手机号建出了两个账号。
    }

    @Test
    @DisplayName("判重：库里存的是带空白的写法时，拿干净的同一个号建号也必须被拒（40903）")
    void shouldRejectCleanPhoneAgainstPaddedStoredPhone() throws Exception {
        String token = loginAsAdmin();
        String phone = uniquePhone();
        createUserOk(token, uniqueUsername(), PAD + phone + PAD, null);

        createUserRequest(token, uniqueUsername(), phone, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40903));
        // 这一条把反方向也钉住：只修"判重入参"或只修"入库值"的任何一半，
        // 都会留下"存 A、比 B"的新不对称 —— 两侧必须共用同一个归一值。
    }

    @Test
    @DisplayName("判重：update 把自己的号改成【别人那个号的带空白写法】，必须被拒（40903）")
    void shouldRejectPaddedPhoneAgainstAnotherUserOnUpdate() throws Exception {
        String token = loginAsAdmin();
        String takenPhone = uniquePhone();
        createUserOk(token, uniqueUsername(), takenPhone, null);

        String username = uniqueUsername();
        long id = createUserOk(token, username, uniquePhone(), null);

        putUserAddress(token, id, username, PAD + takenPhone + PAD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40903));
        // excludeId 把自己排除掉是既有语义（改自己的号不该与自己冲突），
        // 本片要钉的是"排除自己之后仍然要能比中别人"—— 带空白会把这次比较整个跳过。
    }

    /* ==================== 三、边界钉：归一只去掉空白，不改别的语义 ==================== */

    @Test
    @DisplayName("回归钉：纯空白的 phone 照旧等于【没有手机号】（不是一条带空白的值）")
    void shouldStillTreatWhitespaceOnlyPhoneAsAbsent() throws Exception {
        String token = loginAsAdmin();

        long blank = createUserOk(token, uniqueUsername(), PAD, null);
        assertNull(stringOf(getUser(token, blank), "$.data.phone"),
                "纯空白该退化成 null —— 这是 FilterValue.text 与它替换掉的那个实现共有的语义，"
                        + "换实现不该把它弄丢");

        long empty = createUserOk(token, uniqueUsername(), "", null);
        assertNull(stringOf(getUser(token, empty), "$.data.phone"), "空串照旧等于没有");

        // 两条都不占"某个号"：第三条用空串建号仍然成功（若纯空白被当成一个真实值，
        // 第二条就会因为与第一条"撞号"而 40903）
        long absent = createUserOk(token, uniqueUsername(), null, null);
        assertNull(stringOf(getUser(token, absent), "$.data.phone"), "不传照旧等于没有");
    }

    @Test
    @DisplayName("回归钉：判重沿用 0065 的文本相等关系 —— 大小写不同仍是两个值")
    void shouldStillTreatCaseVariantAsDistinct() throws Exception {
        String token = loginAsAdmin();
        String email = "probe" + uniqueTag().toLowerCase() + "@bw.test";
        createUserOk(token, uniqueUsername(), null, email);

        // 全仓的文本相等由排序规则定义（0065 把三方言统一成"区分大小写 + 区分重音"），
        // 判重用的是同一个 eq、因此同一条关系。若哪天有人把 email 也归一成小写，
        // 这条会红 —— 那时该改的是 0065 的结论，不是这一行。
        createUserRequest(token, uniqueUsername(), null, email.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("前提钉：带两端空白的 email 在 DTO 层就被拒（40000）—— 所以那一列的写入侧只会收到干净值")
    void shouldStillRejectPaddedEmailAtDto() throws Exception {
        String token = loginAsAdmin();

        createUserRequest(token, uniqueUsername(), null, PAD + "probe@bw.test" + PAD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value(containsString("邮箱")));
        // @Email 不 trim：带空白的邮箱判成"格式不正确"，压根进不到 UserService。
        // 这条不是"顺带一测"，它是本片对 email 只做等价变换的**前提**——
        // 这个前提是可撤销的（比如有人把 @Email 换成更宽松的写法），所以必须有东西看着它。
    }

    /* ==================== 断言辅助 ==================== */

    private String stringOf(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    /* ==================== 请求夹具 ==================== */

    /** 建号（不关心结果形状时用），断言 {@code code == 0} 并返回主键。 */
    private long createUserOk(String token, String username, String phone, String email)
            throws Exception {
        MvcResult result = createUserRequest(token, username, phone, email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    /**
     * 建号请求本身。{@code phone} / {@code email} 传 {@code null} 表示<b>不带这个字段</b>
     * （不是带一个空值）—— 本片要区分"没填"与"填了空白"两种输入。
     */
    private ResultActions createUserRequest(String token, String username, String phone, String email)
            throws Exception {
        return mockMvc.perform(post("/api/auth/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":" + quote(username)
                        + ",\"password\":\"" + PASSWORD + "\""
                        + ",\"displayName\":\"寻址列探针\""
                        + ",\"phone\":" + quote(phone)
                        + ",\"email\":" + quote(email) + "}"));
    }

    /** 改号。{@code username} 必须带上 —— 它与 create 共用 DTO，而那里是 {@code @NotBlank}。 */
    private ResultActions putUserAddress(String token, long id, String username, String phone,
                                         String email) throws Exception {
        return mockMvc.perform(put("/api/auth/users/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":" + quote(username)
                        + ",\"displayName\":\"寻址列探针\""
                        + ",\"phone\":" + quote(phone)
                        + ",\"email\":" + quote(email) + "}"));
    }

    private MvcResult getUser(String token, long id) throws Exception {
        return mockMvc.perform(get("/api/auth/users/" + id)
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

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** 每次跑一个新的短标签，用它把断言限定在本次造的那一行上（只含字母与数字，首字符是字母） */
    private static String uniqueTag() {
        return "A" + Long.toString(System.nanoTime(), 36) + UUID.randomUUID().toString().substring(0, 4);
    }

    /** 用户名的字符集比编码窄：只允许字母开头 + 字母数字与 {@code _.-} */
    private static String uniqueUsername() {
        return "an_" + Long.toString(System.nanoTime(), 36);
    }

    /**
     * 手机号只要求"每次不同、且在 {@code @Size(max = 32)} 之内"。
     * 刻意写成 {@code 1} 开头的一串数字（像真的手机号），是因为本片要证明的正是
     * "值本身合法、只是带了两端空白" —— 值若明显不像手机号，结论就说不清了。
     */
    private static String uniquePhone() {
        return "1" + Long.toString(System.nanoTime()).substring(4);
    }

}
