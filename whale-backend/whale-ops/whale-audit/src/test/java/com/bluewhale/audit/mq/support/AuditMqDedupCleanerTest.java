package com.bluewhale.audit.mq.support;

import com.bluewhale.audit.mq.mapper.AuditMqMessageMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计 MQ 去重表清理单测：按保留天数删除、异常仅告警不冒泡、非法保留天数按 1 天兜底。
 *
 * @author BlueWhale
 */
class AuditMqDedupCleanerTest {

    private final AuditMqMessageMapper mapper = mock(AuditMqMessageMapper.class);

    @Test
    void shouldDeleteExpiredDedupRows() {
        when(mapper.delete(any())).thenReturn(3);

        String result = new AuditMqDedupCleaner(mapper, 7).cleanExpired();

        verify(mapper).delete(any());
        assertThat(result).isEqualTo("days=7, deleted=3");
    }

    @Test
    void shouldTreatNonPositiveRetentionAsOneDay() {
        when(mapper.delete(any())).thenReturn(0);

        String result = new AuditMqDedupCleaner(mapper, 0).cleanExpired();

        assertThat(result).isEqualTo("days=1, deleted=0");
    }

    @Test
    void shouldSwallowDeleteFailure() {
        when(mapper.delete(any())).thenThrow(new IllegalStateException("db down"));

        String result = assertDoesNotThrow(() -> new AuditMqDedupCleaner(mapper, 7).cleanExpired());

        assertThat(result).isEqualTo("failed");
    }
}
