package com.bluewhale.lms.exam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽题排序键：可复现性与取值范围。
 *
 * @author BlueWhale
 */
class PickOrderTest {

    /** 取模基数，与 {@link PickOrder} 内部一致 */
    private static final long MODULUS = 1_000_003L;

    @Test
    @DisplayName("同一 seed 与题目 ID 的排序键稳定可复现")
    void keyShouldBeStableForSameSeed() {
        long first = PickOrder.keyOf("PAPER-001", 123L);
        long second = PickOrder.keyOf("PAPER-001", 123L);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("排序键落在取模范围内")
    void keyShouldStayInRange() {
        for (long id = 1; id <= 50; id++) {
            long key = PickOrder.keyOf("SEED", id);
            assertTrue(key >= 0 && key < MODULUS, "排序键越界：" + key);
        }
    }

    @Test
    @DisplayName("不同 seed 排出不同题序（抽题结果受 seed 控制）")
    void differentSeedShouldChangeOrder() {
        List<Long> first = ordered("SEED-A");
        List<Long> second = ordered("SEED-B");
        assertNotEquals(first, second);
    }

    /**
     * 按 seed 对固定题集排序。
     *
     * @param seed 抽题种子
     * @return 题目 ID 顺序
     */
    private List<Long> ordered(String seed) {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            ids.add(id);
        }
        ids.sort(Comparator.comparingLong((Long id) -> PickOrder.keyOf(seed, id))
                .thenComparing((Long id) -> id));
        return ids;
    }
}
