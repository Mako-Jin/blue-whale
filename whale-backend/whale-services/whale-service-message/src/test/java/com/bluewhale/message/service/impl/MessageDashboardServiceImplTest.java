package com.bluewhale.message.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.dto.MessageDashboardQueryRequest;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageReceiptMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.vo.ChannelStatVO;
import com.bluewhale.message.vo.MessageDashboardVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageDashboardServiceImpl} 聚合口径单元测试。
 *
 * @author BlueWhale
 */
class MessageDashboardServiceImplTest {

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageReceiptMapper receiptMapper = mock(MessageReceiptMapper.class);

    private final MessageDashboardServiceImpl service =
            new MessageDashboardServiceImpl(recordMapper, deliveryMapper, receiptMapper);

    @Test
    @DisplayName("开始时间不早于结束时间直接拒绝")
    void shouldRejectIllegalWindow() {
        MessageDashboardQueryRequest request = new MessageDashboardQueryRequest();
        LocalDateTime time = LocalDateTime.of(2026, 11, 19, 10, 0);
        request.setStartTime(time);
        request.setEndTime(time);

        assertThrows(BizException.class, () -> service.dashboard(request));
        verify(deliveryMapper, never()).selectMaps(any());
    }

    @Test
    @DisplayName("按渠道聚合成功率，失败按 FAILED+BOUNCED 计")
    void shouldAggregateChannelStats() {
        when(recordMapper.selectCount(any())).thenReturn(12L);
        when(receiptMapper.selectCount(any())).thenReturn(3L);
        when(deliveryMapper.selectMaps(any())).thenReturn(
                List.of(
                        row("EMAIL", "DELIVERED", 2L),
                        row("EMAIL", "READ", 1L),
                        row("EMAIL", "FAILED", 1L),
                        row("SMS", "SENT", 3L),
                        row("SMS", "EXPIRED", 1L)),
                List.of(row("EMAIL", "SMTP 550 拒收", 1L)));

        MessageDashboardVO dashboard = service.dashboard(new MessageDashboardQueryRequest());

        assertEquals(12L, dashboard.getTotalMessages());
        assertEquals(3L, dashboard.getUnmatchedReceipts());
        assertEquals(8L, dashboard.getTotalDeliveries());
        assertEquals(3L, dashboard.getSuccessCount());
        assertEquals(1L, dashboard.getFailedCount());
        assertEquals(0.375D, dashboard.getSuccessRate(), 0.0001D);
        assertEquals(2, dashboard.getChannels().size());

        ChannelStatVO email = dashboard.getChannels().get(0);
        assertEquals("EMAIL", email.getChannel());
        assertEquals(4L, email.getTotal());
        assertEquals(2L, email.getDelivered());
        assertEquals(1L, email.getRead());
        assertEquals(1L, email.getFailed());
        assertEquals(0.75D, email.getSuccessRate(), 0.0001D);

        ChannelStatVO sms = dashboard.getChannels().get(1);
        assertEquals(3L, sms.getSent());
        assertEquals(1L, sms.getExpired());
        assertEquals(0D, sms.getSuccessRate(), 0.0001D);

        assertEquals(1, dashboard.getTopFailures().size());
        assertEquals("EMAIL", dashboard.getTopFailures().get(0).getChannel());
        assertEquals("SMTP 550 拒收", dashboard.getTopFailures().get(0).getErrorMessage());
        assertEquals(1L, dashboard.getTopFailures().get(0).getCount());
    }

    @Test
    @DisplayName("未传时间窗时默认最近 24 小时，且开始早于结束")
    void shouldDefaultToLastDay() {
        when(recordMapper.selectCount(any())).thenReturn(null);
        when(receiptMapper.selectCount(any())).thenReturn(null);
        when(deliveryMapper.selectMaps(any())).thenReturn(List.of());

        MessageDashboardVO dashboard = service.dashboard(new MessageDashboardQueryRequest());

        assertTrue(dashboard.getStartTime().isBefore(dashboard.getEndTime()));
        assertEquals(24L, Duration.between(
                dashboard.getStartTime(), dashboard.getEndTime()).toHours());
        assertEquals(0L, dashboard.getTotalDeliveries());
        assertEquals(0D, dashboard.getSuccessRate(), 0.0001D);
    }

    /**
     * 构造分组查询结果行。
     *
     * @param channel 渠道
     * @param status  状态或错误摘要
     * @param count   条数
     * @return 结果行
     */
    private static Map<String, Object> row(String channel, String status, long count) {
        Map<String, Object> row = new HashMap<>();
        row.put("channel", channel);
        row.put("status", status);
        row.put("error_message", status);
        row.put("cnt", count);
        return row;
    }
}
