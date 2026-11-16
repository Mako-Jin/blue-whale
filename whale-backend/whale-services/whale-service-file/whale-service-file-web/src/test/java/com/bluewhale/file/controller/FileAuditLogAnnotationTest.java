package com.bluewhale.file.controller;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.file.model.request.FileBatchDeleteRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件高危操作审计注解元测试（批次 28）。
 *
 * <p>以反射断言（不拉起容器）：删除、找回、回滚与批量删除入口均标注 {@code @AuditLog}，
 * 保证「不可逆操作必须留痕」这一设计约束在代码层不回归。</p>
 *
 * @author BlueWhale
 */
class FileAuditLogAnnotationTest {

    /** 审计操作对象类型（与控制器常量 {@code AUDIT_TARGET_TYPE} 一致） */
    private static final String EXPECTED_TARGET_TYPE = "file_info";

    @Test
    @DisplayName("文件删除与回收站找回：@AuditLog 留痕且对象类型/目标ID正确")
    void shouldAnnotateFileDeleteAndRestore() throws NoSuchMethodException {
        assertAuditLog(FileController.class.getMethod("delete", Long.class, HttpServletRequest.class),
                OperateType.DELETE, "#id");
        assertAuditLog(FileController.class.getMethod("restore", Long.class, HttpServletRequest.class),
                OperateType.UPDATE, "#id");
    }

    @Test
    @DisplayName("版本回滚/删除/找回：@AuditLog 留痕且对象类型/目标ID正确")
    void shouldAnnotateVersionOperations() throws NoSuchMethodException {
        assertAuditLog(FileVersionController.class.getMethod("rollback", Long.class, int.class,
                HttpServletRequest.class), OperateType.UPDATE, "#id");
        assertAuditLog(FileVersionController.class.getMethod("delete", Long.class, int.class,
                HttpServletRequest.class), OperateType.DELETE, "#id");
        assertAuditLog(FileVersionController.class.getMethod("restore", Long.class, int.class,
                HttpServletRequest.class), OperateType.UPDATE, "#id");
    }

    @Test
    @DisplayName("批量删除：@AuditLog 以请求中的文件ID列表为目标")
    void shouldAnnotateBatchDelete() throws NoSuchMethodException {
        assertAuditLog(FileBatchController.class.getMethod("batchDelete", FileBatchDeleteRequest.class,
                HttpServletRequest.class), OperateType.DELETE, "#request.ids");
    }

    /**
     * 断言方法带指定审计注解
     *
     * @param method 待断言方法
     * @param expectedType 期望操作类型
     * @param expectedTargetId 期望目标ID表达式
     */
    private static void assertAuditLog(Method method, OperateType expectedType, String expectedTargetId) {
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        assertThat(annotation).as("%s 应标注 @AuditLog", method.getName()).isNotNull();
        assertThat(annotation.type()).as("%s 操作类型", method.getName()).isEqualTo(expectedType);
        assertThat(annotation.operation()).as("%s 操作描述", method.getName()).isNotBlank();
        assertThat(annotation.targetType()).as("%s 对象类型", method.getName()).isEqualTo(EXPECTED_TARGET_TYPE);
        assertThat(annotation.targetId()).as("%s 目标ID表达式", method.getName()).isEqualTo(expectedTargetId);
    }
}
