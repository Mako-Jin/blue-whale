package com.bluewhale.erp.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.dto.ErpInternalItemUpsertRequest;
import com.bluewhale.erp.dto.ErpItemQueryRequest;
import com.bluewhale.erp.dto.ErpItemSaveRequest;
import com.bluewhale.erp.vo.ErpInternalItemSyncVO;
import com.bluewhale.erp.vo.ErpItemVO;

/**
 * 物料服务
 *
 * @author BlueWhale
 */
public interface ErpItemService {

    /**
     * 分页查询物料
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<ErpItemVO> page(ErpItemQueryRequest request);

    /**
     * 物料详情
     *
     * @param id 主键ID
     * @return 物料详情
     */
    ErpItemVO getDetail(Long id);

    /**
     * 新增物料
     *
     * @param request 新增请求
     * @return 新增后的物料
     */
    ErpItemVO create(ErpItemSaveRequest request);

    /**
     * 更新物料
     *
     * @param id      主键ID
     * @param request 更新请求
     * @return 更新后的物料
     */
    ErpItemVO update(Long id, ErpItemSaveRequest request);

    /**
     * 删除物料（逻辑删除）
     *
     * @param id 主键ID
     */
    void delete(Long id);

    /**
     * 按 PDM 零件编码幂等写入物料
     *
     * @param request 同步请求
     * @return 同步结果
     */
    ErpInternalItemSyncVO upsertFromPdm(ErpInternalItemUpsertRequest request);
}
