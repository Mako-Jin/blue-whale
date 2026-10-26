package com.bluewhale.pdm.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.dto.PdmProductQueryRequest;
import com.bluewhale.pdm.dto.PdmProductSaveRequest;
import com.bluewhale.pdm.vo.PdmProductVO;

/**
 * 产品服务
 *
 * @author BlueWhale
 */
public interface PdmProductService {

    /**
     * 分页查询产品
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmProductVO> page(PdmProductQueryRequest request);

    /**
     * 产品详情
     *
     * @param id 主键ID
     * @return 产品详情
     */
    PdmProductVO getDetail(Long id);

    /**
     * 新增产品
     *
     * @param request 新增请求
     * @return 新增后的产品
     */
    PdmProductVO create(PdmProductSaveRequest request);

    /**
     * 更新产品
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的产品
     */
    PdmProductVO update(Long id, PdmProductSaveRequest request);

    /**
     * 删除产品（逻辑删除）
     *
     * @param id 主键ID
     */
    void delete(Long id);
}
