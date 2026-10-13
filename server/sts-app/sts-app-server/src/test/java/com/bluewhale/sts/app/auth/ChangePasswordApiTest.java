package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.audit.auditlog.AuditLogEntity;
import com.bluewhale.sts.audit.auditlog.AuditLogMapper;
import com.bluewhale.sts.audit.auditlog.AuditResult;
import com.bluewhale.sts.auth.common.AuthAuditActions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自助改密（{@code PUT /api/auth/password}）的端到端测试。
 *
 * <p>它验的是 {@code PasswordChanger} 之外那一层 —— **接线**：旧口令真的被验了、
 * 失败真的被计数了、会话真的被撤销了、{@code mustChangePassword} 真的被清掉了、
 * 审计真的记下来了。三件事本身由 {@code PasswordChangerTest} 单独钉住。
 *
 * <p><b>刻意不加 {@code @Transactional}：</b>与 {@code PasswordHardeningApiTest} 同源 ——
 * "失败计数必须活过事务回滚"这一条，加上测试事务就再也测不出来了
 * （未提交的写在同一事务内可见，缺陷被完全掩盖）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** 种子账号的口令**跟着配置走**，不在测试里抄一份（见 {@code docs/decisions/0029}） */
    @Autowired
    private AuthProperties properties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Test
    @DisplayName("当前口令不正确：40101，且失败次数真的落库（独立事务，不随回滚消失）")
    void shouldRejectWrongOldPasswordAndCountTheFailure() throws Exception {
        String username = uniqueName("oldwrong");
        long userId = createUser(loginAsAdmin(), username, "Seed@1234");
        String token = loginAs(username, "Seed@1234");

        change(token, "NotIt@1234", "Next@1234")
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("当前口令不正确"));

        // 改密接口是一个**新的口令猜测入口**：拿着令牌的人可以反复猜旧口令。
        // 若不计数，"改密接口能绕过登录锁定去猜口令"就成了一个现成的后门。
        // 这一条断言需要真实提交 —— 失败请求抛 RuntimeException 会回滚本方法的事务，
        // 计数只有跑在 REQUIRES_NEW 里才留得下来（与登录失败同一条路）。
        UserEntity afterFailure = userMapper.selectById(userId);
        assertEquals(1, afterFailure.getFailedAttempts(), "旧口令错一次必须计一次数");
        assertTrue(afterFailure.getPasswordHash().startsWith("PBKDF2-SHA256"),
                "口令不该被改动");
    }

    @Test
    @DisplayName("新口令不合规：40003，且旧口令仍然可用（一次输错不该变成一次登出）")
    void shouldRejectWeakNewPasswordAndKeepSessionAlive() throws Exception {
        String username = uniqueName("weaknew");
        createUser(loginAsAdmin(), username, "Seed@1234");
        String token = loginAs(username, "Seed@1234");

        change(token, "Seed@1234", "abc")
                .andExpect(jsonPath("$.code").value(40003));

        loginAs(username, "Seed@1234");
    }

    @Test
    @DisplayName("新口令与旧口令相同：40914")
    void shouldRejectReusingSamePassword() throws Exception {
        String username = uniqueName("samepwd");
        createUser(loginAsAdmin(), username, "Seed@1234");
        String token = loginAs(username, "Seed@1234");

        change(token, "Seed@1234", "Seed@1234")
                .andExpect(jsonPath("$.code").value(40914));
    }

    @Test
    @DisplayName("成功：旧口令作废、新口令可用，且全部刷新令牌被撤销（需重新登录）")
    void shouldChangePasswordAndRevokeAllSessions() throws Exception {
        String username = uniqueName("chgok");
        createUser(loginAsAdmin(), username, "Seed@1234");
        String[] session = loginWithRefresh(username, "Seed@1234");

        change(session[0], "Seed@1234", "Fresh@1234")
                .andExpect(jsonPath("$.code").value(0));

        // 旧口令必须失效、新口令必须可用 —— 否则"改密成功"只是一句响应体里的说法
        login(username, "Seed@1234").andExpect(jsonPath("$.code").value(40101));
        loginAs(username, "Fresh@1234");

        // 改密最常见的动机是"怀疑口令泄露了"。此时若留着别人已换到手的刷新令牌，
        // 改密就成了一次无害的仪式 —— 所以**全部**撤销，包括当前这个会话。
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(session[1])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40106));
    }

    @Test
    @DisplayName("改自己的口令 = 完成\"必须改密\"这件事本身：mustChangePassword 被清 0")
    void shouldClearMustChangePasswordFlag() throws Exception {
        String username = uniqueName("mustchg");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Seed@1234");

        // 管理员重置 → 口令变、并置 mustChangePassword = 1（"下次登录必须改密"）
        mockMvc.perform(post("/api/auth/users/" + userId + "/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Reset@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, userMapper.selectById(userId).getMustChangePassword(),
                "管理员重置应置强制改密标记");

        String token = loginAs(username, "Reset@1234");
        change(token, "Reset@1234", "Final@1234").andExpect(jsonPath("$.code").value(0));

        // 不清 0 的后果是：用户刚改完口令，下次登录又被要求改 —— 而他并没有别的可改
        assertEquals(0, userMapper.selectById(userId).getMustChangePassword(),
                "本人改密之后不该再被要求改密");
    }

    @Test
    @DisplayName("接口要认证：不带头即拒，且不给任何\"操作了谁\"的可能")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"Seed@1234","newPassword":"Fresh@1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40104));
    }

    @Test
    @DisplayName("改密要进审计：凭据变更是低频高重事件，一条记录能定住整条安全时间线")
    void shouldWriteAuditRecord() throws Exception {
        String username = uniqueName("chgaudit");
        String adminToken = loginAsAdmin();
        long userId = createUser(adminToken, username, "Seed@1234");
        String token = loginAs(username, "Seed@1234");

        change(token, "Seed@1234", "Audited@1234").andExpect(jsonPath("$.code").value(0));

        // 与"重置口令"分开记（auth.user.reset_password），因为事后追问的是两个问题：
        // "谁重置过这个账号"（管理员介入）与"这个账号的口令什么时候被本人改过"（安全时间线）。
        // 合并的话，后一个问题永远答不出来。
        AuditLogEntity record = auditLogMapper.selectOne(Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(AuditLogEntity::getAction, AuthAuditActions.USER_CHANGE_PASSWORD)
                .eq(AuditLogEntity::getTargetId, String.valueOf(userId))
                .orderByDesc(AuditLogEntity::getId)
                .last("LIMIT 1"));
        assertNotNull(record, "自助改密必须留下一条审计（action=" + AuthAuditActions.USER_CHANGE_PASSWORD + "）");
        assertEquals(AuditResult.SUCCESS.wireName(), record.getResult());
        assertEquals("auth:user", record.getResource());
        assertNotNull(record.getOperatorId());
    }

    // ==================== 辅助 ====================

    private ResultActions change(String token, String oldPassword, String newPassword) throws Exception {
        return mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(oldPassword, newPassword)))
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

    private String loginAs(String username, String password) throws Exception {
        return JsonPath.read(login(username, password)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }

    /** 返回 [访问令牌, 刷新令牌] —— 撤销会话那一条用例需要后者 */
    private String[] loginWithRefresh(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return new String[]{
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken")
        };
    }

    private String loginAsAdmin() throws Exception {
        return loginAs(properties.getSeedAdmin().getUsername(), properties.getSeedAdmin().getPassword());
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
