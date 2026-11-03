package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.CreatorQueryCommand;
import com.bluewhale.video.application.command.CreatorUpdateCommand;
import com.bluewhale.video.application.service.CreatorAppService;
import com.bluewhale.video.application.view.CreatorAdminView;
import com.bluewhale.video.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作者运营接口：分页与资料 / 状态维护。
 *
 * <p>停用创作者后不得发布新内容；头像通过 {@code /video/creator/{id}/avatar}
 * 代理访问，运营视图不暴露头像文件 ID。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video/admin/creator")
@RequiredArgsConstructor
public class VideoAdminCreatorController {

    private final CreatorAppService creatorAppService;

    /**
     * 创作者分页（昵称 / 状态筛选）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @return 创作者分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:video:creator:manage")
    public Result<PageResult<CreatorAdminView>> page(
            @ModelAttribute CreatorQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(creatorAppService.adminPage(command));
    }

    /**
     * 编辑创作者资料与状态。
     *
     * @param id 创作者 ID
     * @param command 编辑命令
     * @return 创作者运营视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:video:creator:manage")
    @AuditLog(operation = "编辑视频创作者", type = OperateType.UPDATE,
            targetType = "vd_creator", targetId = "#id")
    public Result<CreatorAdminView> update(@PathVariable Long id,
                                           @Valid @RequestBody CreatorUpdateCommand command) {
        return Result.ok(creatorAppService.update(id, command));
    }
}
