package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.auth.command.LoginCommand;
import com.bluewhale.auth.application.auth.command.SwitchTenantCommand;
import com.bluewhale.auth.application.auth.result.CurrentUserResult;
import com.bluewhale.auth.application.auth.result.LoginResult;
import com.bluewhale.auth.application.auth.result.TenantResult;
import com.bluewhale.auth.application.user.password.ChangeOwnPasswordCommand;
import com.bluewhale.auth.application.vo.MenuVO;
import com.bluewhale.auth.interfaces.dto.request.ChangeOwnPasswordRequest;
import com.bluewhale.auth.interfaces.dto.request.LoginRequest;
import com.bluewhale.auth.interfaces.dto.request.SwitchTenantRequest;
import com.bluewhale.auth.interfaces.dto.response.CurrentUserResponse;
import com.bluewhale.auth.interfaces.dto.response.LoginResponse;
import com.bluewhale.auth.interfaces.dto.response.MenuResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 认证接口装配器：负责 interfaces DTO 与 application Command/Query/Result 之间的转换。
 *
 * <p>转换规则由 MapStruct 在编译期生成，运行期无反射开销；嵌套菜单树通过
 * {@link #toMenuResponse(MenuVO)} 递归映射。</p>
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface AuthAssembler {

    /**
     * 登录请求 → 登录命令
     *
     * @param request 登录请求 DTO
     * @return 登录命令
     */
    LoginCommand toCommand(LoginRequest request);

    /**
     * 自助改密请求 → 改密命令
     *
     * @param request 改密请求 DTO
     * @return 改密命令
     */
    ChangeOwnPasswordCommand toCommand(ChangeOwnPasswordRequest request);

    /**
     * 切换租户请求 + 请求上下文 → 切换租户命令
     *
     * @param request 切换租户请求 DTO
     * @param authorization 请求头 Authorization 原始值
     * @param currentRefreshToken Cookie 中的当前 refreshToken
     * @return 切换租户命令
     */
    @Mapping(target = "authorization", source = "authorization")
    @Mapping(target = "targetTenantId", source = "request.tenantId")
    @Mapping(target = "currentRefreshToken", source = "currentRefreshToken")
    SwitchTenantCommand toCommand(SwitchTenantRequest request, String authorization, String currentRefreshToken);

    /**
     * 登录结果 → 登录响应（refreshToken 仅写 Cookie，不进入响应体）
     *
     * @param result 登录结果
     * @return 登录响应 DTO
     */
    LoginResponse toLoginResponse(LoginResult result);

    /**
     * 当前用户结果 → 当前用户响应
     *
     * @param result 当前用户结果
     * @return 当前用户响应 DTO
     */
    CurrentUserResponse toCurrentUserResponse(CurrentUserResult result);

    /**
     * 菜单视图对象 → 菜单响应（children 递归映射）
     *
     * @param menu 菜单视图对象
     * @return 菜单响应 DTO
     */
    MenuResponse toMenuResponse(MenuVO menu);

    /**
     * 租户结果 → 租户响应
     *
     * @param result 租户结果
     * @return 租户响应 DTO
     */
    TenantResponse toTenantResponse(TenantResult result);

    /**
     * 租户结果列表 → 租户响应列表
     *
     * @param results 租户结果列表
     * @return 租户响应列表
     */
    List<TenantResponse> toTenantResponses(List<TenantResult> results);
}
