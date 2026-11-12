package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.ReportCreateRequest;
import com.bluewhale.forum.dto.ReportHandleRequest;
import com.bluewhale.forum.dto.ReportVO;

/**
 * 举报工单服务（鸣鼓）。
 *
 * <p>两条纪律：</p>
 * <ul>
 *   <li><b>受理不改帖状态</b>：举报是工单，处置是另一条链路。混在一起会出现
 *       「被大量举报但内容没问题」的帖子被误删。</li>
 *   <li><b>驳回不通知被举报人</b>：只有举报人知道「不成立」，否则等于给被举报人
 *       递了一份「有人在盯你」的名单，会诱发报复性举报。</li>
 * </ul>
 *
 * @author BlueWhale
 */
public interface ReportService {

    /**
     * 提交举报（同人同目标在去重窗口内返回既有工单）。
     *
     * @param request 举报请求
     * @param actor 举报人
     * @return 工单ID
     */
    Long submit(ReportCreateRequest request, ForumActor actor);

    /**
     * 工单分页。
     *
     * @param status 状态过滤（可空表示全部）
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作者
     * @return 分页结果
     */
    PageResult<ReportVO> page(String status, Long current, Long size, ForumActor actor);

    /**
     * 处理工单（结案 + 可选处置）。
     *
     * @param id 工单ID
     * @param request 处理请求
     * @param actor 操作者
     */
    void handle(Long id, ReportHandleRequest request, ForumActor actor);
}
