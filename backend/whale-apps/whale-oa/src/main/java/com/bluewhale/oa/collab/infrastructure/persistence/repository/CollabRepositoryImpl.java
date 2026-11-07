package com.bluewhale.oa.collab.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import com.bluewhale.oa.collab.domain.model.Meeting;
import com.bluewhale.oa.collab.domain.model.MeetingAction;
import com.bluewhale.oa.collab.domain.model.MeetingMinute;
import com.bluewhale.oa.collab.domain.model.MeetingParticipant;
import com.bluewhale.oa.collab.domain.model.MeetingRoom;
import com.bluewhale.oa.collab.domain.model.Notice;
import com.bluewhale.oa.collab.domain.model.NoticeRecipient;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.model.TaskComment;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.MeetingActionEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.MeetingEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.MeetingMinuteEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.MeetingParticipantEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.MeetingRoomEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.NoticeEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.NoticeRecipientEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.OaTaskEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.entity.TaskCommentEntity;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.MeetingActionMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.MeetingMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.MeetingMinuteMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.MeetingParticipantMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.MeetingRoomMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.NoticeMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.NoticeRecipientMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.OaTaskMapper;
import com.bluewhale.oa.collab.infrastructure.persistence.mapper.TaskCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 协同业务仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CollabRepositoryImpl implements CollabRepository {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 到期任务条数硬上限 */
    private static final int MAX_DUE_TASK_LIMIT = 50;

    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private static final String STATUS_TODO = "TODO";

    private static final String STATUS_DOING = "DOING";

    private final MeetingRoomMapper roomMapper;

    private final MeetingMapper meetingMapper;

    private final MeetingParticipantMapper participantMapper;

    private final MeetingMinuteMapper minuteMapper;

    private final MeetingActionMapper actionMapper;

    private final NoticeMapper noticeMapper;

    private final NoticeRecipientMapper recipientMapper;

    private final OaTaskMapper taskMapper;

    private final TaskCommentMapper commentMapper;

    // ---------------------------------------------------------------- 会议室

    @Override
    public MeetingRoom findRoom(Long id) {
        MeetingRoomEntity entity = roomMapper.selectById(id);
        return entity == null ? null : toRoom(entity);
    }

    @Override
    public boolean roomNameExists(String name, Long excludeId) {
        LambdaQueryWrapper<MeetingRoomEntity> wrapper = new LambdaQueryWrapper<MeetingRoomEntity>()
                .eq(MeetingRoomEntity::getName, name);
        if (excludeId != null) {
            wrapper.ne(MeetingRoomEntity::getId, excludeId);
        }
        Long total = roomMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public PageResult<MeetingRoom> pageRooms(CollabQuery query) {
        Page<MeetingRoomEntity> page = roomMapper.selectPage(page(query), roomFilter(query));
        List<MeetingRoom> records = page.getRecords().stream().map(CollabRepositoryImpl::toRoom).toList();
        Long total = roomMapper.selectCount(roomFilter(query));
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public List<MeetingRoom> listRooms(CollabQuery query) {
        return roomMapper.selectList(new LambdaQueryWrapper<MeetingRoomEntity>()
                        .eq(StringUtils.hasText(query.getStatus()), MeetingRoomEntity::getStatus, query.getStatus())
                        .orderByAsc(MeetingRoomEntity::getName))
                .stream().map(CollabRepositoryImpl::toRoom).toList();
    }

    @Override
    public void saveRoom(MeetingRoom room) {
        MeetingRoomEntity entity = new MeetingRoomEntity();
        fill(entity, room);
        roomMapper.insert(entity);
        room.setId(entity.getId());
    }

    @Override
    public void updateRoom(MeetingRoom room) {
        MeetingRoomEntity entity = new MeetingRoomEntity();
        entity.setId(room.getId());
        fill(entity, room);
        roomMapper.updateById(entity);
    }

    @Override
    public void deleteRoom(Long id) {
        roomMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 会议

    @Override
    public PageResult<Meeting> pageMeetings(CollabQuery query) {
        Page<MeetingEntity> page = meetingMapper.selectPage(page(query), meetingFilter(query));
        List<Meeting> records = page.getRecords().stream().map(CollabRepositoryImpl::toMeeting).toList();
        Long total = meetingMapper.selectCount(meetingFilter(query));
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public Meeting findMeeting(Long id) {
        MeetingEntity entity = meetingMapper.selectById(id);
        return entity == null ? null : toMeeting(entity);
    }

    @Override
    public boolean hasRoomConflict(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        LambdaQueryWrapper<MeetingEntity> wrapper = new LambdaQueryWrapper<MeetingEntity>()
                .eq(MeetingEntity::getRoomId, roomId)
                .ne(MeetingEntity::getStatus, STATUS_CANCELLED)
                .lt(MeetingEntity::getStartTime, end)
                .gt(MeetingEntity::getEndTime, start);
        if (excludeId != null) {
            wrapper.ne(MeetingEntity::getId, excludeId);
        }
        Long total = meetingMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public void saveMeeting(Meeting meeting) {
        MeetingEntity entity = new MeetingEntity();
        fill(entity, meeting);
        meetingMapper.insert(entity);
        meeting.setId(entity.getId());
    }

    @Override
    public void updateMeeting(Meeting meeting) {
        MeetingEntity entity = new MeetingEntity();
        entity.setId(meeting.getId());
        fill(entity, meeting);
        meetingMapper.updateById(entity);
    }

    @Override
    public List<MeetingParticipant> listParticipants(Long meetingId) {
        return participantMapper.selectList(new LambdaQueryWrapper<MeetingParticipantEntity>()
                        .eq(MeetingParticipantEntity::getMeetingId, meetingId)
                        .orderByAsc(MeetingParticipantEntity::getId))
                .stream().map(CollabRepositoryImpl::toParticipant).toList();
    }

    @Override
    public void saveParticipants(List<MeetingParticipant> participants) {
        participants.forEach(participant -> {
            MeetingParticipantEntity entity = new MeetingParticipantEntity();
            fill(entity, participant);
            participantMapper.insert(entity);
            participant.setId(entity.getId());
        });
    }

    @Override
    public void updateParticipant(MeetingParticipant participant) {
        MeetingParticipantEntity entity = new MeetingParticipantEntity();
        entity.setId(participant.getId());
        entity.setStatus(participant.getStatus());
        entity.setRemark(participant.getRemark());
        participantMapper.updateById(entity);
    }

    @Override
    public void deleteParticipants(Long meetingId) {
        participantMapper.delete(new LambdaQueryWrapper<MeetingParticipantEntity>()
                .eq(MeetingParticipantEntity::getMeetingId, meetingId));
    }

    @Override
    public MeetingMinute findMinute(Long meetingId) {
        MeetingMinuteEntity entity = minuteMapper.selectOne(new LambdaQueryWrapper<MeetingMinuteEntity>()
                .eq(MeetingMinuteEntity::getMeetingId, meetingId));
        return entity == null ? null : toMinute(entity);
    }

    @Override
    public void saveMinute(MeetingMinute minute) {
        MeetingMinuteEntity entity = new MeetingMinuteEntity();
        fill(entity, minute);
        minuteMapper.insert(entity);
        minute.setId(entity.getId());
    }

    @Override
    public void updateMinute(MeetingMinute minute) {
        MeetingMinuteEntity entity = new MeetingMinuteEntity();
        entity.setId(minute.getId());
        fill(entity, minute);
        minuteMapper.updateById(entity);
    }

    @Override
    public List<MeetingAction> listActions(Long meetingId) {
        return actionMapper.selectList(new LambdaQueryWrapper<MeetingActionEntity>()
                        .eq(MeetingActionEntity::getMeetingId, meetingId)
                        .orderByAsc(MeetingActionEntity::getId))
                .stream().map(CollabRepositoryImpl::toAction).toList();
    }

    @Override
    public void saveActions(List<MeetingAction> actions) {
        actions.forEach(action -> {
            MeetingActionEntity entity = new MeetingActionEntity();
            fill(entity, action);
            actionMapper.insert(entity);
            action.setId(entity.getId());
        });
    }

    @Override
    public void updateAction(MeetingAction action) {
        MeetingActionEntity entity = new MeetingActionEntity();
        entity.setId(action.getId());
        fill(entity, action);
        actionMapper.updateById(entity);
    }

    @Override
    public void deleteActions(Long meetingId) {
        actionMapper.delete(new LambdaQueryWrapper<MeetingActionEntity>()
                .eq(MeetingActionEntity::getMeetingId, meetingId));
    }

    // ------------------------------------------------------------------ 公告

    @Override
    public PageResult<Notice> pageNotices(CollabQuery query) {
        Page<NoticeEntity> page = noticeMapper.selectPage(page(query), noticeFilter(query));
        List<Notice> records = page.getRecords().stream().map(CollabRepositoryImpl::toNotice).toList();
        Long total = noticeMapper.selectCount(noticeFilter(query));
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public PageResult<Notice> pageMyNotices(CollabQuery query) {
        Long userId = query.getUserId();
        if (userId == null) {
            return PageResult.of(List.of(), 0L, query.getCurrent(), query.getSize());
        }
        List<Long> noticeIds = recipientMapper.selectList(new LambdaQueryWrapper<NoticeRecipientEntity>()
                        .eq(NoticeRecipientEntity::getUserId, userId))
                .stream().map(NoticeRecipientEntity::getNoticeId).toList();
        if (noticeIds.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getCurrent(), query.getSize());
        }
        LambdaQueryWrapper<NoticeEntity> wrapper = new LambdaQueryWrapper<NoticeEntity>()
                .in(NoticeEntity::getId, noticeIds)
                .eq(NoticeEntity::getStatus, STATUS_PUBLISHED)
                .and(StringUtils.hasText(query.getKeyword()), inner -> inner
                        .like(NoticeEntity::getTitle, query.getKeyword())
                        .or().like(NoticeEntity::getContent, query.getKeyword()))
                .orderByDesc(NoticeEntity::getPublishTime)
                .orderByDesc(NoticeEntity::getId);
        Page<NoticeEntity> page = noticeMapper.selectPage(page(query), wrapper);
        Long total = noticeMapper.selectCount(wrapper);
        List<Notice> records = page.getRecords().stream().map(CollabRepositoryImpl::toNotice).toList();
        records.forEach(notice -> {
            NoticeRecipient recipient = findRecipient(notice.getId(), userId);
            if (recipient != null) {
                notice.setMyReadFlag(recipient.getReadFlag());
                notice.setMyReadTime(recipient.getReadTime());
            }
        });
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public Notice findNotice(Long id) {
        NoticeEntity entity = noticeMapper.selectById(id);
        return entity == null ? null : toNotice(entity);
    }

    @Override
    public void saveNotice(Notice notice) {
        NoticeEntity entity = new NoticeEntity();
        fill(entity, notice);
        noticeMapper.insert(entity);
        notice.setId(entity.getId());
    }

    @Override
    public void updateNotice(Notice notice) {
        NoticeEntity entity = new NoticeEntity();
        entity.setId(notice.getId());
        fill(entity, notice);
        noticeMapper.updateById(entity);
    }

    @Override
    public List<NoticeRecipient> listRecipients(Long noticeId) {
        return recipientMapper.selectList(new LambdaQueryWrapper<NoticeRecipientEntity>()
                        .eq(NoticeRecipientEntity::getNoticeId, noticeId)
                        .orderByAsc(NoticeRecipientEntity::getId))
                .stream().map(CollabRepositoryImpl::toRecipient).toList();
    }

    @Override
    public NoticeRecipient findRecipient(Long noticeId, Long userId) {
        NoticeRecipientEntity entity = recipientMapper.selectOne(new LambdaQueryWrapper<NoticeRecipientEntity>()
                .eq(NoticeRecipientEntity::getNoticeId, noticeId)
                .eq(NoticeRecipientEntity::getUserId, userId));
        return entity == null ? null : toRecipient(entity);
    }

    @Override
    public void saveRecipients(List<NoticeRecipient> recipients) {
        recipients.forEach(recipient -> {
            NoticeRecipientEntity entity = new NoticeRecipientEntity();
            fill(entity, recipient);
            recipientMapper.insert(entity);
            recipient.setId(entity.getId());
        });
    }

    @Override
    public void updateRecipient(NoticeRecipient recipient) {
        NoticeRecipientEntity entity = new NoticeRecipientEntity();
        entity.setId(recipient.getId());
        entity.setReadFlag(recipient.getReadFlag());
        entity.setReadTime(recipient.getReadTime());
        recipientMapper.updateById(entity);
    }

    @Override
    public void deleteRecipients(Long noticeId) {
        recipientMapper.delete(new LambdaQueryWrapper<NoticeRecipientEntity>()
                .eq(NoticeRecipientEntity::getNoticeId, noticeId));
    }

    @Override
    public long countUnreadNotices(Long userId) {
        if (userId == null) {
            return 0L;
        }
        Long total = recipientMapper.selectCount(new LambdaQueryWrapper<NoticeRecipientEntity>()
                .eq(NoticeRecipientEntity::getUserId, userId)
                .eq(NoticeRecipientEntity::getReadFlag, 0));
        return total == null ? 0L : total;
    }

    // ------------------------------------------------------------------ 任务

    @Override
    public PageResult<OaTask> pageTasks(CollabQuery query) {
        Page<OaTaskEntity> page = taskMapper.selectPage(page(query), taskFilter(query));
        List<OaTask> records = page.getRecords().stream().map(CollabRepositoryImpl::toTask).toList();
        Long total = taskMapper.selectCount(taskFilter(query));
        return PageResult.of(records, total == null ? 0L : total, query.getCurrent(), query.getSize());
    }

    @Override
    public List<OaTask> listDueTasks(Long ownerId, LocalDate dueTo, int limit) {
        if (ownerId == null) {
            return List.of();
        }
        int capped = Math.min(Math.max(limit, 1), MAX_DUE_TASK_LIMIT);
        Page<OaTaskEntity> page = taskMapper.selectPage(new Page<>(1L, capped, false),
                new LambdaQueryWrapper<OaTaskEntity>()
                        .eq(OaTaskEntity::getOwnerId, ownerId)
                        .le(OaTaskEntity::getDueDate, dueTo)
                        .in(OaTaskEntity::getStatus, STATUS_TODO, STATUS_DOING)
                        .orderByAsc(OaTaskEntity::getDueDate)
                        .orderByAsc(OaTaskEntity::getId));
        return page.getRecords().stream().map(CollabRepositoryImpl::toTask).toList();
    }

    @Override
    public OaTask findTask(Long id) {
        OaTaskEntity entity = taskMapper.selectById(id);
        return entity == null ? null : toTask(entity);
    }

    @Override
    public void saveTask(OaTask task) {
        OaTaskEntity entity = new OaTaskEntity();
        fill(entity, task);
        taskMapper.insert(entity);
        task.setId(entity.getId());
    }

    @Override
    public void updateTask(OaTask task) {
        OaTaskEntity entity = new OaTaskEntity();
        entity.setId(task.getId());
        fill(entity, task);
        taskMapper.updateById(entity);
    }

    @Override
    public void deleteTask(Long id) {
        taskMapper.deleteById(id);
    }

    @Override
    public List<TaskComment> listComments(Long taskId) {
        return commentMapper.selectList(new LambdaQueryWrapper<TaskCommentEntity>()
                        .eq(TaskCommentEntity::getTaskId, taskId)
                        .orderByAsc(TaskCommentEntity::getId))
                .stream().map(CollabRepositoryImpl::toComment).toList();
    }

    @Override
    public TaskComment findComment(Long id) {
        TaskCommentEntity entity = commentMapper.selectById(id);
        return entity == null ? null : toComment(entity);
    }

    @Override
    public void saveComment(TaskComment comment) {
        TaskCommentEntity entity = new TaskCommentEntity();
        fill(entity, comment);
        commentMapper.insert(entity);
        comment.setId(entity.getId());
    }

    @Override
    public void deleteComment(Long id) {
        commentMapper.deleteById(id);
    }

    // ---------------------------------------------------------------- 公共方法

    private <T> Page<T> page(CollabQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        return new Page<>(Math.max(query.getCurrent(), 1L), size, false);
    }

    private LambdaQueryWrapper<MeetingRoomEntity> roomFilter(CollabQuery query) {
        return new LambdaQueryWrapper<MeetingRoomEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(MeetingRoomEntity::getName, query.getKeyword())
                        .or().like(MeetingRoomEntity::getLocation, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), MeetingRoomEntity::getStatus, query.getStatus())
                .orderByDesc(MeetingRoomEntity::getId);
    }

    private LambdaQueryWrapper<MeetingEntity> meetingFilter(CollabQuery query) {
        LambdaQueryWrapper<MeetingEntity> wrapper = new LambdaQueryWrapper<MeetingEntity>()
                .and(StringUtils.hasText(query.getKeyword()), item -> item
                        .like(MeetingEntity::getCode, query.getKeyword())
                        .or().like(MeetingEntity::getTitle, query.getKeyword()))
                .eq(query.getRoomId() != null, MeetingEntity::getRoomId, query.getRoomId())
                .eq(StringUtils.hasText(query.getStatus()), MeetingEntity::getStatus, query.getStatus())
                .ge(query.getDateFrom() != null, MeetingEntity::getStartTime,
                        query.getDateFrom() == null ? null : query.getDateFrom().atStartOfDay())
                .le(query.getDateTo() != null, MeetingEntity::getStartTime,
                        query.getDateTo() == null ? null : query.getDateTo().atTime(23, 59, 59));
        if (query.getParticipantId() != null) {
            List<Long> meetingIds = participantMapper.selectList(new LambdaQueryWrapper<MeetingParticipantEntity>()
                            .eq(MeetingParticipantEntity::getUserId, query.getParticipantId()))
                    .stream().map(MeetingParticipantEntity::getMeetingId).toList();
            if (meetingIds.isEmpty()) {
                wrapper.eq(MeetingEntity::getId, -1L);
            } else {
                wrapper.in(MeetingEntity::getId, meetingIds);
            }
        }
        return wrapper.orderByDesc(MeetingEntity::getStartTime)
                .orderByDesc(MeetingEntity::getId);
    }

    private LambdaQueryWrapper<NoticeEntity> noticeFilter(CollabQuery query) {
        return new LambdaQueryWrapper<NoticeEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(NoticeEntity::getTitle, query.getKeyword())
                        .or().like(NoticeEntity::getContent, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), NoticeEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getScopeType()), NoticeEntity::getScopeType, query.getScopeType())
                .ge(query.getDateFrom() != null, NoticeEntity::getPublishTime,
                        query.getDateFrom() == null ? null : query.getDateFrom().atStartOfDay())
                .le(query.getDateTo() != null, NoticeEntity::getPublishTime,
                        query.getDateTo() == null ? null : query.getDateTo().atTime(23, 59, 59))
                .orderByDesc(NoticeEntity::getId);
    }

    private LambdaQueryWrapper<OaTaskEntity> taskFilter(CollabQuery query) {
        return new LambdaQueryWrapper<OaTaskEntity>()
                .like(StringUtils.hasText(query.getKeyword()), OaTaskEntity::getTitle, query.getKeyword())
                .eq(query.getOwnerId() != null, OaTaskEntity::getOwnerId, query.getOwnerId())
                .eq(query.getUserId() != null, OaTaskEntity::getOwnerId, query.getUserId())
                .eq(StringUtils.hasText(query.getStatus()), OaTaskEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getPriority()), OaTaskEntity::getPriority, query.getPriority())
                .eq(StringUtils.hasText(query.getSource()), OaTaskEntity::getSource, query.getSource())
                .ge(query.getDateFrom() != null, OaTaskEntity::getDueDate, query.getDateFrom())
                .le(query.getDateTo() != null, OaTaskEntity::getDueDate, query.getDateTo())
                .orderByAsc(OaTaskEntity::getStatus)
                .orderByAsc(OaTaskEntity::getDueDate)
                .orderByDesc(OaTaskEntity::getId);
    }

    private static MeetingRoom toRoom(MeetingRoomEntity entity) {
        MeetingRoom model = new MeetingRoom();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setLocation(entity.getLocation());
        model.setCapacity(entity.getCapacity());
        model.setStatus(entity.getStatus());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(MeetingRoomEntity entity, MeetingRoom model) {
        entity.setName(model.getName());
        entity.setLocation(model.getLocation());
        entity.setCapacity(model.getCapacity());
        entity.setStatus(model.getStatus());
        entity.setRemark(model.getRemark());
    }

    private static Meeting toMeeting(MeetingEntity entity) {
        Meeting model = new Meeting();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setTitle(entity.getTitle());
        model.setRoomId(entity.getRoomId());
        model.setOrganizer(entity.getOrganizer());
        model.setOrganizerId(entity.getOrganizerId());
        model.setDeptId(entity.getDeptId());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setAgenda(entity.getAgenda());
        model.setStatus(entity.getStatus());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(MeetingEntity entity, Meeting model) {
        entity.setCode(model.getCode());
        entity.setTitle(model.getTitle());
        entity.setRoomId(model.getRoomId());
        entity.setOrganizer(model.getOrganizer());
        entity.setOrganizerId(model.getOrganizerId());
        entity.setDeptId(model.getDeptId());
        entity.setStartTime(model.getStartTime());
        entity.setEndTime(model.getEndTime());
        entity.setAgenda(model.getAgenda());
        entity.setStatus(model.getStatus());
        entity.setRemark(model.getRemark());
    }

    private static MeetingParticipant toParticipant(MeetingParticipantEntity entity) {
        MeetingParticipant model = new MeetingParticipant();
        model.setId(entity.getId());
        model.setMeetingId(entity.getMeetingId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setRequired(entity.getRequired());
        model.setStatus(entity.getStatus());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(MeetingParticipantEntity entity, MeetingParticipant model) {
        entity.setMeetingId(model.getMeetingId());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setRequired(model.getRequired());
        entity.setStatus(model.getStatus());
        entity.setRemark(model.getRemark());
    }

    private static MeetingMinute toMinute(MeetingMinuteEntity entity) {
        MeetingMinute model = new MeetingMinute();
        model.setId(entity.getId());
        model.setMeetingId(entity.getMeetingId());
        model.setContent(entity.getContent());
        model.setAuthor(entity.getAuthor());
        model.setAuthorId(entity.getAuthorId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(MeetingMinuteEntity entity, MeetingMinute model) {
        entity.setMeetingId(model.getMeetingId());
        entity.setContent(model.getContent());
        entity.setAuthor(model.getAuthor());
        entity.setAuthorId(model.getAuthorId());
    }

    private static MeetingAction toAction(MeetingActionEntity entity) {
        MeetingAction model = new MeetingAction();
        model.setId(entity.getId());
        model.setMeetingId(entity.getMeetingId());
        model.setContent(entity.getContent());
        model.setOwnerId(entity.getOwnerId());
        model.setOwnerName(entity.getOwnerName());
        model.setDueDate(entity.getDueDate());
        model.setStatus(entity.getStatus());
        model.setTaskId(entity.getTaskId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(MeetingActionEntity entity, MeetingAction model) {
        entity.setMeetingId(model.getMeetingId());
        entity.setContent(model.getContent());
        entity.setOwnerId(model.getOwnerId());
        entity.setOwnerName(model.getOwnerName());
        entity.setDueDate(model.getDueDate());
        entity.setStatus(model.getStatus());
        entity.setTaskId(model.getTaskId());
    }

    private static Notice toNotice(NoticeEntity entity) {
        Notice model = new Notice();
        model.setId(entity.getId());
        model.setTitle(entity.getTitle());
        model.setContent(entity.getContent());
        model.setScopeType(entity.getScopeType());
        model.setScopeValue(entity.getScopeValue());
        model.setStatus(entity.getStatus());
        model.setPublisher(entity.getPublisher());
        model.setPublisherId(entity.getPublisherId());
        model.setPublishTime(entity.getPublishTime());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(NoticeEntity entity, Notice model) {
        entity.setTitle(model.getTitle());
        entity.setContent(model.getContent());
        entity.setScopeType(model.getScopeType());
        entity.setScopeValue(model.getScopeValue());
        entity.setStatus(model.getStatus());
        entity.setPublisher(model.getPublisher());
        entity.setPublisherId(model.getPublisherId());
        entity.setPublishTime(model.getPublishTime());
        entity.setRemark(model.getRemark());
    }

    private static NoticeRecipient toRecipient(NoticeRecipientEntity entity) {
        NoticeRecipient model = new NoticeRecipient();
        model.setId(entity.getId());
        model.setNoticeId(entity.getNoticeId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setReadFlag(entity.getReadFlag());
        model.setReadTime(entity.getReadTime());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(NoticeRecipientEntity entity, NoticeRecipient model) {
        entity.setNoticeId(model.getNoticeId());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setReadFlag(model.getReadFlag());
        entity.setReadTime(model.getReadTime());
    }

    private static OaTask toTask(OaTaskEntity entity) {
        OaTask model = new OaTask();
        model.setId(entity.getId());
        model.setTitle(entity.getTitle());
        model.setDescription(entity.getDescription());
        model.setOwnerId(entity.getOwnerId());
        model.setOwnerName(entity.getOwnerName());
        model.setCreatorId(entity.getCreatorId());
        model.setCreatorName(entity.getCreatorName());
        model.setPriority(entity.getPriority());
        model.setStatus(entity.getStatus());
        model.setDueDate(entity.getDueDate());
        model.setSource(entity.getSource());
        model.setSourceId(entity.getSourceId());
        model.setFinishTime(entity.getFinishTime());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    private static void fill(OaTaskEntity entity, OaTask model) {
        entity.setTitle(model.getTitle());
        entity.setDescription(model.getDescription());
        entity.setOwnerId(model.getOwnerId());
        entity.setOwnerName(model.getOwnerName());
        entity.setCreatorId(model.getCreatorId());
        entity.setCreatorName(model.getCreatorName());
        entity.setPriority(model.getPriority());
        entity.setStatus(model.getStatus());
        entity.setDueDate(model.getDueDate());
        entity.setSource(model.getSource());
        entity.setSourceId(model.getSourceId());
        entity.setFinishTime(model.getFinishTime());
    }

    private static TaskComment toComment(TaskCommentEntity entity) {
        TaskComment model = new TaskComment();
        model.setId(entity.getId());
        model.setTaskId(entity.getTaskId());
        model.setUserId(entity.getUserId());
        model.setUserName(entity.getUserName());
        model.setContent(entity.getContent());
        model.setCreateTime(entity.getCreateTime());
        return model;
    }

    private static void fill(TaskCommentEntity entity, TaskComment model) {
        entity.setTaskId(model.getTaskId());
        entity.setUserId(model.getUserId());
        entity.setUserName(model.getUserName());
        entity.setContent(model.getContent());
    }
}
