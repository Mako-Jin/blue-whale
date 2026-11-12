package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.interfaces.dto.response.GatewayAccessRuleResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 网关访问名单管理接口装配器：view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface GatewayAccessRuleAssembler {

    /**
     * 访问名单视图对象 → 响应
     *
     * @param vo 访问名单视图对象
     * @return 访问名单响应
     */
    GatewayAccessRuleResponse toResponse(GatewayAccessRuleVO vo);

    /**
     * 访问名单视图对象列表 → 响应列表
     *
     * @param vos 访问名单视图对象列表
     * @return 访问名单响应列表
     */
    List<GatewayAccessRuleResponse> toResponses(List<GatewayAccessRuleVO> vos);
}
