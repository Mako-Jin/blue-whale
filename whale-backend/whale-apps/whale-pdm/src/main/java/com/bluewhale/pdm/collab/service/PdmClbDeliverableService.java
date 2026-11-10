package com.bluewhale.pdm.collab.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableQueryRequest;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableSaveRequest;
import com.bluewhale.pdm.collab.vo.PdmClbDeliverableVO;

/**
 * 项目交付物服务
 *
 * @author BlueWhale
 */
public interface PdmClbDeliverableService {

    /**
     * 分页查询交付物
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmClbDeliverableVO> page(PdmClbDeliverableQueryRequest request);

    /**
     * 归档已发布对象版本
     *
     * @param request 归档请求
     * @param userId  当前用户
     * @return 交付物视图
     */
    PdmClbDeliverableVO archive(PdmClbDeliverableSaveRequest request, Long userId);

    /**
     * 取消归档
     *
     * @param id 交付物 ID
     */
    void delete(Long id);
}
