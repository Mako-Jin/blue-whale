package com.bluewhale.mes.quality.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.quality.dto.MesReworkActionRequest;
import com.bluewhale.mes.quality.dto.MesReworkQueryRequest;
import com.bluewhale.mes.quality.vo.MesReworkOrderVO;

/**
 * MES 返工单应用服务
 *
 * <p>状态机：{@code CREATED → IN_PROGRESS → COMPLETED}。返工单由返工处置执行时自动生成。</p>
 *
 * @author BlueWhale
 */
public interface MesReworkService {

    /**
     * 分页查询返工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesReworkOrderVO> page(MesReworkQueryRequest request);

    /**
     * 返工开工
     *
     * @param id      返工单 ID
     * @param request 动作请求
     * @return 返工单视图
     */
    MesReworkOrderVO start(Long id, MesReworkActionRequest request);

    /**
     * 返工完工
     *
     * @param id      返工单 ID
     * @param request 动作请求
     * @return 返工单视图
     */
    MesReworkOrderVO complete(Long id, MesReworkActionRequest request);
}
