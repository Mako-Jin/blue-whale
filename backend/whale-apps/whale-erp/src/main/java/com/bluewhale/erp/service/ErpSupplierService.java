package com.bluewhale.erp.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.dto.ErpSupplierQueryRequest;
import com.bluewhale.erp.dto.ErpSupplierSaveRequest;
import com.bluewhale.erp.vo.ErpSupplierVO;

/**
 * 供应商主数据服务。
 *
 * @author BlueWhale
 */
public interface ErpSupplierService {

    /**
     * 分页查询供应商。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<ErpSupplierVO> page(ErpSupplierQueryRequest request);

    /**
     * 查询供应商详情。
     *
     * @param id 主键ID
     * @return 供应商详情
     */
    ErpSupplierVO getDetail(Long id);

    /**
     * 新增供应商。
     *
     * @param request 新增请求
     * @return 新增后的供应商
     */
    ErpSupplierVO create(ErpSupplierSaveRequest request);

    /**
     * 更新供应商。
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的供应商
     */
    ErpSupplierVO update(Long id, ErpSupplierSaveRequest request);

    /**
     * 删除供应商（逻辑删除）。
     *
     * @param id 主键ID
     */
    void delete(Long id);
}
