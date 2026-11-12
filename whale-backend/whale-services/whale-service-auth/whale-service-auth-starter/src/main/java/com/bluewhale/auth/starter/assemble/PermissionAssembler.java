package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.vo.PermissionVO;
import com.bluewhale.auth.interfaces.dto.response.PermissionResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 权限点接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface PermissionAssembler {

    /**
     * 权限点视图对象 → 权限点响应
     *
     * @param vo 权限点视图对象
     * @return 权限点响应
     */
    PermissionResponse toResponse(PermissionVO vo);

    /**
     * 权限点分页 → 权限点响应分页
     *
     * @param page 权限点分页
     * @return 权限点响应分页
     */
    default PageResult<PermissionResponse> toPage(PageResult<PermissionVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
