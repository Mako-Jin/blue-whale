package com.bluewhale.mes.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dto.MesWorkOrderQueryRequest;
import com.bluewhale.mes.dto.MesWorkOrderSaveRequest;
import com.bluewhale.mes.vo.MesWorkOrderVO;

/**
 * 生产工单服务
 *
 * @author BlueWhale
 */
public interface MesWorkOrderService {

    /**
     * 分页查询生产工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesWorkOrderVO> page(MesWorkOrderQueryRequest request);

    /**
     * 生产工单详情
     *
     * @param id 主键ID
     * @return 工单详情
     */
    MesWorkOrderVO getDetail(Long id);

    /**
     * 新增生产工单
     *
     * @param request 新增请求
     * @return 新增后的工单
     */
    MesWorkOrderVO create(MesWorkOrderSaveRequest request);

    /**
     * 更新生产工单
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的工单
     */
    MesWorkOrderVO update(Long id, MesWorkOrderSaveRequest request);

    /**
     * 删除生产工单（逻辑删除）
     *
     * @param id 主键ID
     */
    void delete(Long id);
}
