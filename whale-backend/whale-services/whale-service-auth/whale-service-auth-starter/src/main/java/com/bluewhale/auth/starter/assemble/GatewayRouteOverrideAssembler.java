package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.GatewayRouteOverrideVO;
import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideItemResponse;
import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 网关路由覆盖接口装配器：view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface GatewayRouteOverrideAssembler {

    /**
     * 路由覆盖视图对象 → 下发项响应（仅取网关匹配所需字段）
     *
     * @param vo 路由覆盖视图对象
     * @return 下发项响应
     */
    GatewayRouteOverrideItemResponse toItemResponse(GatewayRouteOverrideVO vo);

    /**
     * 路由覆盖视图对象列表 → 下发项响应列表
     *
     * @param vos 路由覆盖视图对象列表
     * @return 下发项响应列表
     */
    List<GatewayRouteOverrideItemResponse> toItemResponses(List<GatewayRouteOverrideVO> vos);

    /**
     * 路由覆盖视图对象 → 管理端响应
     *
     * @param vo 路由覆盖视图对象
     * @return 管理端响应
     */
    GatewayRouteOverrideResponse toResponse(GatewayRouteOverrideVO vo);

    /**
     * 路由覆盖视图对象列表 → 管理端响应列表
     *
     * @param vos 路由覆盖视图对象列表
     * @return 管理端响应列表
     */
    List<GatewayRouteOverrideResponse> toResponses(List<GatewayRouteOverrideVO> vos);
}
