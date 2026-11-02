package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.AnnouncementPublishRequest;
import com.bluewhale.message.dto.AnnouncementQueryRequest;
import com.bluewhale.message.entity.MessageAnnouncement;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.enums.AnnouncementStatus;
import com.bluewhale.message.mapper.MessageAnnouncementMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.vo.AnnouncementVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageAnnouncementServiceImpl} 系统公告单元测试。
 *
 * @author BlueWhale
 */
class MessageAnnouncementServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        // 单测无 MyBatis 容器，手动初始化新实体的 lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MessageAnnouncement.class);
    }

    private final MessageAnnouncementMapper announcementMapper = mock(MessageAnnouncementMapper.class);

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageAnnouncementServiceImpl service =
            new MessageAnnouncementServiceImpl(announcementMapper, recordMapper);

    @Test
    @DisplayName("发布公告落消息记录与公告行，缺省业务类型为 SYSTEM_ANNOUNCE")
    void shouldPublishAnnouncement() {
        AnnouncementPublishRequest request = new AnnouncementPublishRequest();
        request.setTitle("平台维护通知");
        request.setContent("今晚 22:00 平台维护");

        service.publish(request, 9L);

        ArgumentCaptor<MessageRecord> recordCaptor = ArgumentCaptor.forClass(MessageRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        MessageRecord record = recordCaptor.getValue();
        assertEquals("平台维护通知", record.getTitle());
        assertEquals("今晚 22:00 平台维护", record.getContent());
        assertEquals("INAPP", record.getChannels());
        assertEquals("SENT", record.getStatus());
        assertEquals("SYSTEM_ANNOUNCE", record.getBizType());

        ArgumentCaptor<MessageAnnouncement> announcementCaptor =
                ArgumentCaptor.forClass(MessageAnnouncement.class);
        verify(announcementMapper).insert(announcementCaptor.capture());
        MessageAnnouncement announcement = announcementCaptor.getValue();
        assertEquals(AnnouncementStatus.PUBLISHED.name(), announcement.getStatus());
        assertEquals(9L, announcement.getPublisherId());
        assertNotNull(announcement.getPublishTime());
    }

    @Test
    @DisplayName("分页查询合并消息记录的标题与正文")
    void shouldPageAnnouncements() {
        MessageAnnouncement announcement = new MessageAnnouncement();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        announcement.setPublisherId(9L);
        Page<MessageAnnouncement> page = new Page<>(1, 10);
        page.setRecords(List.of(announcement));
        page.setTotal(1);
        when(announcementMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        MessageRecord record = new MessageRecord();
        record.setId(1001L);
        record.setTitle("平台维护通知");
        record.setContent("今晚 22:00 平台维护");
        record.setBizType("SYSTEM_ANNOUNCE");
        when(recordMapper.selectBatchIds(anyList())).thenReturn(List.of(record));

        PageResult<AnnouncementVO> result = service.page(new AnnouncementQueryRequest());

        assertEquals(1, result.getTotal());
        assertEquals("平台维护通知", result.getRecords().get(0).getTitle());
        assertEquals("SYSTEM_ANNOUNCE", result.getRecords().get(0).getBizType());
    }

    @Test
    @DisplayName("撤回公告更新状态为 REVOKED")
    void shouldRevokeAnnouncement() {
        MessageAnnouncement announcement = new MessageAnnouncement();
        announcement.setId(5001L);
        announcement.setRecordId(1001L);
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        when(announcementMapper.selectById(5001L)).thenReturn(announcement);

        service.revoke(5001L);

        assertEquals(AnnouncementStatus.REVOKED.name(), announcement.getStatus());
        verify(announcementMapper).updateById(announcement);
    }

    @Test
    @DisplayName("撤回/删除不存在的公告返回 NOT_FOUND")
    void shouldRejectWhenAnnouncementMissing() {
        when(announcementMapper.selectById(9999L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.revoke(9999L));
        assertThrows(BizException.class, () -> service.delete(9999L));
    }

    @Test
    @DisplayName("非法状态过滤条件返回参数错误")
    void shouldRejectInvalidStatus() {
        AnnouncementQueryRequest request = new AnnouncementQueryRequest();
        request.setStatus("INVALID");

        assertThrows(BizException.class, () -> service.page(request));
    }
}
