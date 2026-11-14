package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelException;
import com.bluewhale.workflow.support.ValidationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConditionGuard} 求值语义与安全边界单元测试。
 *
 * @author BlueWhale
 */
class ConditionGuardTest {

    /** 条件求值上下文（空模型、空节点） */
    private static GuardContext context(Map<String, Object> variables) {
        return GuardContext.of((ProcessModel) null, null, StateMachineEvent.APPROVE, variables);
    }

    @Test
    @DisplayName("accept：直接键名与 #变量 两种写法均可用")
    void shouldSupportBothVariableSyntaxes() {
        GuardContext context = context(Map.of("amount", 200));

        assertTrue(ConditionGuard.of("amount > 100").accept(context, null));
        assertTrue(ConditionGuard.of("#amount > 100").accept(context, null));
        assertFalse(ConditionGuard.of("amount < 100").accept(context, null));
    }

    @Test
    @DisplayName("accept：T() 类型引用被受限上下文拒绝")
    void shouldRejectTypeReference() {
        ConditionGuard guard = ConditionGuard.of("T(java.lang.Runtime).getRuntime() != null");

        ProcessModelException ex = assertThrows(ProcessModelException.class,
                () -> guard.accept(context(Map.of()), null));

        assertEquals(ValidationCode.CONDITION_EVALUATION_FAILED, ex.getValidationCode());
    }

    @Test
    @DisplayName("accept：任意方法调用被受限上下文拒绝")
    void shouldRejectMethodInvocation() {
        ConditionGuard guard = ConditionGuard.of("name.length() > 0");

        ProcessModelException ex = assertThrows(ProcessModelException.class,
                () -> guard.accept(context(Map.of("name", "abc")), null));

        assertEquals(ValidationCode.CONDITION_EVALUATION_FAILED, ex.getValidationCode());
    }
}
