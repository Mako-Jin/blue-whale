package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.message.po.MessageAnnouncementPo;
import com.bluewhale.message.po.MessageDeliveryPo;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnnouncementExpander} 系统公告惰性展开单元测试。
 *
 * <p>「已发布 − 已展开」差集由 Mapper 的 NOT EXISTS 反连接在数据库侧完成，
 * 因此展开器只断言对差集结果的补写行为。</p>
 *
 * @author BlueWhale
 */
class AnnouncementExpanderTest {

    @BeforeAll
    static void initTableInfo() {
        // 单测无 MyBatis 容器，手动初始化实体 lambda 列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MessageAnnouncementPo.class);
        TableInfoHelper.initTableInfo(assistant, MessageDeliveryPo.class);
    }

    private final MessageAnnouncementMapper announcementMapper = mock(MessageAnnouncementMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final AnnouncementExpander expander = new AnnouncementExpander(announcementMapper, deliveryMapper);

    @Test
    @DisplayName("未展开的已发布公告补写为个人 INAPP 投递记录")
    void shouldExpandMissingAnnouncements() {
        MessageAnnouncementPo announcement = new MessageAnnouncementPo();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        when(announcementMapper.selectUnexpandedForRecipient("7")).thenReturn(List.of(announcement));

        expander.expand(7L);

        ArgumentCaptor<MessageDeliveryPo> captor = ArgumentCaptor.forClass(MessageDeliveryPo.class);
        verify(deliveryMapper).insert(captor.capture());
        MessageDeliveryPo delivery = captor.getValue();
        assertEquals(1001L, delivery.getMessageId());
        assertEquals(MessageChannel.INAPP.name(), delivery.getChannel());
        assertEquals("7", delivery.getRecipient());
        assertEquals("SENT", delivery.getStatus());
        assertEquals(0, delivery.getReadFlag());
    }

    @Test
    @DisplayName("已展开的公告由差集查询过滤，应用层不再重复补写")
    void shouldSkipExpandedAnnouncements() {
        when(announcementMapper.selectUnexpandedForRecipient("7")).thenReturn(List.of());

        expander.expand(7L);

        verify(deliveryMapper, never()).insert(any(MessageDeliveryPo.class));
    }

    @Test
    @DisplayName("并发撞唯一键时静默跳过，不抛出异常")
    void shouldSwallowDuplicateKeyOnConcurrentExpand() {
        MessageAnnouncementPo announcement = new MessageAnnouncementPo();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        when(announcementMapper.selectUnexpandedForRecipient("7")).thenReturn(List.of(announcement));
        when(deliveryMapper.insert(any(MessageDeliveryPo.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_message_channel_recipient"));

        expander.expand(7L);

        verify(deliveryMapper).insert(any(MessageDeliveryPo.class));
    }

    @Test
    @DisplayName("无待展开公告时不插入任何投递记录")
    void shouldDoNothingWhenNoPendingAnnouncement() {
        when(announcementMapper.selectUnexpandedForRecipient("7")).thenReturn(List.of());

        expander.expand(7L);

        verify(deliveryMapper, never()).insert(any(MessageDeliveryPo.class));
    }

    @Test
    @DisplayName("展开异常降级不阻断收件箱读取")
    void shouldDegradeOnExpandFailure() {
        when(announcementMapper.selectUnexpandedForRecipient("7"))
                .thenThrow(new RuntimeException("db down"));

        expander.expand(7L);

        // 不抛异常即视为降级成功；投递记录未被触碰
        verify(deliveryMapper, never()).insert(any(MessageDeliveryPo.class));
    }
}
