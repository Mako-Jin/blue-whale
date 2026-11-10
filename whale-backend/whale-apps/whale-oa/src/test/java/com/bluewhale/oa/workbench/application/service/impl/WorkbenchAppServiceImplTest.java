package com.bluewhale.oa.workbench.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.service.ApprovalCenterAppService;
import com.bluewhale.oa.approval.application.view.ApprovalTaskView;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import com.bluewhale.oa.collab.domain.model.Meeting;
import com.bluewhale.oa.collab.domain.model.MeetingRoom;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.workbench.application.view.WorkbenchViews;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作台应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class WorkbenchAppServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    private static final Long USER_ID = 9L;

    @Mock
    private ApprovalCenterAppService approvalCenterAppService;

    @Mock
    private CollabRepository collabRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    private WorkbenchAppServiceImpl workbenchAppService;

    @BeforeEach
    void setUp() {
        workbenchAppService = new WorkbenchAppServiceImpl(
                approvalCenterAppService, collabRepository, employeeProfileRepository);
    }

    @Test
    @DisplayName("summary：待办审批取 total，未读公告取统计值")
    void shouldReadPendingTotalAndUnreadCount() {
        when(approvalCenterAppService.pagePending(any(), any()))
                .thenReturn(PageResult.of(List.<ApprovalTaskView>of(), 7L, 1L, 1L));
        when(collabRepository.countUnreadNotices(USER_ID)).thenReturn(3L);

        WorkbenchViews.SummaryView summary = workbenchAppService.summary(USER_ID, "bob", TODAY);

        assertEquals(7L, summary.getPendingApprovalCount());
        assertEquals(3L, summary.getUnreadNoticeCount());

        ArgumentCaptor<ApprovalActor> actorCaptor = ArgumentCaptor.forClass(ApprovalActor.class);
        ArgumentCaptor<ApprovalCenterQuery> queryCaptor = ArgumentCaptor.forClass(ApprovalCenterQuery.class);
        verify(approvalCenterAppService).pagePending(actorCaptor.capture(), queryCaptor.capture());
        assertEquals(USER_ID, actorCaptor.getValue().getUserId());
        assertEquals("bob", actorCaptor.getValue().getUsername());
        assertEquals(1L, queryCaptor.getValue().getCurrent());
        assertEquals(1L, queryCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("summary：今日会议按参会人与日期查询并映射会议室名称")
    void shouldQueryTodayMeetingsAndMapRoomName() {
        when(collabRepository.pageMeetings(any())).thenReturn(PageResult.of(
                List.of(meeting(1L, "周会", 12L), meeting(2L, "评审", 99L)), 2L, 1L, 200L));
        MeetingRoom room = new MeetingRoom();
        room.setId(12L);
        room.setName("A201");
        when(collabRepository.listRooms(any())).thenReturn(List.of(room));

        WorkbenchViews.SummaryView summary = workbenchAppService.summary(USER_ID, "bob", TODAY);

        assertEquals(2, summary.getTodayMeetings().size());
        assertEquals("A201", summary.getTodayMeetings().getFirst().getRoomName());
        assertNull(summary.getTodayMeetings().get(1).getRoomName(), "未匹配会议室时名称为空");

        ArgumentCaptor<CollabQuery> captor = ArgumentCaptor.forClass(CollabQuery.class);
        verify(collabRepository).pageMeetings(captor.capture());
        assertEquals(USER_ID, captor.getValue().getParticipantId());
        assertEquals(TODAY, captor.getValue().getDateFrom());
        assertEquals(TODAY, captor.getValue().getDateTo());
        assertEquals(1L, captor.getValue().getCurrent());
        assertEquals(200L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("summary：到期任务按负责人、截止日期与条数查询")
    void shouldQueryDueTasksWithLimit() {
        OaTask task = new OaTask();
        task.setId(1L);
        task.setTitle("整理纪要");
        task.setDueDate(TODAY.minusDays(1));
        task.setStatus("TODO");
        task.setPriority("HIGH");
        when(collabRepository.listDueTasks(USER_ID, TODAY, 10)).thenReturn(List.of(task));

        WorkbenchViews.SummaryView summary = workbenchAppService.summary(USER_ID, "bob", TODAY);

        assertEquals(1, summary.getDueTasks().size());
        assertEquals("整理纪要", summary.getDueTasks().getFirst().getTitle());
        assertEquals("TODO", summary.getDueTasks().getFirst().getStatus());
        assertEquals("HIGH", summary.getDueTasks().getFirst().getPriority());
        verify(collabRepository).listDueTasks(USER_ID, TODAY, 10);
    }

    @Test
    @DisplayName("summary：生日窗口含今天与第 7 天，不含第 8 天")
    void shouldFilterBirthdayWindow() {
        when(employeeProfileRepository.page(any())).thenReturn(List.of(
                profile(1L, TODAY),
                profile(2L, TODAY.plusDays(8)),
                profile(3L, TODAY.plusDays(7))));

        WorkbenchViews.SummaryView summary = workbenchAppService.summary(USER_ID, "bob", TODAY);

        assertEquals(2, summary.getBirthdays().size());
        assertEquals(1L, summary.getBirthdays().get(0).getUserId());
        assertEquals(0, summary.getBirthdays().get(0).getDaysAhead());
        assertEquals(3L, summary.getBirthdays().get(1).getUserId());
        assertEquals(7, summary.getBirthdays().get(1).getDaysAhead());
    }

    @Test
    @DisplayName("summary：生日窗口跨年按 MonthDay 计算")
    void shouldHandleBirthdayAcrossYear() {
        LocalDate today = LocalDate.of(2026, 12, 28);
        when(employeeProfileRepository.page(any())).thenReturn(List.of(
                profile(1L, LocalDate.of(1990, 1, 2)),
                profile(2L, LocalDate.of(1990, 1, 4)),
                profile(3L, LocalDate.of(1990, 12, 27))));

        WorkbenchViews.SummaryView summary = workbenchAppService.summary(USER_ID, "bob", today);

        assertEquals(2, summary.getBirthdays().size());
        assertEquals(5, summary.getBirthdays().get(0).getDaysAhead());
        assertEquals(7, summary.getBirthdays().get(1).getDaysAhead());
    }

    @Test
    @DisplayName("summary：30 秒缓存命中后不再调用仓储")
    void shouldReuseCachedSummary() {
        when(approvalCenterAppService.pagePending(any(), any()))
                .thenReturn(PageResult.of(List.<ApprovalTaskView>of(), 0L, 1L, 1L));
        when(collabRepository.pageMeetings(any())).thenReturn(PageResult.of(List.of(), 0L, 1L, 200L));
        when(collabRepository.listDueTasks(USER_ID, TODAY, 10)).thenReturn(List.of());
        when(employeeProfileRepository.page(any())).thenReturn(List.of());

        WorkbenchViews.SummaryView first = workbenchAppService.summary(USER_ID, "bob", TODAY);
        WorkbenchViews.SummaryView second = workbenchAppService.summary(USER_ID, "bob", TODAY);

        assertSame(first, second);
        verify(collabRepository, times(1)).countUnreadNotices(anyLong());
        verify(collabRepository, times(1)).pageMeetings(any());
        verify(employeeProfileRepository, times(1)).page(any(EmployeeProfileQuery.class));
    }

    private static Meeting meeting(Long id, String title, Long roomId) {
        Meeting meeting = new Meeting();
        meeting.setId(id);
        meeting.setTitle(title);
        meeting.setRoomId(roomId);
        meeting.setStartTime(LocalDateTime.of(2026, 11, 7, 9, 0));
        meeting.setEndTime(LocalDateTime.of(2026, 11, 7, 10, 0));
        return meeting;
    }

    private static EmployeeProfile profile(Long userId, LocalDate birthday) {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setUserId(userId);
        profile.setEmployeeNo("E00" + userId);
        profile.setDeptId(88L);
        profile.setBirthday(birthday);
        return profile;
    }
}
