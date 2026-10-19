package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.ApiLogQueryRequest;
import com.bluewhale.audit.dto.BehaviorLogQueryRequest;
import com.bluewhale.audit.dto.FrontendLogQueryRequest;
import com.bluewhale.audit.dto.OutboundLogQueryRequest;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多通道日志查询接口元审计单测（AUTH-07）。
 *
 * <p>以反射断言（不拉起容器）：4 个分页查询方法均标注 {@code @AuditLog}，且
 * {@code type()=OperateType.QUERY}、{@code operation()} 非空、{@code recordResult()=false}，
 * 并对号入座 {@code targetType}，保证「读日志亦须留痕」在四通道上不回归。</p>
 *
 * @author BlueWhale
 */
class LogIngestControllerMetaAuditTest {

    @Test
    @DisplayName("四通道查询：均标注 @AuditLog(type=QUERY) 且各自 targetType 正确")
    void shouldAnnotateFourChannelQueryWithQueryMetaAudit() throws NoSuchMethodException {
        assertQueryMetaAudit(FrontendLogQueryRequest.class, "frontendPage", "frontend_log");
        assertQueryMetaAudit(BehaviorLogQueryRequest.class, "behaviorPage", "behavior_log");
        assertQueryMetaAudit(ApiLogQueryRequest.class, "apiPage", "api_log");
        assertQueryMetaAudit(OutboundLogQueryRequest.class, "outboundPage", "outbound_log");
    }

    /**
     * 断言指定查询方法带 QUERY 元审计注解
     *
     * @param requestType 方法查询请求参数类型
     * @param methodName 方法名
     * @param expectedTargetType 期望的操作对象类型
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    private static void assertQueryMetaAudit(Class<?> requestType, String methodName, String expectedTargetType)
            throws NoSuchMethodException {
        Method method = LogIngestController.class.getDeclaredMethod(methodName, requestType);
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        assertThat(annotation).as("%s 应标注 @AuditLog", methodName).isNotNull();
        assertThat(annotation.type()).as("%s 应为 QUERY 类型", methodName).isEqualTo(OperateType.QUERY);
        assertThat(annotation.operation()).as("%s operation 应非空", methodName).isNotBlank();
        assertThat(annotation.targetType()).as("%s targetType 应为 %s", methodName, expectedTargetType)
                .isEqualTo(expectedTargetType);
        assertThat(annotation.recordResult()).as("%s 查询类不应记录返回体", methodName).isFalse();
    }
}
