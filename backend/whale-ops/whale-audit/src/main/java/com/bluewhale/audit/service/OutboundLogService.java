package com.bluewhale.audit.service;

import com.bluewhale.audit.dto.OutboundLogQueryRequest;
import com.bluewhale.audit.vo.OutboundLogVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 后端出站三方调用日志服务：分页查询（REQ-SEC-006 通道②）
 *
 * @author BlueWhale
 */
public interface OutboundLogService {

    /**
     * 分页条件查询（单页条数收敛 [1,200]，按 create_time 倒序）
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<OutboundLogVO> page(OutboundLogQueryRequest request);
}
