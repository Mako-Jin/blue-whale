package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;
import com.bluewhale.auth.interfaces.dto.response.GatewayWhitelistItemResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 网关白名单接口装配器：view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface GatewayWhitelistAssembler {

    /**
     * 白名单视图对象 → 白名单项响应
     *
     * @param vo 白名单视图对象
     * @return 白名单项响应
     */
    GatewayWhitelistItemResponse toResponse(GatewayWhitelistVO vo);

    /**
     * 白名单视图对象列表 → 白名单项响应列表
     *
     * @param vos 白名单视图对象列表
     * @return 白名单项响应列表
     */
    List<GatewayWhitelistItemResponse> toResponses(List<GatewayWhitelistVO> vos);
}
