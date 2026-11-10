package com.bluewhale.common.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通用错误码与业务异常构造语义测试。
 */
class BizExceptionTest {

    @Test
    @DisplayName("以错误码枚举构造：码与文案取枚举值")
    void shouldBuildFromErrorCode() {
        BizException ex = new BizException(ErrorCode.NOT_FOUND);

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("以错误码枚举 + 自定义文案构造：保留码、覆盖文案")
    void shouldKeepCodeAndOverrideMessage() {
        BizException ex = new BizException(ErrorCode.PARAM_ERROR, "手机号格式非法");

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("手机号格式非法", ex.getMessage());
    }

    @Test
    @DisplayName("以码 + 文案构造：原样保留")
    void shouldBuildFromCodeAndMessage() {
        BizException ex = new BizException(40401, "订单不存在");

        assertEquals(40401, ex.getCode());
        assertEquals("订单不存在", ex.getMessage());
    }

    @Test
    @DisplayName("仅以文案构造：错误码缺省为系统错误")
    void shouldDefaultToSystemError() {
        BizException ex = new BizException("服务内部错误");

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertEquals("服务内部错误", ex.getMessage());
    }

    @Test
    @DisplayName("错误码枚举：每个常量的码为正数且文案非空")
    void shouldHaveWellFormedErrorCodes() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertEquals(true, errorCode.getCode() >= 0, errorCode.name() + " 的码不应为负数");
            assertEquals(true, errorCode.getMessage() != null && !errorCode.getMessage().isBlank(),
                    errorCode.name() + " 的文案不应为空");
        }
    }
}
