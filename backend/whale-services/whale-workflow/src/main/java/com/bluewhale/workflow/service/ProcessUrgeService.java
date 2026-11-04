package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.UrgeRequest;
import com.bluewhale.workflow.vo.ProcessUrgeVO;

/**
 * 流程催办服务。
 *
 * <p>M0-4 只做留痕与频控；消息下发与自动催办由 M1A-4 实现。</p>
 *
 * @author BlueWhale
 */
public interface ProcessUrgeService {

    /**
     * 催办：同实例同催办人 1 小时内仅允许一次。
     *
     * @param instanceId 流程实例ID
     * @param request 催办请求
     */
    void urge(Long instanceId, UrgeRequest request);

    /**
     * 分页查询催办记录。
     *
     * @param instanceId 流程实例ID
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<ProcessUrgeVO> page(Long instanceId, long current, long size);
}
