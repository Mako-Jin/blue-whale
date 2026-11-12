package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.RoleVO;
import com.bluewhale.auth.interfaces.dto.response.RoleResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 角色接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface RoleAssembler {

    /**
     * 角色视图对象 → 角色响应
     *
     * @param vo 角色视图对象
     * @return 角色响应
     */
    RoleResponse toResponse(RoleVO vo);

    /**
     * 角色分页 → 角色响应分页
     *
     * @param page 角色分页
     * @return 角色响应分页
     */
    default PageResult<RoleResponse> toPage(PageResult<RoleVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
