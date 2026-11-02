package com.bluewhale.doc.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocMetrics} 指标计数断言。
 *
 * @author BlueWhale
 */
class DocMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final DocMetrics metrics = new DocMetrics(registry);

    @Test
    void countersShouldStartAtZero() {
        assertThat(registry.get("doc.page.save.conflict").counter().count()).isZero();
        assertThat(registry.get("doc.recycle.purged").counter().count()).isZero();
        assertThat(registry.get("doc.orphan.attachment.removed").counter().count()).isZero();
        assertThat(registry.get("doc.orphan.attachment.failed").counter().count()).isZero();
    }

    @Test
    void recordSaveConflictShouldIncrementOnce() {
        metrics.recordSaveConflict();
        metrics.recordSaveConflict();

        assertThat(registry.get("doc.page.save.conflict").counter().count()).isEqualTo(2D);
    }

    @Test
    void recordRecyclePurgedShouldIgnoreNonPositiveCount() {
        metrics.recordRecyclePurged(0);
        metrics.recordRecyclePurged(-3);
        assertThat(registry.get("doc.recycle.purged").counter().count()).isZero();

        metrics.recordRecyclePurged(5);
        assertThat(registry.get("doc.recycle.purged").counter().count()).isEqualTo(5D);
    }

    @Test
    void recordOrphanRemovedShouldIgnoreNonPositiveCount() {
        metrics.recordOrphanRemoved(0);
        assertThat(registry.get("doc.orphan.attachment.removed").counter().count()).isZero();

        metrics.recordOrphanRemoved(4);
        assertThat(registry.get("doc.orphan.attachment.removed").counter().count()).isEqualTo(4D);
    }

    @Test
    void recordOrphanFailedShouldIncrementOnce() {
        metrics.recordOrphanFailed();

        assertThat(registry.get("doc.orphan.attachment.failed").counter().count()).isEqualTo(1D);
    }

    @Test
    void recordSearchShouldRouteToTaggedTimer() {
        metrics.recordSearch(true, 1000L);
        metrics.recordSearch(false, 2000L);

        assertThat(registry.get("doc.search").tag("mode", "fulltext").timer().count()).isEqualTo(1L);
        assertThat(registry.get("doc.search").tag("mode", "like").timer().count()).isEqualTo(1L);
    }
}
