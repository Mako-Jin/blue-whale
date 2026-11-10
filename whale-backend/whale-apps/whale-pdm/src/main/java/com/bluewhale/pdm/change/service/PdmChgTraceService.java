package com.bluewhale.pdm.change.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.change.dto.PdmChgTraceQueryRequest;
import com.bluewhale.pdm.change.vo.PdmChgTraceVO;

import java.util.List;

/**
 * 变更追溯服务
 *
 * <p>只提供查询；追溯记录由业务动作统一写入，不允许编辑或删除。</p>
 *
 * @author BlueWhale
 */
public interface PdmChgTraceService {

    /**
     * 分页查询追溯。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmChgTraceVO> page(PdmChgTraceQueryRequest request);

    /**
     * 按对象查询时间线。
     *
     * @param objectType 对象类型
     * @param objectId   对象ID
     * @return 按时间升序的追溯
     */
    List<PdmChgTraceVO> timeline(String objectType, Long objectId);
}
