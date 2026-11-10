package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.user.UserDirectoryAppService;
import com.bluewhale.auth.interfaces.dto.UserExistsRequest;
import com.bluewhale.auth.application.vo.UserDirectoryVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户目录内网接口：供平台内其他服务校验目标用户是否存在且启用、拉取通讯录与按角色查用户。
 *
 * <p>路径固定以 {@code /user/internal} 开头，已列入网关
 * {@code whale.gateway.internal-deny.patterns}，经网关的外部访问一律 403；
 * 调用方须经 Nacos 服务发现直连本服务。本接口不校验业务权限码，
 * 且不返回口令等敏感信息，通讯录中的手机号与邮箱均脱敏。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user/internal")
@RequiredArgsConstructor
public class UserInternalController {

    /** 用户目录应用服务 */
    private final UserDirectoryAppService userDirectoryAppService;

    /**
     * 批量校验用户是否存在且启用
     *
     * @param request 待校验用户 ID 列表
     * @return 存在且启用的用户 ID 列表
     */
    @PostMapping("/exists")
    public Result<List<Long>> exists(@Valid @RequestBody UserExistsRequest request) {
        return Result.ok(userDirectoryAppService.findEnabledIds(request.getUserIds()));
    }

    /**
     * 分页查询内网通讯录（仅启用用户，手机号/邮箱脱敏）
     *
     * @param deptId 部门 ID（可空，为空时不过滤）
     * @param includeChildren 是否连同子部门一起查，默认 {@code false}（精确匹配该部门）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @param current 当前页码，默认 1
     * @param size 每页条数，默认 20，上限 200
     * @return 通讯录分页结果
     */
    @GetMapping("/directory")
    public Result<PageResult<UserDirectoryVO>> directory(
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "false") boolean includeChildren,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(userDirectoryAppService.directory(deptId, includeChildren, keyword, current, size));
    }

    /**
     * 按角色编码查询内网通讯录（仅启用用户）
     *
     * @param roleCode 角色编码
     * @return 通讯录用户列表；角色不存在时返回空列表
     */
    @GetMapping("/by-role")
    public Result<List<UserDirectoryVO>> byRole(@RequestParam String roleCode) {
        return Result.ok(userDirectoryAppService.byRole(roleCode));
    }
}
