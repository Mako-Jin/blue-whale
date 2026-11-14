package com.bluewhale.tools.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdUtils} 单元测试：UUID 形态、简易 ID 的正数范围与趋势有序性。
 *
 * @author BlueWhale
 */
class IdUtilsTest {

    private static final int RANDOM_BITS = 20;

    @Test
    @DisplayName("nextUUID：32 位无横线且批量不重复")
    void shouldGenerateUniqueUuidWithoutHyphen() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String id = IdUtils.nextUUID();
            assertEquals(32, id.length());
            assertFalse(id.contains("-"));
            assertTrue(ids.add(id), "UUID 出现重复: " + id);
        }
    }

    @Test
    @DisplayName("nextId：返回正数，高位为毫秒时间戳")
    void shouldGeneratePositiveIdWithTimestampHighBits() {
        long before = System.currentTimeMillis();
        long id = IdUtils.nextId();
        long after = System.currentTimeMillis();

        assertTrue(id > 0, "简易 ID 必须落在正 long 范围");
        long timestamp = id >>> RANDOM_BITS;
        assertTrue(timestamp >= before && timestamp <= after,
                "高位时间戳应落于调用区间, actual=" + timestamp);
    }

    @Test
    @DisplayName("nextId：跨毫秒调用严格递增（随机位不足以跨毫秒反超）")
    void shouldIncreaseAcrossMilliseconds() throws InterruptedException {
        long first = IdUtils.nextId();
        Thread.sleep(5L);
        long second = IdUtils.nextId();

        assertTrue(second > first, "跨毫秒生成应递增: " + first + " -> " + second);
    }
}
