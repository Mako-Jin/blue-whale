package com.bluewhale.sts.audit.recorder;

import com.bluewhale.sts.audit.auditlog.AuditResult;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.common.web.advice.ResponseOutcome;
import com.bluewhale.sts.components.audit.AuditContext;
import com.bluewhale.sts.components.audit.Audited;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 审计拦截器的单元测试 —— 不启 Spring，只测"什么时候记、记成什么"。
 *
 * <p>这几条用例守住的是**判断策略**，而不是"代码能跑"。尤其是
 * {@code shouldRecordFailureWhenBusinessCodeIsNotSuccess}：
 * 它对应"业务失败也返回 HTTP 200"这条本仓特有约定带来的坑，
 * 一旦有人把结果判定简化成"看有没有抛异常"，那条用例必须立刻失败。
 */
class AuditInterceptorTest {

    /** 被拦截的目标：一个标了 @Audited 的写方法，和一个没标的读方法 */
    static class TargetController {

        @Audited(action = "auth.user.create", resource = "auth:user")
        public String create() {
            return "ok";
        }

        public String read() {
            return "ok";
        }
    }

    private AuditRecorder auditRecorder;
    private AuditInterceptor auditInterceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        auditRecorder = mock(AuditRecorder.class);
        auditInterceptor = new AuditInterceptor(auditRecorder);

        request = new MockHttpServletRequest("POST", "/api/auth/users");
        request.setRequestURI("/api/auth/users");
        request.addHeader("User-Agent", "unit-test-agent");
        request.addHeader("X-Trace-Id", "trace-unit-1");
        response = new MockHttpServletResponse();

        AuthContext context = new AuthContext();
        context.setUserId(7L);
        context.setTenantId(1L);
        context.setUsername("admin");
        AuthContextHolder.set(context);
    }

    @AfterEach
    void tearDown() {
        // ThreadLocal 必须清理：它正是本仓反复强调的那类"忘了清就串号"的失效点
        AuthContextHolder.clear();
    }

    @Test
    @DisplayName("成功的写操作：记为 SUCCESS，动作/资源/操作人/对象逐项正确")
    void shouldRecordSuccess() throws Exception {
        // 直接写请求属性，而不调 AuditContext.target(...)：后者要经 RequestContextHolder
        // （只在真实请求线程里存在），单测里不会生效。拦截器读的正是这个属性本身。
        request.setAttribute(AuditContext.TARGET_ATTRIBUTE, 42L);

        AuditRecord record = invoke("create");

        assertThat(record).isNotNull();
        assertThat(record.action()).isEqualTo("auth.user.create");
        assertThat(record.resource()).isEqualTo("auth:user");
        assertThat(record.operatorId()).isEqualTo(7L);
        assertThat(record.operatorName()).isEqualTo("admin");
        assertThat(record.tenantId()).isEqualTo(1L);
        assertThat(record.targetId()).isEqualTo("42");
        assertThat(record.result()).isEqualTo(AuditResult.SUCCESS);
        assertThat(record.errorCode()).isNull();
        assertThat(record.errorMessage()).isNull();
        assertThat(record.httpMethod()).isEqualTo("POST");
        assertThat(record.requestUri()).isEqualTo("/api/auth/users");
        assertThat(record.traceId()).isEqualTo("trace-unit-1");
        assertThat(record.userAgent()).isEqualTo("unit-test-agent");
        assertThat(record.durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("业务代码没补对象时 targetId 为 null，拦截器绝不凭空造值")
    void shouldLeaveTargetIdNullWhenBusinessCodeDoesNotSetIt() throws Exception {
        AuditRecord record = invoke("create");

        assertThat(record).isNotNull();
        assertThat(record.targetId()).isNull();
    }

    @Test
    @DisplayName("没标 @Audited 的接口不产生任何记录")
    void shouldSkipMethodsWithoutAnnotation() throws Exception {
        invoke("read");

        verify(auditRecorder, never()).record(any());
    }

    @Test
    @DisplayName("匿名请求不产生审计：没有操作人的记录回答不了任何问题")
    void shouldSkipAnonymousRequest() throws Exception {
        AuthContextHolder.clear();

        invoke("create");

        verify(auditRecorder, never()).record(any());
    }

    @Test
    @DisplayName("业务失败但 HTTP 200：必须记为 FAILURE，且带上真实业务码")
    void shouldRecordFailureWhenBusinessCodeIsNotSuccess() throws Exception {
        // 这正是本仓最容易踩的坑：越权被 AuthInterceptor 拒绝后，响应是 HTTP 200，
        // 异常也被 GlobalExceptionHandler 吃掉了 —— afterCompletion 拿到的
        // exception 参数是 null（Spring 6/7 里它恒为 null，已核对 DispatcherServlet 源码：
        // processDispatchResult 调用 triggerAfterCompletion 时传的就是 null）。
        // 若不看 ResponseOutcome，这条会被记成"某人成功创建了用户"。
        response.setStatus(200);
        ResponseOutcome.markFailure(request, 40301, "缺少权限：auth:user:create");

        AuditRecord record = invoke("create");

        assertThat(record).isNotNull();
        assertThat(record.result()).isEqualTo(AuditResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo(40301);
        assertThat(record.errorMessage()).isEqualTo("缺少权限：auth:user:create");
    }

    @Test
    @DisplayName("容器级错误：HTTP 状态码 ≥ 400 同样记为 FAILURE")
    void shouldRecordFailureWhenHttpStatusIsError() throws Exception {
        response.setStatus(500);

        AuditRecord record = invoke("create");

        assertThat(record).isNotNull();
        assertThat(record.result()).isEqualTo(AuditResult.FAILURE);
        // 状态码按本仓"5 位编码"规则折成 50000，免得审计表里混进两种长度的码
        assertThat(record.errorCode()).isEqualTo(50000);
    }

    @Test
    @DisplayName("审计写失败绝不影响业务：异常不得向外传播，但要留一条 WARN")
    void shouldSwallowWriteFailure() throws Exception {
        doThrow(new IllegalStateException("数据库连不上")).when(auditRecorder).record(any());

        assertThatCode(() -> invoke("create")).doesNotThrowAnyException();

        // 失败本身要被记一条日志，否则"审计静默停止工作"这件事没人会发现
        verify(auditRecorder, times(1)).reportFailure(any(), any());
    }

    /** 走完 preHandle + afterCompletion，返回这次写下的记录（未写则返回 null） */
    private AuditRecord invoke(String methodName) throws Exception {
        Method method = TargetController.class.getMethod(methodName);
        auditInterceptor.preHandle(request, response, new HandlerMethod(new TargetController(), method));
        auditInterceptor.afterCompletion(request, response, null, null);
        return lastRecord();
    }

    private AuditRecord lastRecord() {
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        try {
            verify(auditRecorder, atLeastOnce()).record(captor.capture());
        } catch (AssertionError notCalled) {
            return null;
        }
        return captor.getValue();
    }

}
