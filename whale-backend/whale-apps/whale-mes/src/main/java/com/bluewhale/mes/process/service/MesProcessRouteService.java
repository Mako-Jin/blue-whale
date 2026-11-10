package com.bluewhale.mes.process.service;

import com.bluewhale.mes.process.dto.MesProcessRouteReceiveRequest;
import com.bluewhale.mes.process.dto.MesProcessRouteWithdrawRequest;
import com.bluewhale.mes.process.vo.MesProcessRouteVO;

/**
 * MES 工艺路线接收服务
 *
 * @author BlueWhale
 */
public interface MesProcessRouteService {

    /**
     * 接收 PDM 下发的工艺路线（按 {@code routeNo + versionNo} 幂等）
     *
     * @param request 下发内容
     * @return 接收结果
     */
    MesProcessRouteVO receive(MesProcessRouteReceiveRequest request);

    /**
     * 撤回工艺路线（幂等，未接收过也记录撤回墓碑）
     *
     * @param request 撤回请求
     * @return 接收结果
     */
    MesProcessRouteVO withdraw(MesProcessRouteWithdrawRequest request);
}
