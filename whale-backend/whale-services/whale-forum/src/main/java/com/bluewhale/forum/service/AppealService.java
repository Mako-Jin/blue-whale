package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.AppealCreateRequest;
import com.bluewhale.forum.dto.AppealHandleRequest;
import com.bluewhale.forum.dto.AppealVO;

/**
 * 申诉服务（F-09-08）。
 *
 * <p>只受理「已被处置」的对象：内容申诉限作者本人，禁言申诉限本人且当前生效。
 * 复核通过不自动恢复内容，仅禁言申诉通过时自动解禁。</p>
 *
 * @author BlueWhale
 */
public interface AppealService {

    /** 申诉帖子 */
    String TARGET_THREAD = "THREAD";

    /** 申诉楼层 */
    String TARGET_POST = "POST";

    /** 申诉禁言 */
    String TARGET_BAN = "BAN";

    /** 待审 */
    String STATUS_PENDING = "PENDING";

    /** 通过 */
    String STATUS_APPROVED = "APPROVED";

    /** 驳回 */
    String STATUS_REJECTED = "REJECTED";

    /**
     * 提交申诉。
     *
     * @param body 申诉内容
     * @param actor 当前操作者
     * @return 工单
     */
    AppealVO submit(AppealCreateRequest body, ForumActor actor);

    /**
     * 我的申诉（按提交时间倒序）。
     *
     * @param current 页码
     * @param size 每页条数
     * @param actor 当前操作者
     * @return 分页
     */
    PageResult<AppealVO> myAppeals(long current, long size, ForumActor actor);

    /**
     * 管理端工单列表。
     *
     * @param status 可选状态过滤
     * @param current 页码
     * @param size 每页条数
     * @return 分页
     */
    PageResult<AppealVO> page(String status, long current, long size);

    /**
     * 复核申诉。
     *
     * @param appealId 工单ID
     * @param body 复核结论
     * @param actor 当前操作者
     * @return 更新后的工单
     */
    AppealVO handle(Long appealId, AppealHandleRequest body, ForumActor actor);
}
