package com.bluewhale.pdm.support;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一版本号值对象单元测试。
 *
 * <p>覆盖解析、草稿/首发/修订/大改递增、发布目标、排序与非法格式。</p>
 *
 * @author BlueWhale
 */
class PdmVersionNoTest {

    /**
     * 初始草稿与首发版本。
     */
    @Test
    @DisplayName("初始草稿 V0.1、首发 V1.0")
    void initialVersionsShouldBeStable() {
        assertEquals("V0.1", PdmVersionNo.initialDraft().value());
        assertTrue(PdmVersionNo.initialDraft().isDraft());
        assertEquals("V1.0", PdmVersionNo.firstRelease().value());
        assertFalse(PdmVersionNo.firstRelease().isDraft());
    }

    /**
     * 草稿逐次递增次版本，不跳号。
     */
    @Test
    @DisplayName("草稿递增 V0.1 → V0.2 → V0.3")
    void nextMinorShouldIncrementDraft() {
        PdmVersionNo draft = PdmVersionNo.initialDraft();
        assertEquals("V0.2", draft.nextMinor().value());
        assertEquals("V0.3", draft.nextMinor().nextMinor().value());
    }

    /**
     * 修订版本递增次版本。
     */
    @Test
    @DisplayName("修订递增 V1.0 → V1.1")
    void nextMinorShouldIncrementRevision() {
        assertEquals("V1.1", PdmVersionNo.parse("V1.0").nextMinor().value());
        assertEquals("V1.9", PdmVersionNo.parse("V1.8").nextMinor().value());
    }

    /**
     * 大改递增主版本并归零次版本。
     */
    @Test
    @DisplayName("大改递增 V1.3 → V2.0")
    void nextMajorShouldIncrementMajor() {
        assertEquals("V2.0", PdmVersionNo.parse("V1.3").nextMajor().value());
        assertEquals("V10.0", PdmVersionNo.parse("V9.9").nextMajor().value());
    }

    /**
     * 草稿发布归一为首发，正式版本保持不变。
     */
    @Test
    @DisplayName("发布目标 V0.x → V1.0")
    void releaseTargetShouldNormalizeDraft() {
        assertEquals("V1.0", PdmVersionNo.parse("V0.3").releaseTarget().value());
        assertEquals("V1.2", PdmVersionNo.parse("V1.2").releaseTarget().value());
    }

    /**
     * 排序值按主次版本组合，V1.0 < V1.1 < V2.0。
     */
    @Test
    @DisplayName("排序与比较按主次版本")
    void seqShouldComposeMajorAndMinor() {
        assertEquals(1, PdmVersionNo.parse("V0.1").seq());
        assertEquals(10_000, PdmVersionNo.parse("V1.0").seq());
        assertEquals(10_001, PdmVersionNo.parse("V1.1").seq());
        assertTrue(PdmVersionNo.parse("V2.0").compareTo(PdmVersionNo.parse("V1.1")) > 0);
        assertTrue(PdmVersionNo.parse("V1.1").compareTo(PdmVersionNo.parse("V1.0")) > 0);
    }

    /**
     * 值相等按主次版本判断。
     */
    @Test
    @DisplayName("同版本相等")
    void equalsShouldCompareMajorAndMinor() {
        assertEquals(PdmVersionNo.parse("V1.0"), PdmVersionNo.of(1, 0));
        assertFalse(PdmVersionNo.parse("V1.0").equals(PdmVersionNo.parse("V1.1")));
    }

    /**
     * 非法格式与空值拒绝解析。
     */
    @Test
    @DisplayName("非法版本号抛出参数异常")
    void parseShouldRejectInvalidFormat() {
        assertThrows(BizException.class, () -> PdmVersionNo.parse("1.0"));
        assertThrows(BizException.class, () -> PdmVersionNo.parse("V1"));
        assertThrows(BizException.class, () -> PdmVersionNo.parse("V1.0.1"));
        assertThrows(BizException.class, () -> PdmVersionNo.parse(""));
        assertThrows(BizException.class, () -> PdmVersionNo.parse(null));
    }
}
