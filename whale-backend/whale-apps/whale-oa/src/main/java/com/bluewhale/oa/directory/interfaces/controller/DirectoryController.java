package com.bluewhale.oa.directory.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.directory.application.service.DirectoryAppService;
import com.bluewhale.oa.directory.application.view.DirectoryViews;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 通讯录接口。
 *
 * <p>服务内路径 {@code /oa/directory/**}，受 {@code sys:oa:directory:view} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/directory", version = "1")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryAppService directoryAppService;

    /**
     * 分页查询通讯录用户。
     *
     * @param keyword 关键字
     * @param deptId 部门ID
     * @param current 当前页
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping("/users")
    @RequirePermission("sys:oa:directory:view")
    public Result<PageResult<DirectoryViews.UserView>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(directoryAppService.pageUsers(keyword, deptId, current, size));
    }

    /**
     * 查询组织架构树。
     *
     * @return 部门树
     */
    @GetMapping("/depts")
    @RequirePermission("sys:oa:directory:view")
    public Result<List<DirectoryViews.DeptView>> depts() {
        return Result.ok(directoryAppService.deptTree());
    }
}
