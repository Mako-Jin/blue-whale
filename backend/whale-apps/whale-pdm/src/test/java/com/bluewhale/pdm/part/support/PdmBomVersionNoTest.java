package com.bluewhale.pdm.part.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BOM 版本号值对象测试。
 *
 * @author BlueWhale
 */
class PdmBomVersionNoTest {

    /**
     * 初始化版本为 V1.0。
     */
    @Test
    void initialShouldBeV1() {
        assertEquals("V1.0", PdmBomVersionNo.INITIAL);
        assertEquals(10000, PdmBomVersionNo.seqOf(PdmBomVersionNo.INITIAL));
    }

    /**
     * 新建版本递增主版本。
     */
    @Test
    void nextMajorShouldIncreaseMajor() {
        assertEquals("V2.0", PdmBomVersionNo.nextMajor("V1.0"));
        assertEquals("V10.0", PdmBomVersionNo.nextMajor("V9.3"));
    }

    /**
     * 非法版本号回落初始版本。
     */
    @Test
    void nextMajorShouldFallbackForInvalid() {
        assertEquals("V1.0", PdmBomVersionNo.nextMajor(null));
        assertEquals("V1.0", PdmBomVersionNo.nextMajor("1.0"));
    }

    /**
     * 非法版本号校验抛参数错误。
     */
    @Test
    void requireValidShouldRejectMalformed() {
        BizException exception = assertThrows(BizException.class,
                () -> PdmBomVersionNo.requireValid("V1"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
