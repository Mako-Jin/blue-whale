package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.UserDirectoryAppService;
import com.bluewhale.auth.interfaces.api.UserInternalApi;
import com.bluewhale.auth.interfaces.dto.request.UserExistsRequest;
import com.bluewhale.auth.interfaces.dto.response.UserDirectoryResponse;
import com.bluewhale.auth.starter.assemble.UserAssembler;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户目录内网接口实现（Web 适配层）：仅供服务间内网直连调用。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserInternalController implements UserInternalApi {

    private final UserDirectoryAppService userDirectoryAppService;

    private final UserAssembler userAssembler;

    /**
     * 批量校验用户是否存在且启用
     *
     * @param request 待校验用户 ID 列表
     * @return 存在且启用的用户 ID 列表
     */
    @Override
    public Result<List<Long>> exists(@Valid @RequestBody UserExistsRequest request) {
        return Result.ok(userDirectoryAppService.findEnabledIds(request.getUserIds()));
    }

    /**
     * 分页查询内网通讯录（仅启用用户，手机号/邮箱脱敏）
     *
     * @param deptId 部门 ID（可空，为空时不过滤）
     * @param includeChildren 是否连同子部门一起查，默认 {@code false}
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @param current 当前页码，默认 1
     * @param size 每页条数，默认 20，上限 200
     * @return 通讯录分页结果
     */
    @Override
    public Result<PageResult<UserDirectoryResponse>> directory(
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "false") boolean includeChildren,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(userAssembler.toDirectoryPage(
                userDirectoryAppService.directory(deptId, includeChildren, keyword, current, size)));
    }

    /**
     * 按角色编码查询内网通讯录（仅启用用户）
     *
     * @param roleCode 角色编码
     * @return 通讯录用户列表；角色不存在时返回空列表
     */
    @Override
    public Result<List<UserDirectoryResponse>> byRole(@RequestParam String roleCode) {
        return Result.ok(userAssembler.toDirectoryResponses(
                userDirectoryAppService.byRole(roleCode)));
    }
}
