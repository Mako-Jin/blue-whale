package com.bluewhale.oa.collab.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;

import java.util.List;

/**
 * 会议应用服务。
 *
 * @author BlueWhale
 */
public interface MeetingAppService {

    /**
     * 会议室分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CollabViews.RoomView> pageRooms(CollabQuery query);

    /**
     * 会议室下拉列表（默认仅启用）。
     *
     * @param query 查询条件
     * @return 会议室列表
     */
    List<CollabViews.RoomView> listRooms(CollabQuery query);

    /**
     * 新增会议室。
     *
     * @param command 保存命令
     * @return 会议室视图
     */
    CollabViews.RoomView createRoom(CollabCommands.RoomSaveCommand command);

    /**
     * 编辑会议室。
     *
     * @param id 会议室ID
     * @param command 保存命令
     * @return 会议室视图
     */
    CollabViews.RoomView updateRoom(Long id, CollabCommands.RoomSaveCommand command);

    /**
     * 删除会议室。
     *
     * @param id 会议室ID
     */
    void deleteRoom(Long id);

    /**
     * 会议分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CollabViews.MeetingView> pageMeetings(CollabQuery query);

    /**
     * 会议详情（含参会人、纪要、行动项）。
     *
     * @param id 会议ID
     * @return 会议详情
     */
    CollabViews.MeetingDetailView meetingDetail(Long id);

    /**
     * 创建会议。
     *
     * @param command 保存命令
     * @return 会议视图
     */
    CollabViews.MeetingDetailView createMeeting(CollabCommands.MeetingSaveCommand command);

    /**
     * 编辑会议。
     *
     * @param id 会议ID
     * @param command 保存命令
     * @return 会议详情
     */
    CollabViews.MeetingDetailView updateMeeting(Long id, CollabCommands.MeetingSaveCommand command);

    /**
     * 变更会议状态。
     *
     * @param id 会议ID
     * @param status 目标状态
     * @return 是否有变更
     */
    boolean changeMeetingStatus(Long id, String status);

    /**
     * 变更参会人状态。
     *
     * @param id 会议ID
     * @param userId 参会人用户ID
     * @param status 目标状态
     * @param remark 备注
     * @return 参会人视图
     */
    CollabViews.ParticipantView changeParticipantStatus(Long id, Long userId, String status, String remark);

    /**
     * 保存会议纪要（含行动项，可自动生成个人任务）。
     *
     * @param id 会议ID
     * @param command 纪要命令
     * @return 会议详情
     */
    CollabViews.MeetingDetailView saveMinute(Long id, CollabCommands.MinuteSaveCommand command);
}
