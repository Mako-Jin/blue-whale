package com.bluewhale.audit.service;

import com.bluewhale.audit.dto.BehaviorLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.vo.BehaviorLogVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 前端用户行为埋点服务：接收落库 + 分页查询（REQ-SEC-006）
 *
 * @author BlueWhale
 */
public interface BehaviorLogService {

    /**
     * 接收前端行为埋点批量上报并异步落库
     *
     * @param request 批量上报请求（items 已在控制器侧校验上限）
     * @param userIdFromGateway 网关 {@code X-User-Id} 回填的用户ID；缺失时为请求体兜底值，均可为 {@code null}
     */
    void saveBatch(LogBatchRequest request, Long userIdFromGateway);

    /**
     * 分页条件查询（单页条数收敛 [1,200]，按 create_time 倒序）
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<BehaviorLogVO> page(BehaviorLogQueryRequest request);
}
