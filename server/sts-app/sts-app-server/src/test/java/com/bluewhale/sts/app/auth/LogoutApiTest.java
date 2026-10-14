package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authentication.RefreshTokenEntity;
import com.bluewhale.sts.auth.authentication.RefreshTokenMapper;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登出的两条语义 —— 撤**这一个**会话（{@code /api/auth/logout}）与撤**全部**
 * （{@code /api/auth/logout-all}）。
 *
 * <p><b>本类补的是一段空白：{@code /api/auth/logout} 此前一条用例都没有。</b>
 * 它不是一个边缘接口 —— 它是全仓唯一一个"两条语义共用一条路径、靠请求体在不在区分"
 * 的地方，而 {@code if (request != null && request.getRefreshToken() != null ...)}
 * 这个形状本身就说明写它的人在防"不传"。没有用例，那段分支就一直只是读起来没问题的散文：
 * 没有人知道发一个空 body 会把该账号所有设备踢下线，也没有人知道"我只想退出**这一台**"
 * 这条最普通的意图有没有被正确实现。
 *
 * <p><b>拆分的决策与代价见 {@code docs/decisions/0047}。</b>本类要钉三件事，缺一不可：
 * <ol>
 *   <li><b>原本就对的那一半没被改坏</b> —— 单会话登出只撤它自己（下面第 1 条）；
 *       它撤完之后别的会话照常续期，因为"登出"这件事的全部意思就是"这一台不干了"。</li>
 *   <li><b>"全部"现在有了名字</b>（{@code /logout-all}），且它撤的是真的全部。</li>
 *   <li><b>原来那条"靠省略得到全部"的路被堵上了</b>，包括它最隐蔽的一面：
 *       未认证时它**什么都不做却返回成功**。这条最值得钉 —— 一个"说做了、其实没做"的
 *       回复不会报错、不会告警，只会在真正需要它的时候（用户怀疑账号被人登着）
 *       让人以为自己已经自救过了。</li>
 * </ol>
 *
 * <p><b>刻意不加 {@code @Transactional}：</b>这里断言的核心是"库里的令牌行到底改没改"。
 * 包进测试事务之后，未提交的写在同事务内可见，而"其实没落库"或"撤错了行"都会被掩盖 ——
 * 与 {@code ChangePasswordApiTest} / {@code PasswordExpiryApiTest} 同一个理由。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令跟着配置走，不在测试里抄一份（见 {@code docs/decisions/0029}） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Test
    @DisplayName("登出只撤这一台：另一次登录的刷新令牌照常续期")
    void shouldRevokeOnlyTheNamedSession() throws Exception {
        String username = uniqueName("logoutone");
        createUser(adminToken(), username);
        // 同一账号登两次 —— 两台设备。第二台手上那个令牌是本用例的对照组：
        // 若登出撤的是"该用户全部"，它会跟着一起失效，而症状在用户那里是
        // "我在办公室点了个登出，家里的那台也被踢了"。
        Session first = signIn(username);
        Session second = signIn(username);

        logoutWithToken(first.refreshToken()).andExpect(jsonPath("$.code").value(0));

        // 点名的那一个作废（40106），没点名的那一个照常换出新令牌
        refresh(first.refreshToken()).andExpect(jsonPath("$.code").value(40106));
        refresh(second.refreshToken()).andExpect(jsonPath("$.code").value(0));

        // 库里也只剩一个活着的 —— 上面那句 code 0 只说明"能换"，这一句说明"只改了该改的那一行"
        assertEquals(1L, aliveRefreshTokens(first.userId()), "登出只该作废被点名的那一个");
    }

    @Test
    @DisplayName("登出是幂等的：重复提交、令牌无效，都返回成功且不推进撤销时刻")
    void shouldBeIdempotent() throws Exception {
        String username = uniqueName("logoutidem");
        createUser(adminToken(), username);
        Session session = signIn(username);

        logoutWithToken(session.refreshToken()).andExpect(jsonPath("$.code").value(0));
        Long revokedAt = revokedAtOf(session.userId());
        assertNotNull(revokedAt, "登出应当落下作废时刻");

        // 隔开一点时间，让"改写"这件事有机会在毫秒上显形 ——
        // 不 sleep 的话两次调用可能落在同一毫秒里，即使真的改写了那条断言也会通过，
        // 于是这条用例就变成一句不会红的空话。
        Thread.sleep(10);
        logoutWithToken(session.refreshToken()).andExpect(jsonPath("$.code").value(0));
        assertEquals(revokedAt, revokedAtOf(session.userId()), "重复登出不该把撤销时刻推后");

        // 令牌本身就是垃圾时，登出仍然成功：意图（"这台设备下线"）在令牌已失效时
        // 同样已经达成。回错误只会让每个客户端写一段"哪种失败算成功"的分支 ——
        // 而那段判断这里已经做过一次了。
        logoutWithToken("not-a-real-token").andExpect(jsonPath("$.code").value(0));
        assertEquals(revokedAt, revokedAtOf(session.userId()), "无效令牌的登出不该动任何行");
    }

    @Test
    @DisplayName("不带刷新令牌的登出不再等于\"退出所有设备\"：它被拒绝，且一个会话都不撤")
    void shouldRejectBodylessLogout() throws Exception {
        String username = uniqueName("logoutnobody");
        createUser(adminToken(), username);
        Session first = signIn(username);
        Session second = signIn(username);

        // 完全不带请求体 —— 从前这条路是"撤销该用户全部刷新令牌"，于是**任何**
        // 把请求体序列化错的客户端都会静默地把用户从所有设备踢下线（见 0047 决策 1）。
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        // 带了请求体、但里面没有令牌 —— 由 @NotBlank 拦下，同样是参数错误
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        // 本用例的重点：两次被拒的请求**一个会话都没动**。
        // 只断言"返回了 40000"是不够的 —— 一个"先撤了再校验"的实现同样会返回 40000。
        assertEquals(2L, aliveRefreshTokens(first.userId()), "被拒的登出不该撤销任何会话");
        refresh(first.refreshToken()).andExpect(jsonPath("$.code").value(0));
        refresh(second.refreshToken()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("退出所有设备：两个会话一起失效，且不需要请求体")
    void shouldRevokeEverySession() throws Exception {
        String username = uniqueName("logoutall");
        createUser(adminToken(), username);
        Session first = signIn(username);
        Session second = signIn(username);

        logoutAll(first.accessToken()).andExpect(jsonPath("$.code").value(0));

        refresh(first.refreshToken()).andExpect(jsonPath("$.code").value(40106));
        refresh(second.refreshToken()).andExpect(jsonPath("$.code").value(40106));
        assertEquals(0L, aliveRefreshTokens(first.userId()), "退出所有设备应当撤掉全部刷新令牌");

        // 访问令牌**仍在有效期内**（默认 2 小时），这是无状态访问令牌的固有残留
        // （同 logout / changePassword）。钉住它不是认可它，而是把它写成可执行的事实：
        // 别的设备上正在打开的页面不会立刻掉线，它会在下一次刷新时被送回登录页。
        // 谁要是以为"退出所有设备"立刻生效，这条断言会告诉他边界在哪。
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("未认证时不能\"退出所有设备\"：拒绝，而不是什么都不做却返回成功")
    void shouldRejectAnonymousLogoutAll() throws Exception {
        String username = uniqueName("logoutanon");
        createUser(adminToken(), username);
        Session session = signIn(username);

        // 拆开之前，这个形状是"拿不到当前用户就跳过撤销、照样返回 ok" ——
        // 一句"说做了、其实没做"的回复。它在最需要它的时候最坏：用户怀疑账号被人登着，
        // 点了退出所有设备，看到成功提示，而别人的会话还好端端地开着。
        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40104));

        assertEquals(1L, aliveRefreshTokens(session.userId()), "被拒的退出所有设备不该撤销任何会话");
    }

    // ==================== 辅助 ====================

    /** 一次登录的结果。把 {@code userId} 一起带回来，避免为了查主键再登一次（那会多造一个令牌） */
    private record Session(String accessToken, String refreshToken, long userId) {
    }

    /** 该用户手上还有几个未作废的刷新令牌 */
    private long aliveRefreshTokens(long userId) {
        return refreshTokenMapper.selectCount(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 0));
    }

    /** 该用户已作废令牌的撤销时刻（单会话场景下只可能有一条） */
    private Long revokedAtOf(long userId) {
        RefreshTokenEntity revoked = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 1));
        return revoked == null ? null : revoked.getRevokedAt();
    }

    private ResultActions logoutWithToken(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    private ResultActions logoutAll(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(properties.getSeedAdmin().getTenantCode(), username, password)))
                .andExpect(status().isOk());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    private String adminToken() throws Exception {
        String body = login(properties.getSeedAdmin().getUsername(), properties.getSeedAdmin().getPassword())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    /** 由管理员建号。用户名每次唯一，因此不必担心与其它用例撞号 */
    private void createUser(String adminToken, String username) throws Exception {
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, "Seed@1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 登录一次。
     *
     * <p>与 {@link #createUser} 分开：同一账号要登两次（模拟两台设备）是本类多处要摆的
     * 场景，而建号只能建一次 —— 混在一个方法里，第二次调用会撞 {@code 40901 用户名已存在}，
     * 而那个失败看起来像"设备数不对"，与真实原因差得很远。
     */
    private Session signIn(String username) throws Exception {
        String body = login(username, "Seed@1234")
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return new Session(
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken"),
                ((Number) JsonPath.read(body, "$.data.userId")).longValue());
    }

    /** 用户名要满足后端的 {@code ^[a-zA-Z][a-zA-Z0-9_.-]{1,63}$}，且同一次构建里不重名 */
    private static String uniqueName(String prefix) {
        return prefix + "_" + Long.toString(System.nanoTime(), 36);
    }

}
