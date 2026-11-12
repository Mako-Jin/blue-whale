package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.UserLifecycleVO;
import com.bluewhale.auth.application.model.vo.ResetPasswordVO;
import com.bluewhale.auth.application.model.vo.UserAttrVO;
import com.bluewhale.auth.application.model.vo.UserDirectoryVO;
import com.bluewhale.auth.application.model.vo.UserVO;
import com.bluewhale.auth.interfaces.dto.request.UserLifecycleRequest;
import com.bluewhale.auth.interfaces.dto.response.ResetPasswordResponse;
import com.bluewhale.auth.interfaces.dto.response.UserAttrResponse;
import com.bluewhale.auth.interfaces.dto.response.UserDirectoryResponse;
import com.bluewhale.auth.interfaces.dto.response.UserLifecycleResponse;
import com.bluewhale.auth.interfaces.dto.response.UserResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

import java.util.List;
import com.bluewhale.auth.application.model.command.UserLifecycleCommand;

/**
 * 用户接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface UserAssembler {

    /**
     * 用户视图对象 → 用户响应
     *
     * @param vo 用户视图对象
     * @return 用户响应
     */
    UserResponse toResponse(UserVO vo);

    /**
     * 用户分页 → 用户响应分页
     *
     * @param page 用户分页
     * @return 用户响应分页
     */
    default PageResult<UserResponse> toPage(PageResult<UserVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 重置口令结果 → 重置口令响应
     *
     * @param vo 重置口令结果
     * @return 重置口令响应
     */
    ResetPasswordResponse toResetPasswordResponse(ResetPasswordVO vo);

    /**
     * 用户属性视图对象 → 用户属性响应
     *
     * @param vo 用户属性视图对象
     * @return 用户属性响应
     */
    UserAttrResponse toAttrResponse(UserAttrVO vo);

    /**
     * 用户属性视图对象列表 → 用户属性响应列表
     *
     * @param vos 用户属性视图对象列表
     * @return 用户属性响应列表
     */
    List<UserAttrResponse> toAttrResponses(List<UserAttrVO> vos);

    /**
     * 通讯录视图对象 → 通讯录响应
     *
     * @param vo 通讯录视图对象
     * @return 通讯录响应
     */
    UserDirectoryResponse toDirectoryResponse(UserDirectoryVO vo);

    /**
     * 通讯录视图对象列表 → 通讯录响应列表
     *
     * @param vos 通讯录视图对象列表
     * @return 通讯录响应列表
     */
    List<UserDirectoryResponse> toDirectoryResponses(List<UserDirectoryVO> vos);

    /**
     * 通讯录分页 → 通讯录响应分页
     *
     * @param page 通讯录分页
     * @return 通讯录响应分页
     */
    default PageResult<UserDirectoryResponse> toDirectoryPage(PageResult<UserDirectoryVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toDirectoryResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 生命周期结果 → 生命周期响应
     *
     * @param vo 生命周期结果
     * @return 生命周期响应
     */
    UserLifecycleResponse toLifecycleResponse(UserLifecycleVO vo);

    /**
     * 生命周期请求（interfaces DTO）→ 应用层命令
     *
     * @param request 生命周期请求
     * @return 生命周期命令
     */
    com.bluewhale.auth.application.model.command.UserLifecycleCommand toLifecycleCommand(
            UserLifecycleRequest request);
}
