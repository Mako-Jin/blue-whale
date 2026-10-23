package com.bluewhale.common.audit.aop;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.audit.support.AuditLogForwarder;
import com.bluewhale.common.audit.support.AuditLogMqPublisher;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计切面单测：覆盖成功/失败两条路径、网关注入头采集、参数脱敏与无请求上下文场景。
 *
 * @author BlueWhale
 */
class AuditLogAspectTest {

    private static final String GATEWAY_USER_ID = "1001";
    private static final String GATEWAY_USERNAME = "alice";
    private static final String GATEWAY_TRACE_ID = "trace-id-from-gateway";

    private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
    private final MockEnvironment environment =
            new MockEnvironment().withProperty("spring.application.name", "whale-auth");
    private final AuditLogAspect aspect = new AuditLogAspect(
            auditLogMapper,
            new SensitiveDataMasker(JsonMapper.builder().build()),
            Runnable::run,
            environment,
            null,
            null);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldRecordSuccessWithGatewayHeadersAndMaskedParams() throws Throwable {
        mockRequest("POST", "/user", true);
        CreatePayload payload = new CreatePayload(2001L, "alice", "P@ssw0rd!");
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{payload});
        when(joinPoint.proceed()).thenReturn(Result.ok("created"));

        aspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        AuditLogRecord record = captureSingleRecord();
        assertThat(record.getTraceId()).isEqualTo(GATEWAY_TRACE_ID);
        assertThat(record.getUserId()).isEqualTo(Long.valueOf(GATEWAY_USER_ID));
        assertThat(record.getUsername()).isEqualTo(GATEWAY_USERNAME);
        assertThat(record.getModule()).isEqualTo("whale-auth");
        assertThat(record.getOperation()).isEqualTo("创建用户");
        assertThat(record.getOperateType()).isEqualTo(OperateType.CREATE.name());
        assertThat(record.getTargetType()).isEqualTo("sys_user");
        assertThat(record.getTargetId()).isEqualTo("2001");
        assertThat(record.getMethod()).isEqualTo(SampleService.class.getName() + "#createUser");
        assertThat(record.getRequestUri()).isEqualTo("/user");
        assertThat(record.getHttpMethod()).isEqualTo("POST");
        assertThat(record.getResponseCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(record.getBizStatus()).isEqualTo(1);
        assertThat(record.getErrorMsg()).isNull();
        assertThat(record.getIp()).isEqualTo("10.1.1.1");
        assertThat(record.getUserAgent()).isEqualTo("junit");
        assertThat(record.getCostMs()).isNotNull();
        assertThat(record.getCreateTime()).isNotNull();
        assertThat(record.getRequestParams()).contains("\"password\":\"******\"")
                .doesNotContain("P@ssw0rd!");
    }

