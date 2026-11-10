package com.bluewhale.audit.service;

import com.bluewhale.audit.dto.FrontendLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.vo.FrontendLogVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 前端开发者日志服务：接收落库 + 分页查询（REQ-SEC-006）
 *
 * @author BlueWhale
 */
public interface FrontendLogService {

    /**
     * 接收前端 console 日志批量上报并异步落库
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
    PageResult<FrontendLogVO> page(FrontendLogQueryRequest request);
}
