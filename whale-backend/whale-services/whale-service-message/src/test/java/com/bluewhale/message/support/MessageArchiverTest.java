package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.config.MessageArchiveProperties;
import com.bluewhale.message.mapper.MessageArchiveWatermarkMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.po.MessageArchiveWatermarkPo;
import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.po.MessageRecordPo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MessageArchiver} 归档任务单元测试。
 *
 * @author BlueWhale
 */
class MessageArchiverTest {

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageArchiveWatermarkMapper watermarkMapper = mock(MessageArchiveWatermarkMapper.class);

    private final MessageArchiveClient archiveClient = mock(MessageArchiveClient.class);

    private final MessageArchiveProperties properties = new MessageArchiveProperties();

    private final MessageArchiver archiver = new MessageArchiver(recordMapper, deliveryMapper,
            watermarkMapper, archiveClient, properties, new ObjectMapper().findAndRegisterModules());

    @Test
    @DisplayName("未启用归档时不做任何查询")
    void shouldSkipWhenDisabled() {
        archiver.archive();

        verifyNoInteractions(recordMapper, deliveryMapper, archiveClient);
    }

    @Test
    @DisplayName("按租户导出过期消息并推进水位")
    void shouldArchiveExpiredMessagesAndAdvanceWatermark() {
        properties.setEnabled(true);
        when(recordMapper.selectExpiredTenantIds(any())).thenReturn(List.of(7L));
        when(watermarkMapper.selectById(7L)).thenReturn(null);
        MessageRecordPo record = new MessageRecordPo();
        record.setId(100L);
        record.setStatus("SENT");
        record.setChannels("INAPP");
        record.setCreateTime(LocalDateTime.now().minusDays(365));
        when(recordMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        MessageDeliveryPo delivery = new MessageDeliveryPo();
        delivery.setId(200L);
        delivery.setMessageId(100L);
        delivery.setChannel("INAPP");
        delivery.setStatus("SENT");
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(delivery));
        when(archiveClient.upload(any(), anyString(), anyString())).thenReturn(Map.of("objectKey", "k-1"));

        archiver.archive();

        verify(archiveClient).upload(any(), contains("message-archive-7-"), eq(properties.getBucket()));
        verify(watermarkMapper).insert(any(MessageArchiveWatermarkPo.class));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("已存在水位时按增量续查并更新水位")
    void shouldContinueFromExistingWatermark() {
        properties.setEnabled(true);
        when(recordMapper.selectExpiredTenantIds(any())).thenReturn(List.of(7L));
        MessageArchiveWatermarkPo watermark = new MessageArchiveWatermarkPo();
        watermark.setId(7L);
        watermark.setLastCreateTime(LocalDateTime.now().minusDays(400));
        watermark.setLastId(50L);
        when(watermarkMapper.selectById(7L)).thenReturn(watermark);
        MessageRecordPo record = new MessageRecordPo();
        record.setId(100L);
        record.setStatus("SENT");
        record.setCreateTime(LocalDateTime.now().minusDays(365));
        when(recordMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(record))
                .thenReturn(List.of());
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(archiveClient.upload(any(), anyString(), anyString())).thenReturn(Map.of());

        archiver.archive();

        verify(watermarkMapper).updateById(any(MessageArchiveWatermarkPo.class));
        verify(watermarkMapper, never()).insert(any(MessageArchiveWatermarkPo.class));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("上传失败时不推进水位")
    void shouldNotAdvanceWatermarkWhenUploadFails() {
        properties.setEnabled(true);
        when(recordMapper.selectExpiredTenantIds(any())).thenReturn(List.of(7L));
        when(watermarkMapper.selectById(7L)).thenReturn(null);
        MessageRecordPo record = new MessageRecordPo();
        record.setId(100L);
        record.setStatus("SENT");
        record.setCreateTime(LocalDateTime.now().minusDays(365));
        when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record));
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(archiveClient.upload(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        archiver.archive();

        verify(watermarkMapper, never()).insert(any(MessageArchiveWatermarkPo.class));
        verify(watermarkMapper, never()).updateById(any(MessageArchiveWatermarkPo.class));
        assertNull(TenantContext.get());
    }
}
