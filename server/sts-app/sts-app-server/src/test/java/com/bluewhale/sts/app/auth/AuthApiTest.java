package com.bluewhale.sts.app.auth;

import com.bluewhale.sts.auth.common.AuthPermissions;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限域的端到端测试 —— 一条请求真实穿过
 * Token 过滤器 → 鉴权拦截器 → Controller → Service → Mapper → H2。
 *
 * <p>放在**应用层**，因为它验证的是"整条链路装配正确"：自动配置是否生效、
 * Mapper 是否被扫描到、Flyway V3 是否建出了表、审计字段是否被自动填充、
 * 统一响应与错误码是否正确翻译。这些单元测试都覆盖不到。
 *
 * <p>账号来自 {@code DefaultAdminSeeder}（h2 profile 下开启的种子数据），
 * 因此这里能直接以 {@code default / admin} 登录。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("登录成功：返回访问令牌、刷新令牌，并带上种子授权计算出的权限")
    void shouldLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"default","username":"admin","password":"Admin@12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.tenantCode").value("default"))
                // 种子数据把 AuthPermissions.ALL 全部授予了 SUPER_ADMIN 角色，再由角色授予 admin —
                // 这条断言实际验证的是"用户 → 角色 → 资源"整条 RBAC 链路取数正确。
                //
                // 期望值**从常量推导**而不是写死数字：写死时每加一个权限码都要回来改一次，
                // 而漏改的症状是"测试失败但代码没问题"，容易被顺手改成新的数字了事
                // （`docs/decisions/0021` 记了这个漂移问题）。
                .andExpect(jsonPath("$.data.permissions.length()").value(AuthPermissions.ALL.size()))
                // 出参绝不包含任何口令字段
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("口令错误只回 40101，且不透露账号是否存在")
    void shouldNotRevealWhetherAccountExists() throws Exception {
        // 账号存在但口令错
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"default","username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));

        // 账号根本不存在 —— 必须回同一个码，否则这就是一个账号枚举接口
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"default","username":"no-such-user","password":"whatever"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口返回 40100")
    void shouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("令牌不可用时按未认证处理，而不是 500")
    void shouldTreatBadTokenAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/tenants").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("带令牌可访问受保护接口：/me 回当前用户，租户列表可查")
    void shouldAccessProtectedEndpointsWithToken() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.permissions").isNotEmpty());

        mockMvc.perform(get("/api/auth/tenants").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("已认证但无权限返回 40301 —— 401 与 403 必须分开")
    void shouldDistinguishForbiddenFromUnauthenticated() throws Exception {
        String adminToken = loginAsAdmin();

        // 用管理员建一个"还没有任何角色"的用户
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"Nobody@12345","displayName":"无权限用户"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String nobodyToken = login("default", "nobody", "Nobody@12345");

        // 认证通过（否则会是 40100），但没有 auth:tenant:read 权限 → 40301
        mockMvc.perform(get("/api/auth/tenants").header("Authorization", "Bearer " + nobodyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        // 但他仍能查自己的信息 —— /me 只要求已认证，不要求权限
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nobodyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("nobody"))
                .andExpect(jsonPath("$.data.permissions.length()").value(0));
    }

    @Test
    @DisplayName("只读身份：列表读得到、写操作 403 —— 按钮级权限演示的后端依据")
    void shouldLetAuditorReadButNotWrite() throws Exception {
        String auditorToken = login("default", "auditor", "Auditor@12345");

        // 读得到：列表接口放行。前端据此才敢把"新建"按钮之外的内容渲染出来
        mockMvc.perform(get("/api/auth/users").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());

        // 写不了：同一个接口换个方法就是 403。**这正是"按钮藏起来"之外的第二道防线** ——
        // 前端能藏按钮，但藏不住直接构造的请求，权限必须在服务端也拦一次
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"intruder","password":"Intruder@12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        // 权限集合恰好是三项读权限（用户、租户、审计流水）：多一项写权限就会让
        // "按钮该不该出现"的演示失去区分度
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$.data.permissions.length()").value(3));
    }

    @Test
    @DisplayName("新增租户：编码重复返回 40904，正常新增后可在列表查到")
    void shouldCreateTenantAndRejectDuplicateCode() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"acme","tenantName":"Acme 集团"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tenantCode").value("acme"))
                .andExpect(jsonPath("$.data.tenantLevel").value(1));

        // 同一个编码再来一次 —— 服务层应先给出明确文案，而不是把数据库唯一键冲突抛给调用方
        mockMvc.perform(post("/api/auth/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"acme","tenantName":"另一个名字"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40904));
    }

    @Test
    @DisplayName("刷新令牌轮换：旧刷新令牌用第二次会被拒并触发会话失效")
    void shouldRotateRefreshToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"default","username":"admin","password":"Admin@12345"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.refreshToken");

        // 第一次刷新：成功，并换回一对新令牌
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 用同一个旧令牌再刷新一次：已作废，必须拒绝（这正是泄露检测的触发点）
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40106));
    }

    private String loginAsAdmin() throws Exception {
        return login("default", "admin", "Admin@12345");
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

}
