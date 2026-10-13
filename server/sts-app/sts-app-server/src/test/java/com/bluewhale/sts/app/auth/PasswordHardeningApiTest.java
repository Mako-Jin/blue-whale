package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.LoginLockPolicy;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 口令强度、防重用与登录退避的端到端测试。
 *
 * <p><b>刻意不加 {@code @Transactional}。</b>这不是疏忽，是这些用例成立的前提：
 * 它们验证的恰恰是"失败请求回滚之后，什么仍然留下来了"。
 * 测试事务会把请求包进同一个事务里，未提交的写在同一事务内可见 ——
 * 于是"失败计数根本没写进库"这种缺陷会被完全掩盖，
 * 测试一路绿灯，而线上锁定永远不触发。见 {@code LoginAttemptRecorder#recordFailure}。
 *
 * <p>代价是这些用例会真的写库（h2 内存库，随测试进程消失）。
 * 因此用户名一律带时间戳后缀，避免同一次构建里重复执行时撞名。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordHardeningApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（`docs/decisions/0029` 的后续约束） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private UserMapper userMapper;

    /**
     * 时长期望从策略读取而不是写死分钟数：退避算法本身的边界（翻倍、封顶、溢出）
     * 已由 {@code LoginLockPolicyTest} 用固定配置逐条钉住；这里要验证的是**接线** ——
     * 锁定时长确实按"第几次被锁"取，而不是每次都取同一个值。
     */
    @Autowired
    private LoginLockPolicy lockPolicy;

    @Test
    @DisplayName("建号时弱口令返回 40003，且消息里指明缺了哪几项")
    void shouldRejectWeakPasswordOnCreate() throws Exception {
        String token = loginAsAdmin();

        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"abc"}
                                """.formatted(uniqueName("weak"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003))
                .andReturn();

        // 断言"报了什么"，因为它决定用户能不能一次改对：只回一句"口令不合规"，
        // 用户就得反复试；逐条列出缺失项才是可用性的一部分
        String message = JsonPath.read(result.getResponse().getContentAsString(), "$.message");
        assertTrue(message.contains("长度至少 8 位"), message);
        assertTrue(message.contains("至少一个大写字母"), message);
        assertTrue(message.contains("至少一个数字"), message);
    }

    @Test
    @DisplayName("长度下限只由口令策略表达：DTO 不再重复判定，避免同一个规则两种错误码")
    void shouldExpressMinLengthOnlyInPolicy() throws Exception {
        String token = loginAsAdmin();

        // "Abcd123" 恰好 7 位，大小写与数字都齐 —— 唯一的问题是短。
        // 若 UserSaveRequest 上还留着 @Size(min = 8)，这条会得到通用参数错误，
        // 而不是策略的 40003：同一个规则两处判定、两种码，前端只能猜。
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Abcd123"}
                                """.formatted(uniqueName("short"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003))
                .andExpect(jsonPath("$.message").value(containsString("长度至少 8 位")));
    }

    @Test
    @DisplayName("防重用：与当前口令、或与历史口令重复，一律 40914")
    void shouldRejectReusedPassword() throws Exception {
        String token = loginAsAdmin();
        String username = uniqueName("reuse");
        long userId = createUser(token, username, "First@1234");

        // 与"当前口令"相同 —— 当前口令不在历史表里，必须单独比对，
        // 漏掉这一步的后果就是"重置成原口令"会静默成功
        resetPassword(token, userId, "First@1234")
                .andExpect(jsonPath("$.code").value(40914));

        // 换成一个没用过的 → 成功，旧口令进历史
        resetPassword(token, userId, "Second@1234")
                .andExpect(jsonPath("$.code").value(0));

        // 再改回最开始那个 → 它已在历史里
        resetPassword(token, userId, "First@1234")
                .andExpect(jsonPath("$.code").value(40914));

        // 第三个没用过的 → 通过。证明拒的不是"重置"这个动作，而是那个口令
        resetPassword(token, userId, "Third@1234")
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("连续失败达到阈值即锁定，层级从 1 起、时长为基数")
    void shouldLockAccountAfterRepeatedFailures() throws Exception {
        String username = uniqueName("lock");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Lock@12345");

        for (int i = 0; i < 5; i++) {
            login("default", username, "Wrong@12345")
                    .andExpect(jsonPath("$.code").value(40101));
        }

        // 这一组断言是本类**不加 @Transactional** 的全部理由：
        // 失败请求抛的是 RuntimeException，登录方法上的事务会回滚。
        // 若失败计数写在同一个事务里，下面这些都是 null —— 而代码看上去毫无问题。
        UserEntity locked = findUser(username);
        assertEquals(1, locked.getLockLevel(), "第一次锁定的层级应为 1");
        assertNotNull(locked.getLockedUntil(), "锁定时间必须落库，否则锁定只是内存里的幻觉");
        assertNotNull(locked.getLockReason());

        long remaining = locked.getLockedUntil() - System.currentTimeMillis();
        long expected = lockPolicy.lockDurationOf(1).toMillis();
        assertTrue(remaining > expected * 9 / 10 && remaining <= expected,
                "第 1 次锁定应约为 " + expected + " ms（基数），实际剩余 " + remaining + " ms");

        // 口令正确也进不去 —— 锁定先于发令牌
        login("default", username, "Lock@12345")
                .andExpect(jsonPath("$.code").value(40102));

        // 状态还必须**从接口看得见**：只落库不暴露的话，运维看到"锁到明天"
        // 却既不知道原因、也不知道是第几次锁定 —— 也就无从判断退避到底在不在生效。
        // 这一条是"读库断言"抓不到的：数据库里明明是对的，接口却没有这个字段。
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockLevel").value(1))
                .andExpect(jsonPath("$.data.lockedUntil").isNotEmpty())
                .andExpect(jsonPath("$.data.lockReason").value(containsString("连续登录失败")));
    }

    @Test
    @DisplayName("退避：第二次被锁定的时长按退避增长，而不是取同一个值")
    void shouldApplyBackoffOnSecondLock() throws Exception {
        String username = uniqueName("backoff");
        long userId = createUser(loginAsAdmin(), username, "Backoff@1234");

        // 预置"曾经被锁过一次、且锁定期已过"的状态。
        // 直接改库而不是真的等上 5 分钟 —— 退避的边界已经在 LoginLockPolicyTest 里逐条钉过，
        // 这里要验证的是**接线**：registerFailure 是否真的按层级取时长。
        UserEntity seeded = userMapper.selectById(userId);
        seeded.setLockLevel(1);
        seeded.setLockedUntil(System.currentTimeMillis() - 1_000);
        userMapper.updateById(seeded);

        for (int i = 0; i < 5; i++) {
            login("default", username, "Wrong@12345")
                    .andExpect(jsonPath("$.code").value(40101));
        }

        UserEntity locked = findUser(username);
        assertEquals(2, locked.getLockLevel());

        long remaining = locked.getLockedUntil() - System.currentTimeMillis();
        long expected = lockPolicy.lockDurationOf(2).toMillis();
        // 两次的期望值必须不同，否则这条用例在"退避被改回固定时长"时会静默失效
        assertTrue(expected > lockPolicy.lockDurationOf(1).toMillis(),
                "第 2 次锁定时长应严格大于第 1 次");
        assertTrue(remaining > expected * 9 / 10 && remaining <= expected,
                "第 2 次锁定应约为 " + expected + " ms，实际剩余 " + remaining + " ms");
    }

    // ==================== 辅助 ====================

    private ResultActions login(String tenantCode, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(tenantCode, username, password)))
                .andExpect(status().isOk());
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
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

    private ResultActions resetPassword(String token, long userId, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/users/" + userId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(password)))
                .andExpect(status().isOk());
    }

    private UserEntity findUser(String username) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username));
        assertNotNull(user, "用户应存在：" + username);
        return user;
    }

    /** 用户名要满足后端的 {@code ^[a-zA-Z][a-zA-Z0-9_.-]{1,63}$}，且同一次构建里不重名 */
    private static String uniqueName(String prefix) {
        return prefix + "_" + Long.toString(System.nanoTime(), 36);
    }

}
