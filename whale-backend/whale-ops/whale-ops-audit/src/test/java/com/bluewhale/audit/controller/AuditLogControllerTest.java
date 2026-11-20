package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.AuditLogQueryRequest;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计日志查询接口元审计单测（AUTH-07）。
 *
 * <p>以反射断言（不拉起容器）：3 个查询方法均标注 {@code @AuditLog}，且
 * {@code type()=OperateType.QUERY}、{@code operation()} 非空、{@code targetType()="audit_log"}，
 * 保证「读审计本身也被审计」这一设计约束在代码层不回归。</p>
 *
 * @author BlueWhale
 */
class AuditLogControllerTest {

    /** 元审计操作对象类型（与控制器常量 {@code TARGET_TYPE} 一致） */
    private static final String EXPECTED_TARGET_TYPE = "audit_log";

    @Test
    @DisplayName("分页查询：@AuditLog(type=QUERY, targetType=audit_log)")
    void shouldAnnotatePageWithQueryMetaAudit() throws NoSuchMethodException {
        Method method = AuditLogController.class.getDeclaredMethod("page", AuditLogQueryRequest.class);

        assertQueryMetaAudit(method);
    }

    @Test
    @DisplayName("单条详情：@AuditLog(type=QUERY, targetType=audit_log)")
    void shouldAnnotateDetailWithQueryMetaAudit() throws NoSuchMethodException {
        Method method = AuditLogController.class.getDeclaredMethod("detail", Long.class);

        assertQueryMetaAudit(method);
    }

    @Test
    @DisplayName("按链路查询：@AuditLog(type=QUERY, targetType=audit_log)")
    void shouldAnnotateListByTraceIdWithQueryMetaAudit() throws NoSuchMethodException {
        Method method = AuditLogController.class.getDeclaredMethod("listByTraceId", String.class);

        assertQueryMetaAudit(method);
    }

    @Test
    @DisplayName("导出：@AuditLog(type=EXPORT, targetType=audit_log, recordResult=false)")
    void shouldAnnotateExportWithExportMetaAudit() throws NoSuchMethodException {
        Method method = AuditLogController.class.getDeclaredMethod("export", AuditLogQueryRequest.class);

        AuditLog annotation = method.getAnnotation(AuditLog.class);

        assertThat(annotation).as("export 应标注 @AuditLog").isNotNull();
        assertThat(annotation.type()).isEqualTo(OperateType.EXPORT);
        assertThat(annotation.operation()).isNotBlank();
        assertThat(annotation.targetType()).isEqualTo(EXPECTED_TARGET_TYPE);
        assertThat(annotation.recordResult()).isFalse();
    }

    @Test
    @DisplayName("查询方法均为 QUERY 类型且不落返回体（recordResult=false）")
    void shouldNotRecordResultForQueryMethods() throws NoSuchMethodException {
        List<Method> methods = List.of(
                AuditLogController.class.getDeclaredMethod("page", AuditLogQueryRequest.class),
                AuditLogController.class.getDeclaredMethod("detail", Long.class),
                AuditLogController.class.getDeclaredMethod("listByTraceId", String.class));

        for (Method method : methods) {
            AuditLog annotation = method.getAnnotation(AuditLog.class);
            assertThat(annotation).as("%s 应标注 @AuditLog", method.getName()).isNotNull();
            assertThat(annotation.recordResult()).as("%s 查询类不应记录返回体", method.getName()).isFalse();
        }
    }

    /**
     * 断言方法带 QUERY 元审计注解
     *
     * @param method 待断言方法
     */
    private static void assertQueryMetaAudit(Method method) {
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        assertThat(annotation).as("%s 应标注 @AuditLog", method.getName()).isNotNull();
        assertThat(annotation.type()).isEqualTo(OperateType.QUERY);
        assertThat(annotation.operation()).isNotBlank();
        assertThat(annotation.targetType()).isEqualTo(EXPECTED_TARGET_TYPE);
    }
}
