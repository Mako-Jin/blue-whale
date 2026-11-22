package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.BoardSaveRequest;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.service.BoardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 版块接口（自持 {@code /forum} 一级前缀，网关不剥前缀）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}/board", version = "1")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 可见版块列表（平铺，前端按 parentId 组树）。
     *
     * @param request 当前请求
     * @return 版块列表
     */
    @GetMapping("/page")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<List<BoardVO>> page(HttpServletRequest request) {
        return Result.ok(boardService.listVisible(ForumActor.from(request)));
    }

    /**
     * 新建版块。
     *
     * @param body 保存请求
     * @param request 当前请求
     * @return 新建后的版块
     */
    @PostMapping
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    @AuditLog(operation = "新建论坛版块", type = OperateType.CREATE, targetType = "fm_board")
    public Result<BoardVO> create(@Valid @RequestBody BoardSaveRequest body, HttpServletRequest request) {
        return Result.ok(boardService.create(body, ForumActor.from(request)));
    }

    /**
     * 更新版块（编码不可改）。
     *
     * @param id 版块ID
     * @param body 保存请求
     * @param request 当前请求
     * @return 更新后的版块
     */
    @PutMapping("/{id}")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    @AuditLog(operation = "更新论坛版块", type = OperateType.UPDATE, targetType = "fm_board")
    public Result<BoardVO> update(@PathVariable("id") Long id,
                                  @Valid @RequestBody BoardSaveRequest body,
                                  HttpServletRequest request) {
        return Result.ok(boardService.update(id, body, ForumActor.from(request)));
    }

    /**
     * 删除版块（软删）。
     *
     * @param id 版块ID
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(ForumPermissions.MANAGE_BOARD)
    @AuditLog(operation = "删除论坛版块", type = OperateType.DELETE, targetType = "fm_board")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        boardService.delete(id, ForumActor.from(request));
        return Result.ok();
    }
}
