package com.bluewhale.common.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通用错误码与业务异常构造语义测试。
 */
class BizExceptionTest {

    @Test
    void errorCodeExposesCodeAndMessage() {
        assertEquals(400, ErrorCode.PARAM_ERROR.getCode());
        assertEquals("参数错误", ErrorCode.PARAM_ERROR.getMessage());
        assertEquals(0, ErrorCode.SUCCESS.getCode());
    }

    @Test
    void constructorWithErrorCodeCopiesCodeAndMessage() {
        BizException exception = new BizException(ErrorCode.NOT_FOUND);
        assertEquals(404, exception.getCode());
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    void constructorWithErrorCodeAndMessageKeepsCode() {
        BizException exception = new BizException(ErrorCode.PARAM_ERROR, "客户名称不能为空");
        assertEquals(400, exception.getCode());
        assertEquals("客户名称不能为空", exception.getMessage());
    }

    @Test
    void constructorWithRawCodeAndMessage() {
        BizException exception = new BizException(4001, "自定义业务异常");
        assertEquals(4001, exception.getCode());
        assertEquals("自定义业务异常", exception.getMessage());
    }

    @Test
    void messageOnlyConstructorFallsBackToSystemError() {
        BizException exception = new BizException("数据库连接失败");
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), exception.getCode());
        assertEquals("数据库连接失败", exception.getMessage());
    }
}