    @Test
    void shouldRecordFailureAndRethrowException() throws Throwable {
        mockRequest("POST", "/role", false);
        BizException failure = new BizException(ErrorCode.FORBIDDEN);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createRole", new Object[]{new CreatePayload(1L, "bob", "secret")});
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation("createRole", CreatePayload.class)))
                .isSameAs(failure);

        AuditLogRecord record = captureSingleRecord();
        assertThat(record.getBizStatus()).isEqualTo(0);
        assertThat(record.getResponseCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(record.getErrorMsg()).contains("BizException").contains(ErrorCode.FORBIDDEN.getMessage());
        // 未携带网关注入头时用户字段留空，但 traceId 仍按网关规格兜底生成
        assertThat(record.getUserId()).isNull();
        assertThat(record.getUsername()).isNull();
        assertThat(record.getTraceId()).hasSize(32);
    }

    @Test
    void shouldSkipParamsWhenRecordParamsDisabled() throws Throwable {
        mockRequest("POST", "/auth/login", false);
        LoginPayload payload = new LoginPayload("alice", "P@ssw0rd!");
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "login", new Object[]{payload});
        when(joinPoint.proceed()).thenReturn(Result.ok("token"));

        aspect.around(joinPoint, annotation("login", LoginPayload.class));

        AuditLogRecord record = captureSingleRecord();
        assertThat(record.getRequestParams()).isNull();
        assertThat(record.getOperateType()).isEqualTo(OperateType.LOGIN.name());
        assertThat(record.getModule()).isEqualTo("whale-auth");
    }

    @Test
    void shouldRecordWithoutHttpRequestContext() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createRole", new Object[]{new CreatePayload(1L, "bob", "secret")});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        aspect.around(joinPoint, annotation("createRole", CreatePayload.class));

        AuditLogRecord record = captureSingleRecord();
        assertThat(record.getBizStatus()).isEqualTo(1);
        assertThat(record.getRequestUri()).isNull();
        assertThat(record.getIp()).isNull();
        assertThat(record.getTraceId()).hasSize(32);
    }

    @Test
    void shouldPersistExactlyOncePerInvocation() throws Throwable {
        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{new CreatePayload(1L, "bob", "secret")});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        aspect.around(joinPoint, annotation("createUser", CreatePayload.class));
        aspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        verify(auditLogMapper, times(2)).insert(any(AuditLogRecord.class));
    }

    @Test
    void shouldIgnoreTargetIdWhenSpelFails() throws Throwable {
        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        aspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        assertThat(captureSingleRecord().getTargetId()).isNull();
    }

    @Test
    void shouldRecordMaskedResultBodyWhenRecordResultEnabled() throws Throwable {
        mockRequest("POST", "/user/query", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "queryUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(new ResultPayload("P@ssw0rd!", "ok"));

        aspect.around(joinPoint, annotation("queryUser", ResultPayload.class));

        String resultBody = captureSingleRecord().getResultBody();
        assertThat(resultBody).contains("\"password\":\"******\"").doesNotContain("P@ssw0rd!");
        assertThat(resultBody).contains("\"note\":\"ok\"");
    }

    @Test
    void shouldNotRecordResultBodyByDefault() throws Throwable {
        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(new ResultPayload("P@ssw0rd!", "ok"));

        aspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        assertThat(captureSingleRecord().getResultBody()).isNull();
    }

    @Test
    void shouldTruncateOversizedResultBody() throws Throwable {
        mockRequest("POST", "/user/query", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "queryUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(new ResultPayload("a", "x".repeat(5000)));

        aspect.around(joinPoint, annotation("queryUser", ResultPayload.class));

        // 与 AuditLogAspect.MAX_RESULT_LENGTH 保持一致的截断上限
        assertThat(captureSingleRecord().getResultBody()).hasSize(2000);
    }

    @Test
    void shouldReturnNullResultBodyWhenNotSerializable() throws Throwable {
        mockRequest("POST", "/user/query", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "queryUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(new CyclicPayload());

        aspect.around(joinPoint, annotation("queryUser", ResultPayload.class));

        assertThat(captureSingleRecord().getResultBody()).isNull();
    }

    @Test
    void shouldSkipLocalInsertWhenForwardSucceeds() throws Throwable {
        AuditLogForwarder forwarder = mock(AuditLogForwarder.class);
        when(forwarder.forward(any())).thenReturn(true);
        AuditLogAspect forwardingAspect = new AuditLogAspect(
                auditLogMapper,
                new SensitiveDataMasker(JsonMapper.builder().build()),
                Runnable::run,
                environment,
                null,
                forwarder);

        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        forwardingAspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        verify(forwarder).forward(any(AuditLogRecord.class));
        verify(auditLogMapper, never()).insert(any(AuditLogRecord.class));
    }

    @Test
    void shouldFallbackToLocalInsertWhenForwardFails() throws Throwable {
        AuditLogForwarder forwarder = mock(AuditLogForwarder.class);
        when(forwarder.forward(any())).thenReturn(false);
        AuditLogAspect forwardingAspect = new AuditLogAspect(
                auditLogMapper,
                new SensitiveDataMasker(JsonMapper.builder().build()),
                Runnable::run,
                environment,
                null,
                forwarder);

        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        forwardingAspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        verify(forwarder).forward(any(AuditLogRecord.class));
        verify(auditLogMapper).insert(any(AuditLogRecord.class));
    }

    @Test
    void shouldPreferMqAndSkipHttpAndLocalWhenPublishSucceeds() throws Throwable {
        AuditLogMqPublisher publisher = mock(AuditLogMqPublisher.class);
        AuditLogForwarder forwarder = mock(AuditLogForwarder.class);
        when(publisher.publish(any())).thenReturn(true);
        AuditLogAspect mqAspect = new AuditLogAspect(
                auditLogMapper,
                new SensitiveDataMasker(JsonMapper.builder().build()),
                Runnable::run,
                environment,
                publisher,
                forwarder);

        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        mqAspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        verify(publisher).publish(any(AuditLogRecord.class));
        verify(forwarder, never()).forward(any());
        verify(auditLogMapper, never()).insert(any(AuditLogRecord.class));
    }

    @Test
    void shouldFallbackFromMqToHttpWhenPublishFails() throws Throwable {
        AuditLogMqPublisher publisher = mock(AuditLogMqPublisher.class);
        AuditLogForwarder forwarder = mock(AuditLogForwarder.class);
        when(publisher.publish(any())).thenReturn(false);
        when(forwarder.forward(any())).thenReturn(true);
        AuditLogAspect mqAspect = new AuditLogAspect(
                auditLogMapper,
                new SensitiveDataMasker(JsonMapper.builder().build()),
                Runnable::run,
                environment,
                publisher,
                forwarder);

        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        mqAspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        verify(publisher).publish(any(AuditLogRecord.class));
        verify(forwarder).forward(any(AuditLogRecord.class));
        verify(auditLogMapper, never()).insert(any(AuditLogRecord.class));
    }

    @Test
    void shouldFallbackToLocalInsertWhenMqAndHttpBothFail() throws Throwable {
        AuditLogMqPublisher publisher = mock(AuditLogMqPublisher.class);
        AuditLogForwarder forwarder = mock(AuditLogForwarder.class);
        when(publisher.publish(any())).thenReturn(false);
        when(forwarder.forward(any())).thenReturn(false);
        AuditLogAspect mqAspect = new AuditLogAspect(
                auditLogMapper,
                new SensitiveDataMasker(JsonMapper.builder().build()),
                Runnable::run,
                environment,
                publisher,
                forwarder);

        mockRequest("POST", "/user", false);
        ProceedingJoinPoint joinPoint = joinPoint(SampleService.class, "createUser", new Object[]{null});
        when(joinPoint.proceed()).thenReturn(Result.ok());

        mqAspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        verify(publisher).publish(any(AuditLogRecord.class));
        verify(forwarder).forward(any(AuditLogRecord.class));
        verify(auditLogMapper).insert(any(AuditLogRecord.class));
    }

    private AuditLogRecord captureSingleRecord() {
        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLogRecord record = captor.getValue();
        assertThat(record).isNotNull();
        return record;
    }

    private static void mockRequest(String httpMethod, String uri, boolean withGatewayHeaders) {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, uri);
        request.addHeader("User-Agent", "junit");
        if (withGatewayHeaders) {
            request.addHeader(AuditLogAspect.TRACE_ID_HEADER, GATEWAY_TRACE_ID);
            request.addHeader(AuditLogAspect.USER_ID_HEADER, GATEWAY_USER_ID);
            request.addHeader(AuditLogAspect.USERNAME_HEADER, GATEWAY_USERNAME);
            request.addHeader("X-Forwarded-For", "10.1.1.1, 10.1.1.2");
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static ProceedingJoinPoint joinPoint(Class<?> declaringType, String methodName, Object[] args) throws Throwable {
        Method method = findMethod(declaringType, methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringTypeName()).thenReturn(declaringType.getName());
        when(signature.getName()).thenReturn(methodName);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getTarget()).thenReturn(new SampleService());
        return joinPoint;
    }

    private static Method findMethod(Class<?> declaringType, String methodName) {
        return List.of(declaringType.getMethods()).stream()
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("测试方法不存在: " + methodName));
    }

    private static AuditLog annotation(String methodName, Class<?> parameterType) throws NoSuchMethodException {
        return SampleService.class.getMethod(methodName, parameterType).getAnnotation(AuditLog.class);
    }

    /** 被切面拦截的样例服务 */
    static class SampleService {

        @AuditLog(module = "whale-auth", operation = "创建用户", type = OperateType.CREATE,
                targetType = "sys_user", targetId = "#request.userId")
        public Result<String> createUser(CreatePayload request) {
            return Result.ok();
        }

        @AuditLog(operation = "用户登录", type = OperateType.LOGIN, recordParams = false)
        public Result<String> login(LoginPayload request) {
            return Result.ok();
        }

        @AuditLog(module = "whale-auth", operation = "创建角色", type = OperateType.CREATE, targetType = "sys_role")
        public Result<String> createRole(CreatePayload request) {
            return Result.ok();
        }

        @AuditLog(module = "whale-auth", operation = "查询用户", type = OperateType.QUERY, recordResult = true)
        public Result<String> queryUser(ResultPayload request) {
            return Result.ok();
        }
    }

    /** 创建类请求样例（含敏感字段） */
    static class CreatePayload {

        private final Long userId;
        private final String username;
        private final String password;

        CreatePayload(Long userId, String username, String password) {
            this.userId = userId;
            this.username = username;
            this.password = password;
        }

        /** SpEL {@code #request.userId} 依赖 getter 访问属性 */
        public Long getUserId() {
            return userId;
        }
    }

    /** 登录请求样例（凭据不得落库） */
    static class LoginPayload {

        private final String username;
        private final String password;

        LoginPayload(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    /** 查询类返回体样例（含敏感字段，用于验证 {@code result_body} 脱敏） */
    static class ResultPayload {

        private final String password;
        private final String note;

        ResultPayload(String password, String note) {
            this.password = password;
            this.note = note;
        }

        /** Jackson 依据 getter 序列化，password 字段须被掩码规则命中 */
        public String getPassword() {
            return password;
        }

        public String getNote() {
            return note;
        }
    }

    /** 自身引用成环的返回体样例（验证序列化失败仅告警、字段留空） */
    static class CyclicPayload {

        /** 直接自身引用，触发 Jackson {@code FAIL_ON_SELF_REFERENCES} 而非栈溢出 */
        public CyclicPayload getSelf() {
            return this;
        }
    }
}
