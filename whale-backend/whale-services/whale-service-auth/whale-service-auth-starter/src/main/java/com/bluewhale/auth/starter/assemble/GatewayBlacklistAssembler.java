package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;
import com.bluewhale.auth.interfaces.dto.response.GatewayBlacklistItemResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 网关黑名单接口装配器：view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface GatewayBlacklistAssembler {

    /**
     * 黑名单视图对象 → 黑名单项响应
     *
     * @param vo 黑名单视图对象
     * @return 黑名单项响应
     */
    GatewayBlacklistItemResponse toResponse(GatewayBlacklistVO vo);

    /**
     * 黑名单视图对象列表 → 黑名单项响应列表
     *
     * @param vos 黑名单视图对象列表
     * @return 黑名单项响应列表
     */
    List<GatewayBlacklistItemResponse> toResponses(List<GatewayBlacklistVO> vos);
}
