package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authentication.RefreshTokenEntity;
import com.bluewhale.sts.auth.authentication.RefreshTokenMapper;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.enums.Status;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「停用租户」这件事的**效力**与它的**自锁边界**（ADR-0058）。
 *
 * <p><b>起点是 {@code 0051} 未覆盖 1 的两句话，而它们此前只能连着一起答。</b>
 * 那里实测"停用租户对**已经在用的人**不生效"：重新登录被拒，但**续期照通**、
 * 访问令牌也照用。根因在 {@code AuthService#refresh} 的同一行上 ——
 * 用户判了状态、租户只判了"非 {@code null}"。于是"停用"只挡新登录，
 * 而刷新令牌是**轮换**的 ⇒ 只要一个会话在被使用，它就永远续得下去。
 *
 * <p><b>本轮不需要产品决定，因为判据仓里已经有了。</b>{@code docs/decisions/0046} 的
 * 「四」把这件事答过一遍 —— 它列的三种状态变化里就写着"**账号被停用**：续期时读得到
 * {@code status}，靠校验就够"，并给出那条可以被检查的性质：
 *
 * <pre>
 * 撤销是主动清理，只用于无法从当前状态推出来的变化。
 * "停用 / 锁定 / 删除"都是当前状态，读的时候就知道，因此靠判断、不靠清理。
 * </pre>
 *
 * 那条判据对**用户**状态是落地的（`refresh` 确实在拦，{@code SessionRevocationApiTest}
 * 钉着），对**租户**状态却漏在那一行之外 —— 不是"判据不同"，是同一条判据的
 * **第二个对象**没有落地。所以修法也只有一处：把租户状态并进那一行判定。
 *
 * <p><b>本片钉四件事：</b>
 * <ol>
 *   <li>停用之后**续期也被拒**（此前这条是通的 —— 本片的正题）；</li>
 *   <li>停用**不**撤销会话（令牌行原样留着），于是**重新启用之后同一个刷新令牌
 *       立刻又能用** —— "不必重新登录就回来"正是 {@code 0046} 拒绝顺手清理时给的收益；</li>
 *   <li>不可停用**自己所在**的租户（{@code 40921}）；</li>
 *   <li>不可删除**自己所在**的租户（同一个码）。</li>
 * </ol>
 *
 * <p><b>后两条是"恢复入口还在不在"这条问题的答案的另一半。</b>续期也判状态之后，
 * 停用自己所在租户 = 所有会话在访问令牌到期后失效、而重新登录被拒；
 * 恢复入口只剩"另一个租户里持有 {@code auth:tenant:update} 的账号"（本仓的形态
 * 就是种子租户的管理员），根租户自己则只剩改库 —— 与 {@code 40917} 拒绝停用系统内置角色的
 * 理由一字不差（"一次点击造成的后果需要连数据库才能撤销，这种操作不该由一个 HTTP 接口提供"）。
 *
 * <p><b>反例在正题里：</b>前两例停用的都是**别的**租户且期望成功（{@code code 0}）——
 * 守卫拦的是"自己所在的那一个"，不是"停用这个动作"。缺了它，一个"谁都别停"的实现
 * 也能让后两例通过。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code TenantReferenceApiTest} /
 * {@code SessionRevocationApiTest} 同一理由：本片问的是"库里真正留下了什么" ——
 * 令牌行有没有被作废、租户的 {@code status} 有没有真的被改。测试事务会把"其实没写进去"
 * 掩盖掉，而那正是"不靠清理"这条要看的证据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantDisableApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（见 {@code docs/decisions/0029}） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    /* ==================== 效力：停用也拦已有会话的续期 ==================== */

    @Test
    @DisplayName("停用租户：该租户下**已有会话的续期**也被拒（40106）—— 此前这条路照通")
    void shouldRejectRefreshAfterTenantDisabled() throws Exception {
        String adminToken = loginAsAdmin();
        String code = uniqueTenantCode();
        long tenantId = createTenant(adminToken, code);
        String username = uniqueUsername();
        long userId = createUser(adminToken, tenantId, username);
        String[] session = loginWithRefresh(code, username);

        // 对照：停用之前这条会话是可用的。这里用**访问令牌**验，不用刷新令牌 ——
        // 续期是轮换的，拿它做对照会把下面要用的那一个换成另一个
        me(session[0]).andExpect(jsonPath("$.code").value(0));

        setTenantStatus(adminToken, tenantId, code, Status.DISABLED.wireName())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 正题：续期那条路现在也读租户状态（此前 refresh 里用户判了状态、租户只判了非 null）
        refresh(session[1]).andExpect(jsonPath("$.code").value(40106));
        // 另一半早就成立、但此前没有守护的东西：停用对**还没进来的人**同样生效 ——
        // 口令是对的，拒的理由只是租户；刻意与"口令错误"同码，让"停用"与"不存在"
        // 在登录侧不可区分（见 AuthResultCode.TENANT_DISABLED 的注释）
        login(code, username).andExpect(jsonPath("$.code").value(40101));
        // 访问令牌在停用期间**仍然可用**：这是无状态过滤器的已知残留，边界是访问令牌的
        // 有效期（默认 2 小时）。这一条是**如实记下**而不是"顺手也挡掉" ——
        // 挡住它要把认证过滤器从"只做密码学校验"改成每个请求查一次库，
        // 那条取舍见 docs/decisions/0046 与 0045，代价与收益都不在本片能定的范围内。
        // 写在这里的作用是：谁要是真的去动过滤器，这一条会先红，他必须回头读这一段。
        me(session[0]).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("停用**不**撤销会话：靠判断不靠清理 —— 重新启用后同一个刷新令牌立刻又能用")
    void shouldKeepSessionsAndResumeAfterTenantReEnabled() throws Exception {
        String adminToken = loginAsAdmin();
        String code = uniqueTenantCode();
        long tenantId = createTenant(adminToken, code);
        String username = uniqueUsername();
        long userId = createUser(adminToken, tenantId, username);
        String[] session = loginWithRefresh(code, username);

        setTenantStatus(adminToken, tenantId, code, Status.DISABLED.wireName())
                .andExpect(jsonPath("$.code").value(0));

        // 令牌行**原样留着**（未作废）—— 与"账号被停用"（SessionRevocationApiTest）
        // 是同一条判据：撤销是"必须留下痕迹"的做法，只用于**无法从当前状态推出来**的变化
        // （换口令之后，旧令牌看起来完全正常，没有任何一列能说明它属于上一任口令）；
        // 而"租户被停用"是当前状态，每次续期读得到，所以靠判断不靠清理。
        refresh(session[1]).andExpect(jsonPath("$.code").value(40106));
        assertEquals(1L, aliveRefreshTokens(userId), "停用不该顺手清会话");

        // 被拒的请求不留副作用：那个刷新令牌**没有被烧掉**（0045 决策 3 的"作废排在全部校验之后"）。
        // 再拒一次并复查计数，是因为"第一次拒了、顺手把令牌烧了"与"根本没动"在上一条断言里同形
        refresh(session[1]).andExpect(jsonPath("$.code").value(40106));
        assertEquals(1L, aliveRefreshTokens(userId), "被拒的续期不该烧掉令牌");

        // 恢复入口：另一个租户里持有 auth:tenant:update 的账号（本仓的形态就是种子租户的管理员）。
        // 重新启用之后**不必重新登录** —— 这正是"不清理"换来的收益，也是 0046 拒绝在停用
        // 路径上顺手撤销会话时给的那条理由。自己所在的那个租户没有这条退路，所以那一格
        // 由下面的守卫直接挡住，而不是指望"反正还能恢复"
        setTenantStatus(adminToken, tenantId, code, Status.ENABLED.wireName())
                .andExpect(jsonPath("$.code").value(0));
        refresh(session[1]).andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 自锁边界：不可停用 / 删除自己所在的租户 ==================== */

    @Test
    @DisplayName("不可停用**自己所在**的租户（40921）—— 那会让恢复入口只剩改库")
    void shouldRejectDisablingOwnTenant() throws Exception {
        String adminToken = loginAsAdmin();
        long ownTenantId = currentTenantId(adminToken);
        String ownCode = JsonPath.read(tenantDetail(adminToken, ownTenantId), "$.data.tenantCode");
        String ownName = JsonPath.read(tenantDetail(adminToken, ownTenantId), "$.data.tenantName");

        // 编码与名称都照当前值发送：于是失败**只可能**来自这条守卫，
        // 名称判重（40904）与其它字段都不会被牵动 —— 一条守卫的用例要能被自己的原因解释
        setTenantStatus(adminToken, ownTenantId, ownCode, Status.DISABLED.wireName(), ownName)
                .andExpect(jsonPath("$.code").value(40921));

        // 断言"没生效"而不只是"回了这个码"：码对而状态也真被改了，是最坏的一种通过。
        // 下面这条同时证明守卫**在写之前**就挡住了（不是写完再报错）
        assertEquals(Status.ENABLED.wireName(),
                JsonPath.read(tenantDetail(adminToken, ownTenantId), "$.data.status"),
                "被拒的停用不该留下一丝痕迹");
        // 而且自己的会话照常能用 —— 若守卫没生效，上一行会先红，这里再钉一次"人没被关在门外"
        me(adminToken).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("不可删除**自己所在**的租户（40921）—— 与停用同一个码，后果同为只有改库能撤销")
    void shouldRejectDeletingOwnTenant() throws Exception {
        String adminToken = loginAsAdmin();
        long ownTenantId = currentTenantId(adminToken);

        mockMvc.perform(delete("/api/auth/tenants/" + ownTenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40921));

        // 删除是逻辑删除，所以"没被删掉"要问详情才看得见（列表与详情都带 deleted = 0）
        tenantDetail(adminToken, ownTenantId);
        me(adminToken).andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 辅助 ==================== */

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
        String body = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    /** 登录到**指定租户**（本片多数会话属于自建的那个租户，不是种子租户） */
    private ResultActions login(String tenantCode, String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tenantCode":"%s","username":"%s","password":"%s"}
                        """.formatted(tenantCode, username, seedPassword())))
                .andExpect(status().isOk());
    }

    /** 返回 [访问令牌, 刷新令牌] —— 本片要反复拿后者去续期 */
    private String[] loginWithRefresh(String tenantCode, String username) throws Exception {
        String body = login(tenantCode, username)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return new String[]{
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken")
        };
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    /** 用**访问令牌**访问一个受保护接口 —— 会话可用性的最直接证据 */
    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    /** 从 {@code /me} 取当前登录者的租户，而不是写死 1 —— 写死会在种子策略变化时静默指向别的身份 */
    private long currentTenantId(String token) throws Exception {
        MvcResult result = me(token).andExpect(jsonPath("$.code").value(0)).andReturn();
        Number tenantId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.tenantId");
        return tenantId.longValue();
    }

    /** 租户详情，返回响应体；顺带断言"这个租户查得到"（删除那条要看的就是它） */
    private String tenantDetail(String token, long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** 建一个顶级租户，返回主键 */
    private long createTenant(String token, String tenantCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"停用测试租户 %s"}
                                """.formatted(tenantCode, tenantCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 在指定租户里建一个号，返回主键。**必须带 {@code tenantId}** —— 本片要的正是"别的租户里的人" */
    private long createUser(String token, long tenantId, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":%d,"username":"%s","displayName":"停用测试用户","password":"%s"}
                                """.formatted(tenantId, username, seedPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private ResultActions setTenantStatus(String token, long id, String tenantCode, String status)
            throws Exception {
        return setTenantStatus(token, id, tenantCode, status, "停用测试租户 " + tenantCode);
    }

    /** 改租户状态。名称默认与 {@link #createTenant} 建出来的一致，免得撞名称判重（40904） */
    private ResultActions setTenantStatus(String token, long id, String tenantCode,
                                          String status, String tenantName) throws Exception {
        return mockMvc.perform(put("/api/auth/tenants/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","tenantName":"%s","status":"%s"}
                                """.formatted(tenantCode, tenantName, status)))
                .andExpect(status().isOk());
    }

    /** 该用户手上还有几个未作废的刷新令牌 */
    private long aliveRefreshTokens(long userId) {
        Long count = refreshTokenMapper.selectCount(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 0));
        return count == null ? 0L : count;
    }

    /* 编码一律以字母开头：`Long.toString(n, 36)` 可能以数字开头，而编码首字符要求是字母 ——
       直接用纳秒时间戳会偶尔被自己的 DTO 校验拦下，变成一条"大部分时候通过"的假测试 */

    private static String uniqueTenantCode() {
        return "e2etd" + Long.toString(System.nanoTime(), 36);
    }

    /** 用户名要满足后端的 {@code ^[a-zA-Z][a-zA-Z0-9_.-]{1,63}$}，且同一次构建里不重名 */
    private static String uniqueUsername() {
        return "td_" + Long.toString(System.nanoTime(), 36);
    }

}
