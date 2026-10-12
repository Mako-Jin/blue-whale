package com.bluewhale.sts.app.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.audit.auditlog.AuditLogEntity;
import com.bluewhale.sts.audit.auditlog.AuditLogMapper;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import com.bluewhale.sts.auth.resource.ResourceMapper;
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

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计管道的端到端测试 —— 一条真实请求穿过
 * Token 过滤器 → 审计拦截器 → 鉴权拦截器 → Controller → Service → Mapper → H2，
 * 请求结束后由拦截器把这次操作写进 {@code bw_audit_log}。
 *
 * <p><b>本类真正要证明的一件事：审计记录不是上报上来的。</b>
 * 下面每条用例里都没有任何"发送审计"的代码 —— 调用的是普通的业务接口
 * （新建用户、越权尝试），记录却在请求返回之前就已经躺在库里了。
 * 这正是审计管道与另外两条（日志、事件）的根本差别：
 * 那两条要求调用方主动上报，这一条不需要，也因此**无法被调用方伪造**。
 *
 * <p><b>注意本类用 {@code @Transactional}，但审计记录不会被回滚掉。</b>
 * 业务数据在测试事务里、测试结束回滚；而审计落库走的是独立的
 * {@code REQUIRES_NEW} 事务（见 {@code AuditRecorder}），已经提交了。
 * 这个"不对称"本身就是设计要的效果 —— 业务失败要能回滚，审计不能跟着回滚。
 * 相应地，下面所有断言都**按唯一条件过滤**（用新建用户的 id / 用户名），
 * 而不是断总数，否则会被同一次构建里别的用例产生的记录干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditApiTest {

    /** 与 AuthApiTest 用同一套种子口令（见 sts-server-auth 的 AuthProperties） */
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String AUDITOR_PASSWORD = "Auditor@12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private ResourceMapper resourceMapper;

    /** 管理员的租户，从 {@code /me} 取 —— 不写死数字 */
    private Long adminTenantId;

    @Test
    @DisplayName("新建用户后审计记录已自动产生：没有任何一行上报代码")
    void shouldRecordUserCreationAutomatically() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, "audit_probe_" + shortId());

        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .param("action", "auth.user.create")
                        .param("targetId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].action").value("auth.user.create"))
                .andExpect(jsonPath("$.data.records[0].resource").value("auth:user"))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("admin"))
                .andExpect(jsonPath("$.data.records[0].httpMethod").value("POST"))
                .andExpect(jsonPath("$.data.records[0].requestUri").value("/api/auth/users"))
                // 失败时才有的两列，成功时必须为空 —— 否则审计里"成功"与"失败"
                // 会同时带着错误码，谁都不知道该信哪个
                .andExpect(jsonPath("$.data.records[0].errorCode").value(nullValue()))
                // 只有一个时间戳（服务端记录时间），没有"客户端时间"这一列
                .andExpect(jsonPath("$.data.records[0].serverTime").isNumber());
    }

    @Test
    @DisplayName("越权尝试被记为 FAILURE，并带上真实业务码 —— 这是本仓最容易漏掉的一类")
    void shouldRecordRejectedAttemptAsFailure() throws Exception {
        String auditorToken = login("default", "auditor", AUDITOR_PASSWORD);

        // 审计员没有 auth:user:create 权限：鉴权拦截器在进入 Controller 之前就抛异常，
        // 响应是 **HTTP 200 + 业务码 40301**（本仓约定：业务结果只由 code 表达）。
        // 因此"这次操作失败了"这件事从状态码上完全看不出来 —— 审计必须靠
        // GlobalExceptionHandler 留下的标记来判断，否则会把越权记成"成功创建了用户"。
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"intruder_%s","password":"Intruder@12345"}
                                """.formatted(shortId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        String adminToken = loginAsAdmin();
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "auth.user.create")
                        .param("result", "FAILURE")
                        .param("operatorName", "auditor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 用 greaterThanOrEqualTo 而不是等于 1：同一次构建里别的用例
                // （AuthApiTest 的只读身份用例）也会产生同类记录，而审计走独立事务
                // 不会被测试回滚掉。断言"至少有一条"才是这里真正要守的语义
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].result").value("FAILURE"))
                .andExpect(jsonPath("$.data.records[0].errorCode").value(40301))
                .andExpect(jsonPath("$.data.records[0].errorMessage").isNotEmpty());
    }

    @Test
    @DisplayName("审计查询受权限保护：无权限 403、未认证 401")
    void shouldProtectAuditQuery() throws Exception {
        // 未认证
        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        // 已认证但没有 auth:audit:read
        String adminToken = loginAsAdmin();
        String username = "audit_nobody_" + shortId();
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Nobody@12345","displayName":"无权限用户"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String nobodyToken = login("default", username, "Nobody@12345");
        mockMvc.perform(get("/api/audit/logs").header("Authorization", "Bearer " + nobodyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("审计域没有任何写入接口：POST 被明确拒绝，而不是含糊的『系统内部错误』")
    void shouldExposeNoWriteEndpoint() throws Exception {
        String token = loginAsAdmin();

        // 这条用例守的是一个**架构不变量**，不是功能：一旦有人给审计域加了写入接口，
        // 审计表就从"服务端自己的账本"降级成"谁都能写的一张表"。
        // 断言的是 40500（方法不被支持）而不是任意的非零码 —— 后者会把这条不变量
        // 混进"随便什么错都行"，将来换个 50000 也照样通过。
        mockMvc.perform(post("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"fake.action","resource":"auth:user","result":"SUCCESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40500));
    }

    @Test
    @DisplayName("按动作聚合：动作分布里含刚发生的动作，且失败数单列")
    void shouldAggregateByActionWithFailureCount() throws Exception {
        String token = loginAsAdmin();
        createUser(token, "audit_stats_" + shortId());

        mockMvc.perform(get("/api/audit/logs/stats")
                        .header("Authorization", "Bearer " + token)
                        .param("resource", "auth:user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.failureTotal").isNumber())
                // 聚合窗口必须回传实际生效的时间段：没回传的话，用户看到总数偏小
                // 会以为丢了数据，而不是意识到自己查的是最近 7 天
                .andExpect(jsonPath("$.data.startTime").isNumber())
                .andExpect(jsonPath("$.data.endTime").isNumber())
                .andExpect(jsonPath("$.data.actions[*].action", hasItem("auth.user.create")));
    }

    @Test
    @DisplayName("详情接口按主键取回单条记录")
    void shouldReturnDetailById() throws Exception {
        String token = loginAsAdmin();
        long userId = createUser(token, "audit_detail_" + shortId());

        MvcResult listed = mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .param("action", "auth.user.create")
                        .param("targetId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andReturn();
        Number id = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.records[0].id");

        mockMvc.perform(get("/api/audit/logs/{id}", id.longValue())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.targetId").value(String.valueOf(userId)))
                .andExpect(jsonPath("$.data.id").value(id.longValue()));
    }

    @Test
    @DisplayName("审计记录不存在时返回 40400，而不是空对象")
    void shouldReportNotFoundForUnknownId() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/audit/logs/{id}", 999_999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("别的租户的审计记录对本租户不可见：列表查不到、聚合不含、详情按 404 处理")
    void shouldIsolateAuditLogsAcrossTenants() throws Exception {
        String token = loginAsAdmin();

        // 审计表**没有任何写入接口**（那是它的定义，见 AuditLogService 类注释），
        // 所以造一条"别租户的记录"只能直接写库。这恰好说明这条用例为什么必须存在：
        // 生产环境里这样的记录由另一个租户的真实操作产生，而本租户的管理员不该看见它。
        String action = "tenant.probe." + shortId();
        AuditLogEntity foreign = new AuditLogEntity();
        foreign.setAction(action);
        foreign.setResource("auth:user");
        foreign.setOperatorId(999_999L);
        foreign.setOperatorName("outsider");
        foreign.setTenantId(adminTenantId + 1000L);
        foreign.setResult("SUCCESS");
        foreign.setHttpMethod("POST");
        foreign.setRequestUri("/api/auth/users");
        foreign.setServerTime(System.currentTimeMillis());
        auditLogMapper.insertRecord(foreign);

        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + token)
                        .param("action", action))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        // 聚合路径同样要挡住：它只差一个 WHERE，而返回的只是计数，越权了也几乎不会被察觉
        mockMvc.perform(get("/api/audit/logs/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions[*].action", not(hasItem(action))));

        // 详情返回 404 而不是 403 —— 403 等于向调用方承认"这条记录确实存在"
        AuditLogEntity saved = auditLogMapper.selectOne(Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(AuditLogEntity::getAction, action));
        assertNotNull(saved, "刚插入的审计记录应当查得到");

        mockMvc.perform(get("/api/audit/logs/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("数据范围 ALL 放行整个租户、SELF 只看得到自己，且 operatorId 参数不能用来扩权")
    void shouldApplyDataScopeToAuditQuery() throws Exception {
        String adminToken = loginAsAdmin();
        // 管理员先留下一条记录，作为"SELF 档不该看到"的对照
        createUser(adminToken, "audit_probe_" + shortId());
        long adminId = meUserId(adminToken);

        String username = "audit_self_" + shortId();
        long selfUserId = createUser(adminToken, username);
        long roleId = createRole(adminToken, "AUDIT_SELF_" + shortId(), "只看自己的审计", "SELF");
        grant(adminToken, roleId, resourceIdOf("auth:audit:read"), "ALLOW");
        // 再给一个"会产生审计"的写权限，好让这个账号自己留下一条记录；
        // 只给 audit:read 的话它查什么都是空列表，用例会因为"空"而假通过
        grant(adminToken, roleId, resourceIdOf("auth:user:create"), "ALLOW");
        bindRoleMember(adminToken, roleId, selfUserId);

        String selfToken = login("default", username, "Probe@12345");
        createUser(selfToken, "audit_probe_" + shortId());

        // SELF：每一条都属于他自己。管理员的那些记录就在同一个租户里，但不该出现
        mockMvc.perform(get("/api/audit/logs").header("Authorization", "Bearer " + selfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[*].operatorId", everyItem(is((int) selfUserId))));

        // 自己指定 operatorId 也扩不了权 —— 它只能**在可见范围内进一步收窄**，
        // 所以这里传入管理员的主键，返回的仍然全是自己的记录，而不是空列表：
        // "被忽略"与"筛出一个空结果"在调用方看来必须是同一件事的两种说法，
        // 而实现选的是前者（见 AuditLogService.Visibility.operatorFilter）
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + selfToken)
                        .param("operatorId", String.valueOf(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].operatorId", everyItem(is((int) selfUserId))));

        // ALL：同一个租户里，管理员看得到那个账号留下的记录
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "auth.user.create")
                        .param("operatorName", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[*].operatorId", everyItem(is((int) selfUserId))));
    }

    /** 新建一个用户并返回其主键 */
    private long createUser(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Probe@12345","displayName":"审计探针"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String loginAsAdmin() throws Exception {
        String token = login("default", "admin", ADMIN_PASSWORD);
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        adminTenantId = ((Number) JsonPath.read(
                me.getResponse().getContentAsString(), "$.data.tenantId")).longValue();
        return token;
    }

    /** 当前登录用户的主键，从 {@code /me} 取 —— 写死 1 会在种子策略变化时静默指向别的身份 */
    private long meUserId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(
                me.getResponse().getContentAsString(), "$.data.userId")).longValue();
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

    /** 建一个只读档位的角色。{@code dataScope} 直接写档位字面量，非法值会在服务端被拒 */
    private long createRole(String token, String code, String name, String dataScope) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s","category":"CUSTOM","dataScope":"%s"}
                                """.formatted(code, name, dataScope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private void grant(String token, long roleId, long resourceId, String effect) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"effect":"%s"}
                                """.formatted(resourceId, effect)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void bindRoleMember(String token, long roleId, long userId) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"USER","memberId":%d}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long resourceIdOf(String resourceCode) {
        ResourceEntity resource = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, resourceCode));
        assertNotNull(resource, "种子资源应当存在：" + resourceCode);
        return resource.getId();
    }

    /** 短随机后缀：避免同一次构建里多个用例撞上"登录名租户内唯一" */
    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
