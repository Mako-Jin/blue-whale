package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.command.SaveUserGroupCommand;
import com.bluewhale.auth.application.model.vo.UserGroupVO;
import com.bluewhale.auth.interfaces.dto.request.UserGroupSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.UserGroupResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 用户组接口装配器：request → command，view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface UserGroupAssembler {

    /**
     * 保存请求 → 保存命令（命令 id 置空，更新主键由控制器以路径变量传入）
     *
     * @param request 保存请求
     * @return 保存命令
     */
    SaveUserGroupCommand toCommand(UserGroupSaveRequest request);

    /**
     * 用户组视图对象 → 用户组响应
     *
     * @param vo 用户组视图对象
     * @return 用户组响应
     */
    UserGroupResponse toResponse(UserGroupVO vo);

    /**
     * 用户组分页 → 用户组响应分页
     *
     * @param page 用户组分页
     * @return 用户组响应分页
     */
    default PageResult<UserGroupResponse> toPage(PageResult<UserGroupVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
