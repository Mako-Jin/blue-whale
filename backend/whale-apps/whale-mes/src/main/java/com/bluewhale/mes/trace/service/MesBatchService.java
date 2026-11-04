package com.bluewhale.mes.trace.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.trace.dto.MesBatchQueryRequest;
import com.bluewhale.mes.trace.dto.MesBatchSaveRequest;
import com.bluewhale.mes.trace.dto.MesTraceLinkRequest;
import com.bluewhale.mes.trace.vo.MesBatchVO;
import com.bluewhale.mes.trace.vo.MesTraceNodeVO;

import java.util.List;

/**
 * MES 批次与谱系应用服务
 *
 * <p>批次建档/封批、批次谱系建链（无环、消耗量 ≤ 投入量）、正反向追溯。</p>
 *
 * @author BlueWhale
 */
public interface MesBatchService {

    /**
     * 批次建档
     *
     * @param request 建档请求
     * @return 批次视图
     */
    MesBatchVO create(MesBatchSaveRequest request);

    /**
     * 分页查询批次
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesBatchVO> page(MesBatchQueryRequest request);

    /**
     * 封批（幂等）
     *
     * @param id 批次 ID
     * @return 批次视图
     */
    MesBatchVO close(Long id);

    /**
     * 建立批次谱系链接
     *
     * @param request 建链请求
     * @return 链接 ID
     */
    Long link(MesTraceLinkRequest request);

    /**
     * 正向追溯：从批次出发查下游成品
     *
     * @param batchNo 批次号
     * @param depth   最大层级
     * @return 追溯节点
     */
    List<MesTraceNodeVO> forward(String batchNo, Integer depth);

    /**
     * 反向追溯：从批次出发查上游来料
     *
     * @param batchNo 批次号
     * @param depth   最大层级
     * @return 追溯节点
     */
    List<MesTraceNodeVO> backward(String batchNo, Integer depth);
}
