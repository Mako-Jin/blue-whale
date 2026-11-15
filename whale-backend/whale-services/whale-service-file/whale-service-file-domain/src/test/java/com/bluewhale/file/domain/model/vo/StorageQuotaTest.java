package com.bluewhale.file.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StorageQuota} 三维配额判定单元测试。
 *
 * @author BlueWhale
 */
class StorageQuotaTest {

    @Test
    @DisplayName("三个维度均非正数时判定为不限")
    void shouldBeUnlimitedWhenAllDimensionsDisabled() {
        assertFalse(new StorageQuota(0L, 0L, 0L).hasAnyLimit());
        assertFalse(new StorageQuota(-1L, -1L, -1L).hasAnyLimit());
    }

    @Test
    @DisplayName("任一维度为正数即视为启用限制")
    void shouldBeLimitedWhenAnyDimensionEnabled() {
        StorageQuota quota = new StorageQuota(100L, 0L, 0L);

        assertTrue(quota.hasAnyLimit());
        assertTrue(quota.userLimited());
        assertFalse(quota.tenantLimited());
        assertFalse(quota.bucketLimited());
    }

    @Test
    @DisplayName("租户与桶维度分别判定")
    void shouldReportTenantAndBucketLimits() {
        StorageQuota quota = new StorageQuota(0L, 200L, 300L);

        assertTrue(quota.hasAnyLimit());
        assertFalse(quota.userLimited());
        assertTrue(quota.tenantLimited());
        assertTrue(quota.bucketLimited());
    }
}
