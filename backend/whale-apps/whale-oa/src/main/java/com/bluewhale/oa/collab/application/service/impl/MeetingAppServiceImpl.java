package com.bluewhale.oa.collab.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.service.MeetingAppService;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import com.bluewhale.oa.collab.domain.model.Meeting;
import com.bluewhale.oa.collab.domain.model.MeetingAction;
import com.bluewhale.oa.collab.domain.model.MeetingMinute;
import com.bluewhale.oa.collab.domain.model.MeetingParticipant;
import com.bluewhale.oa.collab.domain.model.MeetingRoom;
import com.bluewhale.oa.collab.domain.model.MeetingStatus;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.model.ParticipantStatus;
import com.bluewhale.oa.collab.domain.model.TaskPriority;
import com.bluewhale.oa.collab.domain.model.TaskSource;
import com.bluewhale.oa.collab.domain.model.TaskStatus;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议应用服务实现。
 *
 * <p>会议室时段重叠在服务层校验；纪要中的行动项可同事务生成个人任务。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MeetingAppServiceImpl implements MeetingAppService {

    private static final String ROOM_ENABLED = "ENABLED";

    private static final String ROOM_DISABLED = "DISABLED";

    private static final int DEFAULT_CAPACITY = 10;

    private static final int MAX_CAPACITY = 1000;

    private static final String CODE_BIZ_TYPE = "MEETING";

    private static final String CODE_PREFIX = "MTG";

    private static final String ACTION_OPEN = "OPEN";

    private static final int TASK_TITLE_MAX = 128;

    private final CollabRepository repository;

    private final SequenceAppService sequenceAppService;

    @Override
    public PageResult<CollabViews.RoomView> pageRooms(CollabQuery query) {
        PageResult<MeetingRoom> page = repository.pageRooms(query);
        List<CollabViews.RoomView> records = page.getRecords().stream()
                .map(MeetingAppServiceImpl::toRoomView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<CollabViews.RoomView> listRooms(CollabQuery query) {
        if (!StringUtils.hasText(query.getStatus())) {
            query.setStatus(ROOM_ENABLED);
        }
        return repository.listRooms(query).stream().map(MeetingAppServiceImpl::toRoomView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.RoomView createRoom(CollabCommands.RoomSaveCommand command) {
        String name = command.getName().trim();
        if (repository.roomNameExists(name, null)) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_ROOM_EXISTS, "会议室名称已存在：" + name);
        }
        MeetingRoom room = new MeetingRoom();
        fillRoom(room, command, name);
        repository.saveRoom(room);
        return toRoomView(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.RoomView updateRoom(Long id, CollabCommands.RoomSaveCommand command) {
        MeetingRoom room = requireRoom(id);
        String name = command.getName().trim();
        if (repository.roomNameExists(name, id)) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_ROOM_EXISTS, "会议室名称已存在：" + name);
        }
        fillRoom(room, command, name);
        repository.updateRoom(room);
        return toRoomView(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoom(Long id) {
        requireRoom(id);
        repository.deleteRoom(id);
    }

    @Override
    public PageResult<CollabViews.MeetingView> pageMeetings(CollabQuery query) {
        PageResult<Meeting> page = repository.pageMeetings(query);
        List<CollabViews.MeetingView> records = page.getRecords().stream()
                .map(this::toMeetingView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public CollabViews.MeetingDetailView meetingDetail(Long id) {
        return toMeetingDetail(requireMeeting(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.MeetingDetailView createMeeting(CollabCommands.MeetingSaveCommand command) {
        Meeting meeting = new Meeting();
        meeting.setTitle(command.getTitle().trim());
        meeting.setRoomId(command.getRoomId());
        meeting.setOrganizer(command.getOrganizer());
        meeting.setOrganizerId(command.getOrganizerId());
        meeting.setDeptId(command.getDeptId());
        meeting.setStartTime(command.getStartTime());
        meeting.setEndTime(command.getEndTime());
        meeting.setAgenda(command.getAgenda());
        meeting.setRemark(command.getRemark());
        meeting.setStatus(MeetingStatus.SCHEDULED.name());
        meeting.assertTimeRange();
        List<MeetingParticipant> participants = buildParticipants(command.getParticipants());
        validateRoomAndConflict(meeting, null);
        meeting.setCode(sequenceAppService.nextCode(CODE_BIZ_TYPE, CODE_PREFIX));
        repository.saveMeeting(meeting);
        participants.forEach(item -> item.setMeetingId(meeting.getId()));
        repository.saveParticipants(participants);
        meeting.setParticipants(participants);
        return toMeetingDetail(meeting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.MeetingDetailView updateMeeting(Long id, CollabCommands.MeetingSaveCommand command) {
        Meeting meeting = requireMeeting(id);
        meeting.assertEditable();
        meeting.setTitle(command.getTitle().trim());
        meeting.setRoomId(command.getRoomId());
        meeting.setOrganizer(command.getOrganizer());
        meeting.setOrganizerId(command.getOrganizerId());
        meeting.setDeptId(command.getDeptId());
        meeting.setStartTime(command.getStartTime());
        meeting.setEndTime(command.getEndTime());
        meeting.setAgenda(command.getAgenda());
        meeting.setRemark(command.getRemark());
        meeting.assertTimeRange();
        validateRoomAndConflict(meeting, id);
        repository.updateMeeting(meeting);
        if (command.getParticipants() != null) {
            List<MeetingParticipant> participants = buildParticipants(command.getParticipants());
            participants.forEach(item -> item.setMeetingId(id));
            repository.deleteParticipants(id);
            repository.saveParticipants(participants);
            meeting.setParticipants(participants);
        }
        return toMeetingDetail(meeting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeMeetingStatus(Long id, String status) {
        Meeting meeting = requireMeeting(id);
        boolean changed = meeting.changeStatus(MeetingStatus.of(status));
        if (changed) {
            repository.updateMeeting(meeting);
        }
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.ParticipantView changeParticipantStatus(Long id, Long userId, String status, String remark) {
        Meeting meeting = requireMeeting(id);
        if (MeetingStatus.of(meeting.getStatus()) == MeetingStatus.CANCELLED) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_STATUS_CONFLICT, "已取消的会议不可登记参会状态");
        }
        ParticipantStatus target = ParticipantStatus.of(status);
        MeetingParticipant participant = repository.listParticipants(id).stream()
                .filter(item -> item.getUserId() != null && item.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "参会人不存在：" + userId));
        participant.setStatus(target.name());
        participant.setRemark(remark);
        repository.updateParticipant(participant);
        return toParticipantView(participant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.MeetingDetailView saveMinute(Long id, CollabCommands.MinuteSaveCommand command) {
        Meeting meeting = requireMeeting(id);
        meeting.assertEditable();
        String content = command.getContent().trim();
        MeetingMinute minute = repository.findMinute(id);
        if (minute == null) {
            minute = new MeetingMinute();
            minute.setMeetingId(id);
            minute.setContent(content);
            minute.setAuthor(command.getAuthor());
            minute.setAuthorId(command.getAuthorId());
            repository.saveMinute(minute);
        } else {
            minute.setContent(content);
            minute.setAuthor(command.getAuthor());
            minute.setAuthorId(command.getAuthorId());
            repository.updateMinute(minute);
        }
        repository.deleteActions(id);
        List<MeetingAction> actions = buildActions(meeting, command);
        if (!actions.isEmpty()) {
            repository.saveActions(actions);
        }
        meeting.setMinute(minute);
        meeting.setActions(actions);
        return toMeetingDetail(meeting);
    }

    // ---------------------------------------------------------------- 私有方法

    /**
     * 校验会议关联会议室是否存在、已启用且时段不冲突；未指定会议室时跳过。
     *
     * @param meeting 待校验的会议
     * @param excludeId 冲突校验时需排除的会议ID（编辑场景）
     */
    private void validateRoomAndConflict(Meeting meeting, Long excludeId) {
        if (meeting.getRoomId() != null) {
            MeetingRoom room = repository.findRoom(meeting.getRoomId());
            if (room == null || !ROOM_ENABLED.equals(room.getStatus())) {
                throw OaErrorCode.exception(OaErrorCode.MEETING_ROOM_NOT_FOUND,
                        "会议室不存在或已停用：" + meeting.getRoomId());
            }
            if (repository.hasRoomConflict(meeting.getRoomId(), meeting.getStartTime(), meeting.getEndTime(), excludeId)) {
                throw OaErrorCode.exception(OaErrorCode.MEETING_TIME_CONFLICT);
            }
        }
    }

    /**
     * 构建参会人模型列表，校验参会人非空、用户ID有效且不重复，初始状态为已邀请。
     *
     * @param commands 参会人命令列表
     * @return 参会人模型列表
     */
    private List<MeetingParticipant> buildParticipants(List<CollabCommands.ParticipantCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_PARTICIPANT_REQUIRED);
        }
        Map<Long, MeetingParticipant> unique = new LinkedHashMap<>();
        for (CollabCommands.ParticipantCommand item : commands) {
            if (item.getUserId() == null || unique.containsKey(item.getUserId())) {
                throw OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "参会人重复或用户ID为空");
            }
            MeetingParticipant participant = new MeetingParticipant();
            participant.setUserId(item.getUserId());
            participant.setUserName(item.getUserName());
            participant.setRequired(item.getRequired() == null || item.getRequired() != 0 ? 1 : 0);
            participant.setStatus(ParticipantStatus.INVITED.name());
            participant.setRemark(item.getRemark());
            unique.put(item.getUserId(), participant);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 构建会议行动项列表，需要生成任务的行动项会同步落库个人任务。
     *
     * @param meeting 所属会议
     * @param command 会议纪要命令
     * @return 行动项模型列表
     */
    private List<MeetingAction> buildActions(Meeting meeting, CollabCommands.MinuteSaveCommand command) {
        List<MeetingAction> actions = new ArrayList<>();
        if (command.getActions() == null || command.getActions().isEmpty()) {
            return actions;
        }
        for (CollabCommands.ActionCommand item : command.getActions()) {
            MeetingAction action = new MeetingAction();
            action.setMeetingId(meeting.getId());
            action.setContent(item.getContent().trim());
            action.setOwnerId(item.getOwnerId());
            action.setOwnerName(item.getOwnerName());
            action.setDueDate(item.getDueDate());
            action.setStatus(ACTION_OPEN);
            if (Boolean.TRUE.equals(item.getCreateTask())) {
                OaTask task = buildTask(item, meeting);
                repository.saveTask(task);
                action.setTaskId(task.getId());
            }
            actions.add(action);
        }
        return actions;
    }

    /**
     * 根据会议行动项构建来源为会议的待办个人任务。
     *
     * @param item 行动项命令
     * @param meeting 所属会议
     * @return 个人任务模型
     */
    private OaTask buildTask(CollabCommands.ActionCommand item, Meeting meeting) {
        OaTask task = new OaTask();
        String content = item.getContent().trim();
        task.setTitle(content.length() > TASK_TITLE_MAX ? content.substring(0, TASK_TITLE_MAX) : content);
        task.setDescription("来自会议《" + meeting.getTitle() + "》的行动项");
        task.setOwnerId(item.getOwnerId());
        task.setOwnerName(item.getOwnerName());
        task.setCreatorId(meeting.getOrganizerId());
        task.setCreatorName(meeting.getOrganizer());
        task.setPriority(TaskPriority.NORMAL.name());
        task.setStatus(TaskStatus.TODO.name());
        task.setDueDate(item.getDueDate());
        task.setSource(TaskSource.MEETING.name());
        task.setSourceId(meeting.getId());
        return task;
    }

    /**
     * 查询会议室，不存在时抛出业务异常。
     *
     * @param id 会议室ID
     * @return 会议室模型
     */
    private MeetingRoom requireRoom(Long id) {
        MeetingRoom room = repository.findRoom(id);
        if (room == null) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_ROOM_NOT_FOUND);
        }
        return room;
    }

    /**
     * 查询会议，不存在时抛出业务异常。
     *
     * @param id 会议ID
     * @return 会议模型
     */
    private Meeting requireMeeting(Long id) {
        Meeting meeting = repository.findMeeting(id);
        if (meeting == null) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_NOT_FOUND);
        }
        return meeting;
    }

    /**
     * 将会议室命令字段填充到会议室模型，并校验容纳人数与状态合法性。
     *
     * @param room 目标会议室模型
     * @param command 会议室保存命令
     * @param name 已去除首尾空白后的会议室名称
     */
    private static void fillRoom(MeetingRoom room, CollabCommands.RoomSaveCommand command, String name) {
        room.setName(name);
        room.setLocation(command.getLocation());
        int capacity = command.getCapacity() == null ? DEFAULT_CAPACITY : command.getCapacity();
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "会议室容纳人数需在 1–1000 之间");
        }
        room.setCapacity(capacity);
        String status = StringUtils.hasText(command.getStatus()) ? command.getStatus() : ROOM_ENABLED;
        if (!ROOM_ENABLED.equals(status) && !ROOM_DISABLED.equals(status)) {
            throw OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "会议室状态仅支持 ENABLED / DISABLED");
        }
        room.setStatus(status);
        room.setRemark(command.getRemark());
    }

    /**
     * 将会议模型转换为列表视图，并补充会议室名称与参会人数。
     *
     * @param meeting 会议模型
     * @return 会议视图
     */
    private CollabViews.MeetingView toMeetingView(Meeting meeting) {
        CollabViews.MeetingView view = new CollabViews.MeetingView();
        view.setId(meeting.getId());
        view.setCode(meeting.getCode());
        view.setTitle(meeting.getTitle());
        view.setRoomId(meeting.getRoomId());
        view.setRoomName(roomName(meeting.getRoomId()));
        view.setOrganizer(meeting.getOrganizer());
        view.setOrganizerId(meeting.getOrganizerId());
        view.setDeptId(meeting.getDeptId());
        view.setStartTime(meeting.getStartTime());
        view.setEndTime(meeting.getEndTime());
        view.setAgenda(meeting.getAgenda());
        view.setStatus(meeting.getStatus());
        view.setRemark(meeting.getRemark());
        view.setParticipantCount(repository.listParticipants(meeting.getId()).size());
        view.setCreateTime(meeting.getCreateTime());
        return view;
    }

    /**
     * 查询会议室名称，会议室ID为空或会议室不存在时返回 {@code null}。
     *
     * @param roomId 会议室ID
     * @return 会议室名称
     */
    private String roomName(Long roomId) {
        if (roomId == null) {
            return null;
        }
        MeetingRoom room = repository.findRoom(roomId);
        return room == null ? null : room.getName();
    }

    /**
     * 将会议模型转换为详情视图，补充参会人、纪要与行动项。
     *
     * @param meeting 会议模型
     * @return 会议详情视图
     */
    private CollabViews.MeetingDetailView toMeetingDetail(Meeting meeting) {
        List<MeetingParticipant> participants = meeting.getParticipants() == null
                ? repository.listParticipants(meeting.getId()) : meeting.getParticipants();
        MeetingMinute minute = meeting.getMinute() == null
                ? repository.findMinute(meeting.getId()) : meeting.getMinute();
        List<MeetingAction> actions = meeting.getActions() == null
                ? repository.listActions(meeting.getId()) : meeting.getActions();
        CollabViews.MeetingDetailView view = new CollabViews.MeetingDetailView();
        CollabViews.MeetingView base = toMeetingView(meeting);
        view.setId(base.getId());
        view.setCode(base.getCode());
        view.setTitle(base.getTitle());
        view.setRoomId(base.getRoomId());
        view.setRoomName(base.getRoomName());
        view.setOrganizer(base.getOrganizer());
        view.setOrganizerId(base.getOrganizerId());
        view.setDeptId(base.getDeptId());
        view.setStartTime(base.getStartTime());
        view.setEndTime(base.getEndTime());
        view.setAgenda(base.getAgenda());
        view.setStatus(base.getStatus());
        view.setRemark(base.getRemark());
        view.setParticipantCount(participants.size());
        view.setCreateTime(base.getCreateTime());
        view.setParticipants(participants.stream().map(MeetingAppServiceImpl::toParticipantView).toList());
        view.setMinute(minute == null ? null : toMinuteView(minute));
        view.setActions(actions.stream().map(MeetingAppServiceImpl::toActionView).toList());
        return view;
    }

    /**
     * 将会议室模型转换为会议室视图。
     *
     * @param room 会议室模型
     * @return 会议室视图
     */
    private static CollabViews.RoomView toRoomView(MeetingRoom room) {
        CollabViews.RoomView view = new CollabViews.RoomView();
        view.setId(room.getId());
        view.setName(room.getName());
        view.setLocation(room.getLocation());
        view.setCapacity(room.getCapacity());
        view.setStatus(room.getStatus());
        view.setRemark(room.getRemark());
        view.setCreateTime(room.getCreateTime());
        return view;
    }

    /**
     * 将参会人模型转换为参会人视图。
     *
     * @param participant 参会人模型
     * @return 参会人视图
     */
    private static CollabViews.ParticipantView toParticipantView(MeetingParticipant participant) {
        CollabViews.ParticipantView view = new CollabViews.ParticipantView();
        view.setId(participant.getId());
        view.setUserId(participant.getUserId());
        view.setUserName(participant.getUserName());
        view.setRequired(participant.getRequired());
        view.setStatus(participant.getStatus());
        view.setRemark(participant.getRemark());
        return view;
    }

    /**
     * 将会议纪要模型转换为纪要视图。
     *
     * @param minute 会议纪要模型
     * @return 纪要视图
     */
    private static CollabViews.MinuteView toMinuteView(MeetingMinute minute) {
        CollabViews.MinuteView view = new CollabViews.MinuteView();
        view.setId(minute.getId());
        view.setMeetingId(minute.getMeetingId());
        view.setContent(minute.getContent());
        view.setAuthor(minute.getAuthor());
        view.setAuthorId(minute.getAuthorId());
        view.setUpdateTime(minute.getUpdateTime());
        return view;
    }

    /**
     * 将会议行动项模型转换为行动项视图。
     *
     * @param action 行动项模型
     * @return 行动项视图
     */
    private static CollabViews.ActionView toActionView(MeetingAction action) {
        CollabViews.ActionView view = new CollabViews.ActionView();
        view.setId(action.getId());
        view.setContent(action.getContent());
        view.setOwnerId(action.getOwnerId());
        view.setOwnerName(action.getOwnerName());
        view.setDueDate(action.getDueDate());
        view.setStatus(action.getStatus());
        view.setTaskId(action.getTaskId());
        return view;
    }
}
