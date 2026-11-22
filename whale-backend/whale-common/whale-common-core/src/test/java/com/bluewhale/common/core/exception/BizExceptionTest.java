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
        assertEquals(ErrorCode.NOT_FOUND.getMessageKey(), ex.getMessageKey());
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
    @DisplayName("以业务码 + 国际化键 + 中文兜底构造：保留码并记录键与占位参数")
    void shouldKeepCustomCodeWithMessageKey() {
        BizException ex = new BizException(40901, "doc.page.revision-conflict", "页面已被他人修改", 3);

        assertEquals(40901, ex.getCode());
        assertEquals("页面已被他人修改", ex.getMessage());
        assertEquals("doc.page.revision-conflict", ex.getMessageKey());
        assertEquals(1, ex.getMessageArgs().length);
        assertEquals(3, ex.getMessageArgs()[0]);
    }

    @Test
    @DisplayName("以错误码枚举 + 国际化键 + 中文兜底构造：保留枚举码与键")
    void shouldBuildFromErrorCodeWithMessageKey() {
        BizException ex = new BizException(ErrorCode.PARAM_ERROR, "common.param.invalid", "参数非法", "name");

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("common.param.invalid", ex.getMessageKey());
        assertEquals("参数非法", ex.getMessage());
        assertEquals(1, ex.getMessageArgs().length);
        assertEquals("name", ex.getMessageArgs()[0]);
    }

    @Test
    @DisplayName("占位参数显式传 null：归一化为空数组，避免 NPE")
    void shouldNormalizeNullVarargsToEmptyArray() {
        BizException ex = new BizException(40901, "doc.page.conflict", "页面冲突", (Object[]) null);

        assertEquals(0, ex.getMessageArgs().length);
    }

    @Test
    @DisplayName("仅以文案构造：错误码缺省为系统错误")
    void shouldDefaultToSystemError() {
        BizException ex = new BizException("服务内部错误");

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        assertEquals("服务内部错误", ex.getMessage());
    }

    @Test
    @DisplayName("错误码枚举：每个常量的码为正数、文案与国际化键均非空")
    void shouldHaveWellFormedErrorCodes() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertEquals(true, errorCode.getCode() >= 0, errorCode.name() + " 的码不应为负数");
            assertEquals(true, errorCode.getMessage() != null && !errorCode.getMessage().isBlank(),
                    errorCode.name() + " 的文案不应为空");
            assertEquals(true, errorCode.getMessageKey() != null && !errorCode.getMessageKey().isBlank(),
                    errorCode.name() + " 的国际化键不应为空");
        }
    }
}
