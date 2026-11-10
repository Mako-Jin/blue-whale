package com.bluewhale.pdm.document.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图文档版本号值对象单元测试。
 *
 * <p>覆盖解析、草稿递增、发布目标、排序值与非法格式拒绝。</p>
 *
 * @author BlueWhale
 */
class PdmDocVersionNoTest {

    /**
     * 首个草稿固定为 V0.1。
     */
    @Test
    @DisplayName("初始草稿为 V0.1")
    void initialDraftShouldBeV0_1() {
        PdmDocVersionNo version = PdmDocVersionNo.initialDraft();

        assertEquals("V0.1", version.value());
        assertEquals(1, version.seq());
        assertTrue(version.isDraft());
    }

    /**
     * 版本递增只提升次版本号。
     */
    @Test
    @DisplayName("下一草稿为 V1.1")
    void nextDraftShouldIncreaseMinor() {
        PdmDocVersionNo version = PdmDocVersionNo.parse("V1.0");

        assertEquals("V1.1", version.nextDraft().value());
        assertFalse(version.isDraft());
    }

    /**
     * 草稿发布目标为 V1.0；已正式版本保持不变。
     */
    @Test
    @DisplayName("发布目标：V0.x 转 V1.0，V1.x 保持")
    void releaseTargetShouldNormalizeDraft() {
        assertEquals("V1.0", PdmDocVersionNo.parse("V0.3").releaseTarget().value());
        assertEquals("V1.2", PdmDocVersionNo.parse("V1.2").releaseTarget().value());
    }

    /**
     * 排序值 = 主版本 * 10000 + 次版本，保证 V1.0 < V1.1。
     */
    @Test
    @DisplayName("排序值按主次版本组合")
    void seqShouldComposeMajorAndMinor() {
        assertEquals(10000, PdmDocVersionNo.parse("V1.0").seq());
        assertEquals(10001, PdmDocVersionNo.parse("V1.1").seq());
        assertTrue(PdmDocVersionNo.parse("V1.1").compareTo(PdmDocVersionNo.parse("V1.0")) > 0);
    }

    /**
     * 非法格式拒绝解析。
     */
    @Test
    @DisplayName("非法版本号抛出参数异常")
    void parseShouldRejectInvalidFormat() {
        assertThrows(BizException.class, () -> PdmDocVersionNo.parse("1.0"));
        assertThrows(BizException.class, () -> PdmDocVersionNo.parse("V1"));
        assertThrows(BizException.class, () -> PdmDocVersionNo.parse(null));
    }
}
