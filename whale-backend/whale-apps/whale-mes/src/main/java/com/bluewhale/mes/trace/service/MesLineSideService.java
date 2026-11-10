package com.bluewhale.mes.trace.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.trace.dto.MesLineSideMovementQueryRequest;
import com.bluewhale.mes.trace.dto.MesLineSideOperationRequest;
import com.bluewhale.mes.trace.dto.MesLineSideStockQueryRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentQueryRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentRequest;
import com.bluewhale.mes.trace.vo.MesLineSideMovementVO;
import com.bluewhale.mes.trace.vo.MesLineSideStockVO;
import com.bluewhale.mes.trace.vo.MesReplenishmentVO;

/**
 * MES 线边仓应用服务
 *
 * <p>线边库存查询、入库/领用（库存非负）、流水分页与叫料补货（按请求号幂等）。</p>
 *
 * @author BlueWhale
 */
public interface MesLineSideService {

    /**
     * 分页查询线边库存
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesLineSideStockVO> pageStock(MesLineSideStockQueryRequest request);

    /**
     * 线边入库 / 退料退回
     *
     * @param request 入库请求
     * @return 流水号
     */
    String inbound(MesLineSideOperationRequest request);

    /**
     * 线边领用扣减
     *
     * @param request 领用请求
     * @return 流水号
     */
    String issue(MesLineSideOperationRequest request);

    /**
     * 分页查询线边流水
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesLineSideMovementVO> pageMovements(MesLineSideMovementQueryRequest request);

    /**
     * 叫料补货请求（相同请求号幂等）
     *
     * @param request 补货请求
     * @return 补货请求视图
     */
    MesReplenishmentVO replenish(MesReplenishmentRequest request);

    /**
     * 分页查询补货请求
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesReplenishmentVO> pageReplenishments(MesReplenishmentQueryRequest request);
}
