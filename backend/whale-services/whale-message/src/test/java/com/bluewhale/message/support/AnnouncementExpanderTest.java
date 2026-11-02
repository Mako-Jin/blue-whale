package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.message.entity.MessageAnnouncement;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.enums.AnnouncementStatus;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageAnnouncementMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnnouncementExpander} 系统公告惰性展开单元测试。
 *
 * @author BlueWhale
 */
class AnnouncementExpanderTest {

    @BeforeAll
    static void initTableInfo() {
        // 单测无 MyBatis 容器，手动初始化实体 lambda 列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MessageAnnouncement.class);
        TableInfoHelper.initTableInfo(assistant, MessageDelivery.class);
    }

    private final MessageAnnouncementMapper announcementMapper = mock(MessageAnnouncementMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final AnnouncementExpander expander = new AnnouncementExpander(announcementMapper, deliveryMapper);

    @Test
    @DisplayName("未展开的已发布公告补写为个人 INAPP 投递记录")
    void shouldExpandMissingAnnouncements() {
        MessageAnnouncement announcement = new MessageAnnouncement();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        when(announcementMapper.selectList(any(Wrapper.class))).thenReturn(List.of(announcement));
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        expander.expand(7L);

        ArgumentCaptor<MessageDelivery> captor = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(deliveryMapper).insert(captor.capture());
        MessageDelivery delivery = captor.getValue();
        assertEquals(1001L, delivery.getMessageId());
        assertEquals(MessageChannel.INAPP.name(), delivery.getChannel());
        assertEquals("7", delivery.getRecipient());
        assertEquals("SENT", delivery.getStatus());
        assertEquals(0, delivery.getReadFlag());
    }

    @Test
    @DisplayName("已展开的公告不再重复补写")
    void shouldSkipExpandedAnnouncements() {
        MessageAnnouncement announcement = new MessageAnnouncement();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        when(announcementMapper.selectList(any(Wrapper.class))).thenReturn(List.of(announcement));
        MessageDelivery expanded = new MessageDelivery();
        expanded.setMessageId(1001L);
        expanded.setChannel(MessageChannel.INAPP.name());
        expanded.setRecipient("7");
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expanded));

        expander.expand(7L);

        verify(deliveryMapper, never()).insert(any(MessageDelivery.class));
    }

    @Test
    @DisplayName("并发撞唯一键时静默跳过，不抛出异常")
    void shouldSwallowDuplicateKeyOnConcurrentExpand() {
        MessageAnnouncement announcement = new MessageAnnouncement();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        when(announcementMapper.selectList(any(Wrapper.class))).thenReturn(List.of(announcement));
        when(deliveryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(deliveryMapper.insert(any(MessageDelivery.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_message_channel_recipient"));

        expander.expand(7L);

        verify(deliveryMapper).insert(any(MessageDelivery.class));
    }

    @Test
    @DisplayName("无已发布公告时不查询投递记录也不插入")
    void shouldDoNothingWhenNoPublishedAnnouncement() {
        when(announcementMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        expander.expand(7L);

        verify(deliveryMapper, never()).selectList(any(Wrapper.class));
        verify(deliveryMapper, never()).insert(any(MessageDelivery.class));
    }

    @Test
    @DisplayName("展开异常降级不阻断收件箱读取")
    void shouldDegradeOnExpandFailure() {
        when(announcementMapper.selectList(any(Wrapper.class)))
                .thenThrow(new RuntimeException("db down"));

        expander.expand(7L);

        // 不抛异常即视为降级成功；投递记录未被触碰
        verify(deliveryMapper, never()).insert(any(MessageDelivery.class));
        assertTrue(true);
    }
}
