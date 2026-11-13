package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.command.SaveRoleConstraintCommand;
import com.bluewhale.auth.application.model.vo.RoleConstraintVO;
import com.bluewhale.auth.interfaces.dto.request.RoleConstraintSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.RoleConstraintResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 角色约束接口装配器：request → command，view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface RoleConstraintAssembler {

    /**
     * 保存请求 → 保存命令（命令 id 置空，更新主键由控制器以路径变量传入）
     *
     * @param request 保存请求
     * @return 保存命令
     */
    SaveRoleConstraintCommand toCommand(RoleConstraintSaveRequest request);

    /**
     * 约束视图对象 → 约束响应
     *
     * @param vo 约束视图对象
     * @return 约束响应
     */
    RoleConstraintResponse toResponse(RoleConstraintVO vo);

    /**
     * 约束分页 → 约束响应分页
     *
     * @param page 约束分页
     * @return 约束响应分页
     */
    default PageResult<RoleConstraintResponse> toPage(PageResult<RoleConstraintVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
