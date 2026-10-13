package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authentication.RefreshTokenEntity;
import com.bluewhale.sts.auth.authentication.RefreshTokenMapper;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.enums.Status;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「什么时候该撤销会话、什么时候不该」的端到端测试（{@code docs/decisions/0046}）。
 *
 * <p><b>它盯的是一处效力不一致：</b>自助改密会撤销该账号的全部刷新令牌，
 * 而管理员重置**只改口令**（{@code UserService#resetPassword} 原本全文如此）。
 * 两个入口的动机是同一个（"管理员认为这个账号出了问题" / "我怀疑口令泄露了"），
 * 效力却差了一半 —— 一个已经换到手刷新令牌的攻击者，在管理员重置之后**仍然能续期**。
 * 修法不是给管理员那条路补一句撤销，而是把"换口令 ⇒ 清旧会话"移到换口令这个动作本身
 * （{@code PasswordChanger#change}），于是它不可能被下一个入口忘掉。
 *
 * <p><b>四例里有两例断言的是"没做"。</b>这不是凑数：本决策的另一半是**边界** ——
 * 账号被停用、被锁定都**不**撤销会话，因为它们靠每次校验读得到，不靠清理。
 * 只钉"该撤的撤了"，下一次有人"顺手也撤一下"就没有东西会红，
 * 而锁定那条一旦被顺手撤掉，代价是"猜错 5 次口令"升级成"远程把你踢下线"。
 *
 * <p><b>刻意不加 {@code @Transactional}：</b>本类断言的核心是"库里的令牌行有没有被改过"，
 * 包进测试事务之后未提交的写在同一事务内可见，"根本没改库"会被完全掩盖 ——
 * 与 {@code ChangePasswordApiTest} / {@code PasswordHardeningApiTest} 同一个理由。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionRevocationApiTest {

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
    @DisplayName("管理员重置口令：该账号**全部**会话立刻作废 —— 与自助改密同等效力")
    void shouldRevokeEverySessionWhenAdminResetsPassword() throws Exception {
        String username = uniqueName("reset_revoke");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Seed@1234");

        // 两个会话（两台设备），重置必须一台都不剩
        String[] phone = loginWithRefresh(username, "Seed@1234");
        String[] desktop = loginWithRefresh(username, "Seed@1234");
        assertEquals(2L, aliveRefreshTokens(userId), "两次登录应有两条未作废的刷新令牌");

        resetPassword(adminToken, userId, "Reset@1234").andExpect(jsonPath("$.code").value(0));

        assertEquals(0L, aliveRefreshTokens(userId), "重置后不该还有活着的会话");
        // 逐个断言而不是只看计数：计数为 0 也可能是"被删了"或"换了另一种作废方式"，
        // 而下面这条要求的正是**客户端拿它会被拒**（40106 = 会话已失效）。
        refresh(phone[1]).andExpect(jsonPath("$.code").value(40106));
        refresh(desktop[1]).andExpect(jsonPath("$.code").value(40106));

        // 新（临时）口令是唯一入口，且带着"下次登录必须改密"
        login(username, "Reset@1234")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mustChangePassword").value(1));
        login(username, "Seed@1234").andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("重置失败（新口令不合规）时，会话一个都不动 —— 撤销是那次变更的最后一步")
    void shouldNotRevokeSessionsWhenResetIsRejected() throws Exception {
        String username = uniqueName("reset_fail");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Seed@1234");
        String[] session = loginWithRefresh(username, "Seed@1234");

        resetPassword(adminToken, userId, "abc").andExpect(jsonPath("$.code").value(40003));

        // 撤销排在强度校验与防重用之后，因此拒绝的请求在会话上不留痕迹 ——
        // 与 refresh 里"作废旧令牌排在全部校验之后"是同一条原则
        // （一个失败的请求不该有副作用），只是换了个地方。
        assertEquals(1L, aliveRefreshTokens(userId), "失败的请求不该动令牌");
        refresh(session[1]).andExpect(jsonPath("$.code").value(0));
        loginAs(username, "Seed@1234");
    }

    @Test
    @DisplayName("账号被停用：**不**撤销会话，靠每次校验拦住 —— 状态读得到，不需要清理")
    void shouldNotRevokeSessionsWhenAccountDisabled() throws Exception {
        String username = uniqueName("disable_keep");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Seed@1234");
        String[] session = loginWithRefresh(username, "Seed@1234");

        disable(adminToken, userId, username).andExpect(jsonPath("$.code").value(0));

        // 令牌行**原样留着**（未作废）—— 这不是漏了，是本决策划的那条线：
        // 撤销是"必须留下痕迹"的做法，只用于**无法从当前状态推出来**的变化
        // （换口令之后，旧令牌看起来完全正常，没有任何一列能说明它属于上一任口令）；
        // 而"停用"是当前状态，续期时读得到，所以靠判断不靠清理。
        // 后者多一层好处：管理员把账号改回启用，那个人不必重新登录就回来了。
        assertEquals(1L, aliveRefreshTokens(userId), "停用不该顺手清会话");
        refresh(session[1]).andExpect(jsonPath("$.code").value(40106));
        login(username, "Seed@1234").andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    @DisplayName("账号被锁定：**不**撤销会话 —— 否则猜错 5 次口令就是一次远程踢人（不需任何凭据）")
    void shouldNotRevokeSessionsWhenAccountLocked() throws Exception {
        String username = uniqueName("lock_keep");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Lock@12345");
        String[] session = loginWithRefresh(username, "Lock@12345");

        // 用错误口令连试 5 次把它锁上 —— 这一步**任何人都能做到**，这正是下面的理由
        for (int i = 0; i < 5; i++) {
            login(username, "Wrong@12345").andExpect(jsonPath("$.code").value(40101));
        }
        UserEntity locked = userMapper.selectById(userId);
        assertNotNull(locked.getLockedUntil(), "账号应已锁定");
        assertEquals(1, locked.getLockLevel());

        // 锁定拦的是**新的登录尝试**，而它已经拦住了（口令正确也进不来，见
        // PasswordHardeningApiTest）。它不该顺手清会话：触发条件是"有人猜错了几次口令"，
        // 而这件事不需要任何凭据就能由第三方制造 —— 若锁定同时清会话，
        // 一次 5 次的错误猜测就升级成"把正在工作的人踢下线"，成了一个免费的 DoS。
        assertEquals(1L, aliveRefreshTokens(userId), "锁定不该顺手清会话");
        refresh(session[1]).andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 辅助 ====================

    /** 该用户手上还有几个未作废的刷新令牌 */
    private long aliveRefreshTokens(long userId) {
        Long count = refreshTokenMapper.selectCount(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 0));
        return count == null ? 0L : count;
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

    /** 返回 [访问令牌, 刷新令牌] —— "重置后旧会话必须被拒"那一条需要后者 */
    private String[] loginWithRefresh(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return new String[]{
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken")
        };
    }

    private ResultActions resetPassword(String adminToken, long userId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/users/" + userId + "/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(password)))
                .andExpect(status().isOk());
    }

    private ResultActions disable(String adminToken, long userId, String username) throws Exception {
        return mockMvc.perform(put("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","status":"%s"}
                                """.formatted(username, Status.DISABLED.wireName())))
                .andExpect(status().isOk());
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
