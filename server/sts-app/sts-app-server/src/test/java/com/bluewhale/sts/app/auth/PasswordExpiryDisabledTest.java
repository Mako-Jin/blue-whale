package com.bluewhale.sts.app.auth;

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
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 口令有效期**未启用**（默认配置）时的行为。
 *
 * <p>它单独成类，而不是与 {@code PasswordExpiryApiTest} 合成一个：那边的每条断言都要求策略打开，
 * 而这里问的恰恰是"策略关着的时候世界是什么样"—— 两种上下文，两个问题，
 * 混在一起只会得到一个靠 `if` 分叉的用例，读的人无法一眼看出哪条断言属于哪种配置。
 *
 * <p><b>这里钉着一条被实现方式逼出来的语义</b>（见 {@code docs/decisions/0045}）：
 * "关掉策略"**不靠清数据，靠判断先看策略**。理由不是省事 ——
 * {@code updateById} 默认跳过 null 字段，所以"把那一列写回 NULL"根本不会落库；
 * 而且即便落库，清残留也只覆盖"关掉之后又改过密的人"，其余人的旧时刻一直都在。
 * 先看策略，效果是**配置一改完立刻对所有人生效、不需要动一行数据**。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordExpiryDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("策略未启用时不写这一列：建号留 NULL，NULL 才是“永不过期”的唯一表示")
    void shouldNotStampExpiry() throws Exception {
        long userId = createUser(loginAsAdmin(), uniqueName("noexpiry"), "Seed@1234");

        assertNull(userMapper.selectById(userId).getPasswordExpiresAt(),
                "expire-days = 0 时不该有任何到期时刻");
    }

    @Test
    @DisplayName("策略一关，库里残留的到期时刻立刻不再生效 —— 登录与续期都照常")
    void shouldIgnoreStaleExpiryWhenPolicyDisabled() throws Exception {
        String username = uniqueName("stalestamp");
        long userId = createUser(loginAsAdmin(), username, "Seed@1234");
        String[] session = loginWithRefresh(username, "Seed@1234");

        // 造一份"策略被关掉之前落下的残留"：一个早就过去的到期时刻
        UserEntity user = userMapper.selectById(userId);
        user.setPasswordExpiresAt(System.currentTimeMillis() - 1_000);
        userMapper.updateById(user);

        // 换成"只看那一列"的写法，这两条都会变成 40111 —— 于是关掉策略这件事只对
        // "关掉之后又改过密的人"生效，其余人会在某个未来的时刻被挡在门外，
        // 而那是最难查的一种：“配置明明是关的，他却进不来”。
        login(username, "Seed@1234").andExpect(jsonPath("$.code").value(0));
        refresh(session[1]).andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 辅助 ====================

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

    private String loginAsAdmin() throws Exception {
        String body = login(properties.getSeedAdmin().getUsername(), properties.getSeedAdmin().getPassword())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private String[] loginWithRefresh(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return new String[]{
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken")
        };
    }

    private long createUser(String token, String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    /** 用户名要满足后端的 {@code ^[a-zA-Z][a-zA-Z0-9_.-]{1,63}$}，且同一次构建里不重名 */
    private static String uniqueName(String prefix) {
        return prefix + "_" + Long.toString(System.nanoTime(), 36);
    }

}
