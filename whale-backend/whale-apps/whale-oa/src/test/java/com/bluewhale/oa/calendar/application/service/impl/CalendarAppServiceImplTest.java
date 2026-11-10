package com.bluewhale.oa.calendar.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.calendar.application.command.CalendarCommands;
import com.bluewhale.oa.calendar.application.view.CalendarViews;
import com.bluewhale.oa.calendar.domain.model.CalendarEntry;
import com.bluewhale.oa.calendar.domain.model.CalendarShare;
import com.bluewhale.oa.calendar.domain.repository.CalendarRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日程应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CalendarAppServiceImplTest {

    @Mock
    private CalendarRepository repository;

    private CalendarAppServiceImpl calendarAppService;

    @BeforeEach
    void setUp() {
        calendarAppService = new CalendarAppServiceImpl(repository);
    }

    @Test
    @DisplayName("create：开始时间晚于结束时间时拒绝")
    void shouldRejectInvalidTimeRange() {
        CalendarCommands.EntrySaveCommand command = entryCommand();
        command.setEndTime(command.getStartTime().minusMinutes(10));

        BizException exception = assertThrows(BizException.class,
                () -> calendarAppService.create(command));

        assertEquals(OaErrorCode.CALENDAR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("create：可见性非法时拒绝")
    void shouldRejectInvalidVisibility() {
        CalendarCommands.EntrySaveCommand command = entryCommand();
        command.setVisibility("TEAM");

        BizException exception = assertThrows(BizException.class,
                () -> calendarAppService.create(command));

        assertEquals(OaErrorCode.CALENDAR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("create：默认私密、个人来源并归一化提醒")
    void shouldApplyCreateDefaults() {
        CalendarCommands.EntrySaveCommand command = entryCommand();
        command.setRemindMinutes(0);

        calendarAppService.create(command);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(repository).saveEntry(captor.capture());
        CalendarEntry saved = captor.getValue();
        assertEquals("PRIVATE", saved.getVisibility());
        assertEquals("PERSONAL", saved.getSource());
        assertEquals(0, saved.getAllDay());
        assertNull(saved.getRemindMinutes(), "提醒分钟数 0 应归一化为不提醒");
        assertEquals(8L, saved.getOwnerId());
    }

    @Test
    @DisplayName("detail：私密日程对非所有者不可见")
    void shouldHidePrivateEntryFromOthers() {
        when(repository.findEntry(1L)).thenReturn(entry("PRIVATE", 8L));
        when(repository.listShares(1L)).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> calendarAppService.detail(1L, 9L));

        assertEquals(OaErrorCode.CALENDAR_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("detail：共享名单内的用户可见但不可编辑")
    void shouldAllowSharedViewer() {
        when(repository.findEntry(1L)).thenReturn(entry("SHARED", 8L));
        CalendarShare share = new CalendarShare();
        share.setUserId(9L);
        share.setUserName("李四");
        when(repository.listShares(1L)).thenReturn(List.of(share));

        CalendarViews.EntryDetailView view = calendarAppService.detail(1L, 9L);

        assertFalse(view.isEditable());
        assertNull(view.getShares(), "非所有者不应看到共享名单");
        assertEquals(8L, view.getOwnerId());
    }

    @Test
    @DisplayName("update：非所有者拒绝编辑")
    void shouldRejectUpdateByNonOwner() {
        when(repository.findEntry(1L)).thenReturn(entry("PRIVATE", 8L));

        BizException exception = assertThrows(BizException.class,
                () -> calendarAppService.update(1L, entryCommand(), 9L));

        assertEquals(OaErrorCode.CALENDAR_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("delete：所有者删除时清理共享记录")
    void shouldDeleteSharesBeforeEntry() {
        when(repository.findEntry(1L)).thenReturn(entry("PRIVATE", 8L));

        calendarAppService.delete(1L, 8L);

        verify(repository).deleteShares(1L);
        verify(repository).deleteEntry(1L);
    }

    @Test
    @DisplayName("replaceShares：去重并回写共享可见性")
    void shouldReplaceSharesAndMarkShared() {
        when(repository.findEntry(1L)).thenReturn(entry("PRIVATE", 8L));
        CalendarCommands.ShareSaveCommand command = new CalendarCommands.ShareSaveCommand();
        CalendarCommands.ShareUserCommand first = new CalendarCommands.ShareUserCommand();
        first.setUserId(9L);
        first.setUserName("李四");
        CalendarCommands.ShareUserCommand duplicate = new CalendarCommands.ShareUserCommand();
        duplicate.setUserId(9L);
        duplicate.setUserName("李四");
        CalendarCommands.ShareUserCommand owner = new CalendarCommands.ShareUserCommand();
        owner.setUserId(8L);
        command.setUsers(List.of(first, duplicate, owner));

        List<CalendarViews.ShareView> shares = calendarAppService.replaceShares(1L, command, 8L);

        assertEquals(1, shares.size());
        assertEquals(9L, shares.get(0).getUserId());
        verify(repository).deleteShares(1L);
        verify(repository).saveShare(any());
        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(repository).updateEntry(captor.capture());
        assertTrue("SHARED".equals(captor.getValue().getVisibility()));
    }

    @Test
    @DisplayName("replaceShares：清空共享名单时回到私密")
    void shouldReturnToPrivateWhenSharesCleared() {
        when(repository.findEntry(1L)).thenReturn(entry("SHARED", 8L));
        CalendarCommands.ShareSaveCommand command = new CalendarCommands.ShareSaveCommand();
        command.setUsers(List.of());

        calendarAppService.replaceShares(1L, command, 8L);

        ArgumentCaptor<CalendarEntry> captor = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(repository).updateEntry(captor.capture());
        assertEquals("PRIVATE", captor.getValue().getVisibility());
    }

    @Test
    @DisplayName("reminders：提醒窗口越界时拒绝")
    void shouldRejectInvalidReminderWindow() {
        BizException exception = assertThrows(BizException.class,
                () -> calendarAppService.reminders(8L, 2000));

        assertEquals(OaErrorCode.CALENDAR_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("reminders：返回窗口内待提醒日程")
    void shouldListReminders() {
        when(repository.listReminders(anyLong(), any(), any())).thenReturn(List.of(entry("PRIVATE", 8L)));

        List<CalendarViews.EntryView> reminders = calendarAppService.reminders(8L, 30);

        assertEquals(1, reminders.size());
    }

    private static CalendarCommands.EntrySaveCommand entryCommand() {
        CalendarCommands.EntrySaveCommand command = new CalendarCommands.EntrySaveCommand();
        command.setOwnerId(8L);
        command.setOwnerName("张三");
        command.setTitle("周会");
        command.setStartTime(LocalDateTime.of(2026, 11, 8, 9, 0));
        command.setEndTime(LocalDateTime.of(2026, 11, 8, 10, 0));
        command.setVisibility("PRIVATE");
        return command;
    }

    private static CalendarEntry entry(String visibility, Long ownerId) {
        CalendarEntry entry = new CalendarEntry();
        entry.setId(1L);
        entry.setOwnerId(ownerId);
        entry.setOwnerName("张三");
        entry.setTitle("周会");
        entry.setStartTime(LocalDateTime.of(2026, 11, 8, 9, 0));
        entry.setEndTime(LocalDateTime.of(2026, 11, 8, 10, 0));
        entry.setVisibility(visibility);
        entry.setSource("PERSONAL");
        return entry;
    }
}
