package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authentication.RefreshTokenEntity;
import com.bluewhale.sts.auth.authentication.RefreshTokenMapper;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 口令有效期**启用时**的端到端测试 —— 走通一条此前不可能发生的路。
 *
 * <p>本类把 {@code bluewhale.auth.password.expire-days} 打开，因为不打开就**什么都测不到**：
 * 判断入口先看策略（{@code PasswordPolicy#isExpired}），策略关着时谁都不会过期，
 * 无论那一列里放着什么。这本身就是一条要钉住的语义，它由
 * {@code PasswordExpiryDisabledTest} 负责。
 *
 * <p><b>它盯的是一个曾经不存在的东西。</b>在 ADR-0045 之前，{@code password_expires_at}
 * 是"有一个读取方、零个写入方"：{@code AuthService#login} 里那句检查的条件**恒为假**
 * （{@code setPasswordExpiresAt} 全仓零命中），于是 {@code 40111} 是一条不可能出现的码。
 * 它看起来完全在工作 —— 有 {@code if}、有文案、有数据库列、有文档描述 ——
 * 只是那个分支一次也不会走到。这与 {@code 0020} 的"失败计数被自己的异常回滚"、
 * {@code 0043} 的"{@code must_change_password} 零逻辑引用"是同一类缺陷，
 * 而这一种最难发现：**没有的东西看起来和有一模一样**。
 *
 * <p>所以本类问两件事，缺一不可：**这个时刻是谁写下来的**（没有写入方，下面全部无从谈起），
 * 以及**写下来之后那条路走得通吗**（登录被拒、续期被拒、续期失败不留副作用、
 * 管理员重置之后回得来）。
 *
 * <p><b>刻意不加 {@code @Transactional}：</b>这里断言的核心是"库里的令牌行没被改过"
 * 与"到期时刻真的落了库"。包进测试事务之后，未提交的写在同一事务内可见，
 * "其实根本没写进库"会被完全掩盖 —— 与 {@code ChangePasswordApiTest} 同一个理由。
 */
@SpringBootTest(properties = "bluewhale.auth.password.expire-days=30")
@AutoConfigureMockMvc
class PasswordExpiryApiTest {

    /** 与配置里的 {@code expire-days} 保持一致；不一致时断言会红，一眼能看出改错了哪个 */
    private static final Duration TTL = Duration.ofDays(30);

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（见 {@code docs/decisions/0029}） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Test
    @DisplayName("到期时刻是谁写的：建号 / 管理员重置 / 本人改密，三个入口都落")
    void shouldStampExpiryOnEveryPasswordEntry() throws Exception {
        String username = uniqueName("stamped");
        String adminToken = loginAsAdmin();

        long beforeCreate = System.currentTimeMillis();
        long userId = createUser(adminToken, username, "Seed@1234");
        long afterCreate = System.currentTimeMillis();
        assertExpiresAround(userId, beforeCreate, afterCreate, "建号");

        long beforeReset = System.currentTimeMillis();
        resetPassword(adminToken, userId, "Reset@1234");
        long afterReset = System.currentTimeMillis();
        assertExpiresAround(userId, beforeReset, afterReset, "管理员重置");

        // 本人改密与管理员重置走的是同一个 PasswordChanger —— 这条也在钉住"唯一写入方"：
        // 换任何一个入口就该换一次寿命，而不是只有管理员那条路会刷新它。
        String token = loginAs(username, "Reset@1234");
        long beforeSelf = System.currentTimeMillis();
        change(token, "Reset@1234", "Final@1234").andExpect(jsonPath("$.code").value(0));
        long afterSelf = System.currentTimeMillis();
        assertExpiresAround(userId, beforeSelf, afterSelf, "本人改密");
    }

    @Test
    @DisplayName("登录**不**重算寿命：否则天天登录的人永远不会过期")
    void shouldNotExtendExpiryOnLogin() throws Exception {
        String username = uniqueName("noextend");
        long userId = createUser(loginAsAdmin(), username, "Seed@1234");
        Long stamped = userMapper.selectById(userId).getPasswordExpiresAt();
        assertNotNull(stamped, "策略启用后建号就该有到期时刻");

        loginAs(username, "Seed@1234");
        loginAs(username, "Seed@1234");

        // 登录时顺手把到期时刻推后是个很自然的"顺手"，但它把有效期悄悄换成了
        // "多久没登录"而不是"多久没换口令"—— 只要一直有人用，那条策略就永远不会触发。
        // 与"只在登录拦不够"（见 AuthService#refresh）是同一枚硬币的两面。
        assertEquals(stamped, userMapper.selectById(userId).getPasswordExpiresAt(),
                "登录只验口令，不动寿命");
    }

    @Test
    @DisplayName("口令过期后登录被拒：40111，且文案指向一条**存在的**路")
    void shouldRejectLoginWithExpiredPassword() throws Exception {
        String username = uniqueName("expired");
        long userId = createUser(loginAsAdmin(), username, "Seed@1234");
        expirePasswordOf(userId);

        login(username, "Seed@1234")
                .andExpect(jsonPath("$.code").value(40111))
                // 文案不能是“请修改口令”：走到这里的人登不进来、也没有会话可续，
                // 而改密接口（PUT /api/auth/password）恰恰要求会话。
                // 那句指示描述的是一个用户执行不了的动作，效果是让他对着界面找一个不存在的入口。
                // 存在的那条路是管理员重置口令 —— 它在 platform-admin 的用户页上，
                // 而且不是一句口号：下面那条用例会证明它真的能把人接回来。
                .andExpect(jsonPath("$.message").value("口令已过期，请联系管理员重置口令"));

        // 口令是对的，只是不再被接受 ⇒ 不该记一次失败，更不该把账号推向锁定。
        // 记了的话，“口令过期”会在几次尝试后升级成“账号已被锁定”，
        // 而那时连管理员重置都还要额外解锁一次 —— 代价与原因完全不成比例。
        assertEquals(0, userMapper.selectById(userId).getFailedAttempts(),
                "口令正确但已过期，不该计一次失败");
    }

    @Test
    @DisplayName("口令过期后续期也被拒：40111 —— 否则一直在用的人永远不必重新登录，过期永不生效")
    void shouldRejectRefreshWithExpiredPassword() throws Exception {
        String username = uniqueName("expiryrefresh");
        long userId = createUser(loginAsAdmin(), username, "Seed@1234");
        String[] session = loginWithRefresh(username, "Seed@1234");
        expirePasswordOf(userId);

        refresh(session[1])
                .andExpect(jsonPath("$.code").value(40111))
                .andExpect(jsonPath("$.message").value("口令已过期，请联系管理员重置口令"));

        // 这一条是本用例的重点：**失败的刷新不该留下副作用**。
        // 同一个刷新令牌再刷一次仍然是 40111，而不是 40106 —— 后者意味着
        // “这个令牌已作废”（被轮换掉、或被重放检测连带撤销了全部会话）。
        // 若那句检查排在“作废旧令牌”之后，一次失败就把令牌烧掉了：新令牌没发出来、
        // 旧的又不能再用，于是管理员重置口令之后这个人仍然回不来，只能从头登录。
        refresh(session[1]).andExpect(jsonPath("$.code").value(40111));
        assertEquals(1L, aliveRefreshTokens(userId), "失败的刷新不该动令牌");
    }

    @Test
    @DisplayName("管理员重置之后那条令牌能自己长回来：40111 的文案指向的路是真的")
    void shouldSelfHealAfterAdminReset() throws Exception {
        String username = uniqueName("expiryheal");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Seed@1234");
        String[] session = loginWithRefresh(username, "Seed@1234");
        expirePasswordOf(userId);

        refresh(session[1]).andExpect(jsonPath("$.code").value(40111));

        // 管理员重置 —— 这是 40111 那句文案指向的动作，也必须是**真的**能走通的路
        long beforeReset = System.currentTimeMillis();
        resetPassword(adminToken, userId, "Reset@1234");
        long afterReset = System.currentTimeMillis();

        // 重置换掉口令时，同一处也把寿命重新起算了 ——
        // 所以"自愈"不只是会话活过来，而是这个人重新拥有一个有效期内的口令。
        assertExpiresAround(userId, beforeReset, afterReset, "管理员重置");

        // 因为淘汰只淘汰“还能不能换到新令牌”，手上这个刷新令牌从未被烧掉，自愈是自动的：
        // 客户端下一次刷新就回到线上，不必重新登录一次。
        refresh(session[1])
                .andExpect(jsonPath("$.code").value(0))
                // 而它带回来的这个 1，正是 0044 那条改密引导的触发条件 ——
                // 于是整条路闭合：口令过期 → 找管理员 → 拿临时口令 → 被引导改密 → 新口令新寿命。
                .andExpect(jsonPath("$.data.mustChangePassword").value(1));

        login(username, "Reset@1234")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mustChangePassword").value(1));
    }

    // ==================== 辅助 ====================

    /**
     * 让这个账号的口令“已经过期”。
     *
     * <p>只有这一种办法：既不能靠等（那是 30 天），也不能靠配置（策略是全局的，
     * 改它会把同类里所有用例一起拖过去）。写的是那一列本身 —— 与
     * {@code PasswordChanger} 落它时是同一列，因此测的是真实形态而不是替身。
     */
    private void expirePasswordOf(long userId) {
        UserEntity user = userMapper.selectById(userId);
        user.setPasswordExpiresAt(System.currentTimeMillis() - 1_000);
        userMapper.updateById(user);
    }

    /**
     * 到期时刻应当落在 {@code [before + TTL, after + TTL]} 之间。
     *
     * <p>区间而不是等值：写入用的是服务端自己的 {@code now}，落在两次取时之间；
     * 掐等值只能得到一条会偶发变红的用例 —— 而偶发变红的用例最终会被跳过。
     */
    private void assertExpiresAround(long userId, long before, long after, String entry) {
        Long expiresAt = userMapper.selectById(userId).getPasswordExpiresAt();
        assertNotNull(expiresAt, entry + "应当落下到期时刻");
        assertTrue(expiresAt >= before + TTL.toMillis() && expiresAt <= after + TTL.toMillis(),
                entry + "落下的到期时刻应当约为 now + " + TTL + "，实际：" + expiresAt
                        + "，区间：[" + (before + TTL.toMillis()) + ", " + (after + TTL.toMillis()) + "]");
    }

    /** 该用户手上还有几个未作废的刷新令牌 */
    private long aliveRefreshTokens(long userId) {
        return refreshTokenMapper.selectCount(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 0));
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

    private String loginAs(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private String loginAsAdmin() throws Exception {
        return loginAs(properties.getSeedAdmin().getUsername(), properties.getSeedAdmin().getPassword());
    }

    /** 返回 [访问令牌, 刷新令牌] —— “失败的刷新不该烧掉令牌”那一条需要后者 */
    private String[] loginWithRefresh(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return new String[]{
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken")
        };
    }

    private ResultActions change(String token, String oldPassword, String newPassword) throws Exception {
        return mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(oldPassword, newPassword)))
                .andExpect(status().isOk());
    }

    private void resetPassword(String adminToken, long userId, String password) throws Exception {
        mockMvc.perform(post("/api/auth/users/" + userId + "/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createUser(String token, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 用户名要满足后端的 {@code ^[a-zA-Z][a-zA-Z0-9_.-]{1,63}$}，且同一次构建里不重名 */
    private static String uniqueName(String prefix) {
        return prefix + "_" + Long.toString(System.nanoTime(), 36);
    }

}
