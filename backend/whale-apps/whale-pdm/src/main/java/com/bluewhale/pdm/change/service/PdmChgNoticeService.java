package com.bluewhale.pdm.change.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.dto.PdmChgNoticeQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgNoticeSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgNotice;
import com.bluewhale.pdm.change.vo.PdmChgNoticeVO;
import com.bluewhale.pdm.change.vo.PdmChgNotifyLogVO;

import java.util.List;

/**
 * 变更通知（ECN）服务
 *
 * @author BlueWhale
 */
public interface PdmChgNoticeService {

    /**
     * 分页查询变更通知。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmChgNoticeVO> page(PdmChgNoticeQueryRequest request);

    /**
     * 查询变更通知详情。
     *
     * @param id 通知ID
     * @return 通知视图
     */
    PdmChgNoticeVO detail(Long id);

    /**
     * 创建变更通知。
     *
     * @param request 保存请求
     * @param userId  当前用户
     * @return 通知视图
     */
    PdmChgNoticeVO create(PdmChgNoticeSaveRequest request, Long userId);

    /**
     * 修改草稿变更通知。
     *
     * @param id      通知ID
     * @param request 保存请求
     * @param userId  当前用户
     * @return 通知视图
     */
    PdmChgNoticeVO update(Long id, PdmChgNoticeSaveRequest request, Long userId);

    /**
     * 删除草稿变更通知。
     *
     * @param id 通知ID
     */
    void delete(Long id);

    /**
     * 发送通知（草稿 → 已通知）。
     *
     * @param id     通知ID
     * @param userId 当前用户
     * @return 通知视图
     */
    PdmChgNoticeVO notify(Long id, Long userId);

    /**
     * 生效（已通知 → 已生效），并把旧版本置为作废。
     *
     * @param id     通知ID
     * @param userId 当前用户，定时任务传 null
     * @return 通知视图
     */
    PdmChgNoticeVO effective(Long id, Long userId);

    /**
     * 关闭已生效通知。
     *
     * @param id     通知ID
     * @param userId 当前用户
     * @return 通知视图
     */
    PdmChgNoticeVO close(Long id, Long userId);

    /**
     * 取消未生效通知。
     *
     * @param id     通知ID
     * @param userId 当前用户
     * @return 通知视图
     */
    PdmChgNoticeVO cancel(Long id, Long userId);

    /**
     * 重试最近一次失败的通知投递。
     *
     * @param id     通知ID
     * @param userId 当前用户
     * @return 通知视图
     */
    PdmChgNoticeVO retryNotify(Long id, Long userId);

    /**
     * 分页查询通知投递日志。
     *
     * @param noticeId 通知ID
     * @param current  当前页
     * @param size     每页条数
     * @return 分页结果
     */
    PageResult<PdmChgNotifyLogVO> notifyLogPage(Long noticeId, Long current, Long size);

    /**
     * 查询已到生效日、待生效的通知。
     *
     * @return 通知列表
     */
    List<PdmChgNotice> dueNotices();
}
