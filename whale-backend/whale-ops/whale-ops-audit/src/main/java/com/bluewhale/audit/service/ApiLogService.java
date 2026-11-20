package com.bluewhale.audit.service;

import com.bluewhale.audit.dto.ApiLogQueryRequest;
import com.bluewhale.audit.vo.ApiLogVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 后端接口日志服务：分页查询（REQ-SEC-006 通道①）
 *
 * @author BlueWhale
 */
public interface ApiLogService {

    /**
     * 分页条件查询（单页条数收敛 [1,200]，按 create_time 倒序）
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<ApiLogVO> page(ApiLogQueryRequest request);
}
