package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.InAppMessageQueryRequest;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.vo.InAppMessageVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageInAppServiceImpl} 站内信收件箱单元测试。
 *
 * @author BlueWhale
 */
class MessageInAppServiceImplTest {

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageInAppServiceImpl service = new MessageInAppServiceImpl(deliveryMapper, recordMapper);

    @Test
    @DisplayName("收件箱分页合并消息标题与正文")
    void shouldPageInAppMessages() {
        MessageDelivery delivery = delivery(2001L, 1001L, 0);
        Page<MessageDelivery> page = new Page<>(1, 10);
        page.setRecords(List.of(delivery));
        page.setTotal(1);
        when(deliveryMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        MessageRecord record = new MessageRecord();
        record.setId(1001L);
        record.setTitle("系统通知");
        record.setContent("您有一条待办");
        record.setBizType("PM_TASK");
        when(recordMapper.selectBatchIds(List.of(1001L))).thenReturn(List.of(record));

        PageResult<InAppMessageVO> result = service.page(7L, new InAppMessageQueryRequest());

        assertEquals(1, result.getTotal());
        assertEquals("系统通知", result.getRecords().get(0).getTitle());
        assertEquals("您有一条待办", result.getRecords().get(0).getContent());
    }

    @Test
    @DisplayName("未读数按当前用户统计")
    void shouldCountUnread() {
        when(deliveryMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        assertEquals(2L, service.unreadCount(7L));
    }

    @Test
    @DisplayName("标记已读时消息不存在则拒绝")
    void shouldRejectMarkReadWhenMissing() {
        when(deliveryMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThrows(BizException.class, () -> service.markRead(7L, 1001L));
    }

    @Test
    @DisplayName("标记已读更新本人投递记录")
    void shouldMarkRead() {
        when(deliveryMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        service.markRead(7L, 1001L);

        verify(deliveryMapper).update(isNull(), any(Wrapper.class));
    }

    /**
     * 构造站内信投递记录。
     *
     * @param deliveryId 投递ID
     * @param messageId  消息ID
     * @param readFlag   已读标记
     * @return 投递记录
     */
    private static MessageDelivery delivery(Long deliveryId, Long messageId, int readFlag) {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setId(deliveryId);
        delivery.setMessageId(messageId);
        delivery.setChannel("INAPP");
        delivery.setRecipient("7");
        delivery.setStatus("SENT");
        delivery.setReadFlag(readFlag);
        return delivery;
    }
}
