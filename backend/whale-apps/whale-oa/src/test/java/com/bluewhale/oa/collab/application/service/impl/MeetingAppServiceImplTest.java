package com.bluewhale.oa.collab.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.domain.model.Meeting;
import com.bluewhale.oa.collab.domain.model.MeetingAction;
import com.bluewhale.oa.collab.domain.model.MeetingRoom;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会议应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MeetingAppServiceImplTest {

    @Mock
    private CollabRepository repository;

    @Mock
    private SequenceAppService sequenceAppService;

    private MeetingAppServiceImpl meetingAppService;

    @BeforeEach
    void setUp() {
        meetingAppService = new MeetingAppServiceImpl(repository, sequenceAppService);
    }

    @Test
    @DisplayName("create：开始时间晚于结束时间时拒绝")
    void shouldRejectInvalidTimeRange() {
        CollabCommands.MeetingSaveCommand command = meetingCommand();
        command.setEndTime(command.getStartTime().minusMinutes(10));

        BizException exception = assertThrows(BizException.class,
                () -> meetingAppService.createMeeting(command));

        assertEquals(OaErrorCode.COLLAB_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("create：会议室停用时拒绝")
    void shouldRejectDisabledRoom() {
        when(repository.findRoom(10L)).thenReturn(disabledRoom());

        BizException exception = assertThrows(BizException.class,
                () -> meetingAppService.createMeeting(meetingCommand()));

        assertEquals(OaErrorCode.MEETING_ROOM_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("create：同会议室时段重叠时拒绝")
    void shouldRejectRoomConflict() {
        when(repository.findRoom(10L)).thenReturn(enabledRoom());
        when(repository.hasRoomConflict(any(), any(), any(), any())).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> meetingAppService.createMeeting(meetingCommand()));

        assertEquals(OaErrorCode.MEETING_TIME_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("create：正常创建会议并落参会人快照")
    void shouldCreateMeetingWithParticipants() {
        when(repository.findRoom(10L)).thenReturn(enabledRoom());
        when(repository.hasRoomConflict(any(), any(), any(), any())).thenReturn(false);
        when(sequenceAppService.nextCode("MEETING", "MTG")).thenReturn("MTG0001");

        meetingAppService.createMeeting(meetingCommand());

        ArgumentCaptor<Meeting> meetingCaptor = ArgumentCaptor.forClass(Meeting.class);
        verify(repository).saveMeeting(meetingCaptor.capture());
        assertEquals("MTG0001", meetingCaptor.getValue().getCode());
        assertEquals("SCHEDULED", meetingCaptor.getValue().getStatus());
        verify(repository).saveParticipants(any());
    }

    @Test
    @DisplayName("minute：行动项勾选生成任务并回填 taskId")
    void shouldCreateTaskFromAction() {
        Meeting meeting = scheduledMeeting();
        when(repository.findMeeting(1L)).thenReturn(meeting);
        when(repository.findMinute(1L)).thenReturn(null);
        doAnswer(invocation -> {
            OaTask task = invocation.getArgument(0);
            task.setId(95L);
            return null;
        }).when(repository).saveTask(any());

        meetingAppService.saveMinute(1L, minuteCommand());

        ArgumentCaptor<List<MeetingAction>> actionCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveActions(actionCaptor.capture());
        assertEquals(1, actionCaptor.getValue().size());
        assertEquals(95L, actionCaptor.getValue().get(0).getTaskId());
        assertNotNull(actionCaptor.getValue().get(0).getContent());
    }

    @Test
    @DisplayName("status：终态会议不可再次变更")
    void shouldRejectTerminalStatusChange() {
        Meeting meeting = scheduledMeeting();
        meeting.setStatus("FINISHED");
        when(repository.findMeeting(1L)).thenReturn(meeting);

        BizException exception = assertThrows(BizException.class,
                () -> meetingAppService.changeMeetingStatus(1L, "CANCELLED"));

        assertEquals(OaErrorCode.MEETING_STATUS_CONFLICT, exception.getCode());
    }

    private static CollabCommands.MeetingSaveCommand meetingCommand() {
        CollabCommands.MeetingSaveCommand command = new CollabCommands.MeetingSaveCommand();
        command.setTitle("周会");
        command.setRoomId(10L);
        command.setOrganizer("alice");
        command.setOrganizerId(100L);
        command.setStartTime(LocalDateTime.of(2026, 11, 8, 9, 0));
        command.setEndTime(LocalDateTime.of(2026, 11, 8, 10, 0));
        CollabCommands.ParticipantCommand participant = new CollabCommands.ParticipantCommand();
        participant.setUserId(101L);
        participant.setUserName("Bob");
        command.setParticipants(List.of(participant));
        return command;
    }

    private static CollabCommands.MinuteSaveCommand minuteCommand() {
        CollabCommands.MinuteSaveCommand command = new CollabCommands.MinuteSaveCommand();
        command.setContent("会议纪要内容");
        command.setAuthor("alice");
        command.setAuthorId(100L);
        CollabCommands.ActionCommand action = new CollabCommands.ActionCommand();
        action.setContent("整理需求清单");
        action.setOwnerId(101L);
        action.setOwnerName("Bob");
        action.setDueDate(LocalDate.of(2026, 11, 12));
        action.setCreateTask(true);
        command.setActions(List.of(action));
        return command;
    }

    private static Meeting scheduledMeeting() {
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode("MTG0001");
        meeting.setTitle("周会");
        meeting.setRoomId(10L);
        meeting.setOrganizer("alice");
        meeting.setOrganizerId(100L);
        meeting.setStartTime(LocalDateTime.of(2026, 11, 8, 9, 0));
        meeting.setEndTime(LocalDateTime.of(2026, 11, 8, 10, 0));
        meeting.setStatus("SCHEDULED");
        return meeting;
    }

    private static MeetingRoom enabledRoom() {
        MeetingRoom room = new MeetingRoom();
        room.setId(10L);
        room.setName("大会议室");
        room.setStatus("ENABLED");
        return room;
    }

    private static MeetingRoom disabledRoom() {
        MeetingRoom room = enabledRoom();
        room.setStatus("DISABLED");
        return room;
    }
}
