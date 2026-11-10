package com.bluewhale.oa.collab.domain.repository;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 协同业务仓储。
 *
 * @author BlueWhale
 */
public interface CollabRepository {

    // ---------------------------------------------------------------- 会议室

    /**
     * 按主键查询会议室。
     *
     * @param id 会议室ID
     * @return 会议室，不存在返回 null
     */
    MeetingRoom findRoom(Long id);

    /**
     * 判断会议室名称是否已存在。
     *
     * @param name 名称
     * @param excludeId 排除的会议室ID
     * @return 是否存在
     */
    boolean roomNameExists(String name, Long excludeId);

    /**
     * 会议室分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<MeetingRoom> pageRooms(CollabQuery query);

    /**
     * 会议室下拉列表。
     *
     * @param query 查询条件
     * @return 会议室列表
     */
    List<MeetingRoom> listRooms(CollabQuery query);

    /**
     * 新增会议室。
     *
     * @param room 会议室
     */
    void saveRoom(MeetingRoom room);

    /**
     * 更新会议室。
     *
     * @param room 会议室
     */
    void updateRoom(MeetingRoom room);

    /**
     * 逻辑删除会议室。
     *
     * @param id 会议室ID
     */
    void deleteRoom(Long id);

    // ------------------------------------------------------------------ 会议

    /**
     * 会议分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Meeting> pageMeetings(CollabQuery query);

    /**
     * 按主键查询会议。
     *
     * @param id 会议ID
     * @return 会议，不存在返回 null
     */
    Meeting findMeeting(Long id);

    /**
     * 判断会议室在时段内是否已有会议。
     *
     * @param roomId 会议室ID
     * @param start 开始时间
     * @param end 结束时间
     * @param excludeId 排除的会议ID
     * @return 是否冲突
     */
    boolean hasRoomConflict(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId);

    /**
     * 新增会议。
     *
     * @param meeting 会议
     */
    void saveMeeting(Meeting meeting);

    /**
     * 更新会议。
     *
     * @param meeting 会议
     */
    void updateMeeting(Meeting meeting);

    /**
     * 查询参会人。
     *
     * @param meetingId 会议ID
     * @return 参会人
     */
    List<MeetingParticipant> listParticipants(Long meetingId);

    /**
     * 批量新增参会人。
     *
     * @param participants 参会人
     */
    void saveParticipants(List<MeetingParticipant> participants);

    /**
     * 更新参会人状态。
     *
     * @param participant 参会人
     */
    void updateParticipant(MeetingParticipant participant);

    /**
     * 删除会议下的参会人（逻辑删除）。
     *
     * @param meetingId 会议ID
     */
    void deleteParticipants(Long meetingId);

    /**
     * 查询会议纪要。
     *
     * @param meetingId 会议ID
     * @return 纪要，不存在返回 null
     */
    MeetingMinute findMinute(Long meetingId);

    /**
     * 新增纪要。
     *
     * @param minute 纪要
     */
    void saveMinute(MeetingMinute minute);

    /**
     * 更新纪要。
     *
     * @param minute 纪要
     */
    void updateMinute(MeetingMinute minute);

    /**
     * 查询行动项。
     *
     * @param meetingId 会议ID
     * @return 行动项
     */
    List<MeetingAction> listActions(Long meetingId);

    /**
     * 批量新增行动项。
     *
     * @param actions 行动项
     */
    void saveActions(List<MeetingAction> actions);

    /**
     * 更新行动项。
     *
     * @param action 行动项
     */
    void updateAction(MeetingAction action);

    /**
     * 删除会议下的行动项（逻辑删除）。
     *
     * @param meetingId 会议ID
     */
    void deleteActions(Long meetingId);

    // ------------------------------------------------------------------ 公告

    /**
     * 公告分页（管理视角）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Notice> pageNotices(CollabQuery query);

    /**
     * 我的公告分页（按接收人）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Notice> pageMyNotices(CollabQuery query);

    /**
     * 按主键查询公告。
     *
     * @param id 公告ID
     * @return 公告，不存在返回 null
     */
    Notice findNotice(Long id);

    /**
     * 新增公告。
     *
     * @param notice 公告
     */
    void saveNotice(Notice notice);

    /**
     * 更新公告。
     *
     * @param notice 公告
     */
    void updateNotice(Notice notice);

    /**
     * 查询接收人快照。
     *
     * @param noticeId 公告ID
     * @return 接收人
     */
    List<NoticeRecipient> listRecipients(Long noticeId);

    /**
     * 查询某人的接收记录。
     *
     * @param noticeId 公告ID
     * @param userId 用户ID
     * @return 接收记录，不存在返回 null
     */
    NoticeRecipient findRecipient(Long noticeId, Long userId);

    /**
     * 批量新增接收人。
     *
     * @param recipients 接收人
     */
    void saveRecipients(List<NoticeRecipient> recipients);

    /**
     * 更新接收记录。
     *
     * @param recipient 接收记录
     */
    void updateRecipient(NoticeRecipient recipient);

    /**
     * 删除公告下的接收人（逻辑删除）。
     *
     * @param noticeId 公告ID
     */
    void deleteRecipients(Long noticeId);

    /**
     * 统计用户未读公告数。
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    long countUnreadNotices(Long userId);

    // ------------------------------------------------------------------ 任务

    /**
     * 任务分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<OaTask> pageTasks(CollabQuery query);

    /**
     * 查询负责人到期任务（{@code TODO/DOING}，按截止日期升序）。
     *
     * @param ownerId 负责人用户ID
     * @param dueTo 截止日期上限（含）
     * @param limit 返回条数上限
     * @return 到期任务
     */
    List<OaTask> listDueTasks(Long ownerId, LocalDate dueTo, int limit);

    /**
     * 按主键查询任务。
     *
     * @param id 任务ID
     * @return 任务，不存在返回 null
     */
    OaTask findTask(Long id);

    /**
     * 新增任务。
     *
     * @param task 任务
     */
    void saveTask(OaTask task);

    /**
     * 更新任务。
     *
     * @param task 任务
     */
    void updateTask(OaTask task);

    /**
     * 逻辑删除任务。
     *
     * @param id 任务ID
     */
    void deleteTask(Long id);

    /**
     * 查询任务评论。
     *
     * @param taskId 任务ID
     * @return 评论
     */
    List<TaskComment> listComments(Long taskId);

    /**
     * 按主键查询评论。
     *
     * @param id 评论ID
     * @return 评论，不存在返回 null
     */
    TaskComment findComment(Long id);

    /**
     * 新增评论。
     *
     * @param comment 评论
     */
    void saveComment(TaskComment comment);

    /**
     * 逻辑删除评论。
     *
     * @param id 评论ID
     */
    void deleteComment(Long id);
}
