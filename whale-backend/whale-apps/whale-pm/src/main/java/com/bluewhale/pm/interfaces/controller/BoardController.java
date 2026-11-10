package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.application.service.BoardAppService;
import com.bluewhale.pm.interfaces.dto.BoardColumnSaveRequest;
import com.bluewhale.pm.interfaces.dto.BoardDragRequest;
import com.bluewhale.pm.interfaces.dto.BoardSaveRequest;
import com.bluewhale.pm.interfaces.vo.BoardColumnVO;
import com.bluewhale.pm.interfaces.vo.BoardVO;
import com.bluewhale.pm.interfaces.vo.BoardViewVO;
import com.bluewhale.pm.interfaces.vo.WorkItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 看板接口：看板 / 列配置、执行视图与拖拽流转（WIP 受限）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pm/board")
@RequiredArgsConstructor
public class BoardController {

    /** 审计操作对象类型：看板 */
    private static final String TARGET_BOARD = "pm_board";

    /** 审计操作对象类型：看板列 */
    private static final String TARGET_COLUMN = "pm_board_column";

    private final BoardAppService boardAppService;

    /**
     * 查询项目看板列表
     *
     * @param projectId  项目ID
     * @param userHeader 用户ID请求头
     * @return 看板列表
     */
    @GetMapping
    @RequirePermission("sys:pm:board:view")
    public Result<List<BoardVO>> list(@RequestParam Long projectId,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.list(projectId, operatorId(userHeader)));
    }

    /**
     * 看板详情（含列配置）
     *
     * @param id         看板ID
     * @param userHeader 用户ID请求头
     * @return 看板详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pm:board:view")
    public Result<BoardVO> detail(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.getDetail(id, operatorId(userHeader)));
    }

    /**
     * 看板执行视图
     *
     * @param id         看板ID
     * @param userHeader 用户ID请求头
     * @return 执行视图
     */
    @GetMapping("/{id}/view")
    @RequirePermission("sys:pm:board:view")
    public Result<BoardViewVO> view(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.view(id, operatorId(userHeader)));
    }

    /**
     * 创建看板
     *
     * @param request    创建请求
     * @param userHeader 用户ID请求头
     * @return 创建后的看板
     */
    @PostMapping
    @RequirePermission("sys:pm:board:manage")
    @AuditLog(operation = "创建看板", type = OperateType.CREATE, targetType = TARGET_BOARD)
    public Result<BoardVO> create(@Valid @RequestBody BoardSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.create(request, operatorId(userHeader)));
    }

    /**
     * 更新看板
     *
     * @param id         看板ID
     * @param request    更新请求
     * @param userHeader 用户ID请求头
     * @return 更新后的看板
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pm:board:manage")
    @AuditLog(operation = "更新看板", type = OperateType.UPDATE, targetType = TARGET_BOARD)
    public Result<BoardVO> update(@PathVariable Long id, @Valid @RequestBody BoardSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.update(id, request, operatorId(userHeader)));
    }

    /**
     * 删除看板
     *
     * @param id         看板ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pm:board:manage")
    @AuditLog(operation = "删除看板", type = OperateType.DELETE, targetType = TARGET_BOARD)
    public Result<Void> delete(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        boardAppService.delete(id, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 新增看板列
     *
     * @param id         看板ID
     * @param request    列请求
     * @param userHeader 用户ID请求头
     * @return 新增后的列
     */
    @PostMapping("/{id}/column")
    @RequirePermission("sys:pm:board:manage")
    @AuditLog(operation = "新增看板列", type = OperateType.CREATE, targetType = TARGET_COLUMN)
    public Result<BoardColumnVO> addColumn(@PathVariable Long id,
            @Valid @RequestBody BoardColumnSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.addColumn(id, request, operatorId(userHeader)));
    }

    /**
     * 更新看板列
     *
     * @param id         看板ID
     * @param columnId   列ID
     * @param request    列请求
     * @param userHeader 用户ID请求头
     * @return 更新后的列
     */
    @PutMapping("/{id}/column/{columnId}")
    @RequirePermission("sys:pm:board:manage")
    @AuditLog(operation = "更新看板列", type = OperateType.UPDATE, targetType = TARGET_COLUMN)
    public Result<BoardColumnVO> updateColumn(@PathVariable Long id, @PathVariable Long columnId,
            @Valid @RequestBody BoardColumnSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.updateColumn(id, columnId, request, operatorId(userHeader)));
    }

    /**
     * 删除看板列
     *
     * @param id         看板ID
     * @param columnId   列ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/{id}/column/{columnId}")
    @RequirePermission("sys:pm:board:manage")
    @AuditLog(operation = "删除看板列", type = OperateType.DELETE, targetType = TARGET_COLUMN)
    public Result<Void> deleteColumn(@PathVariable Long id, @PathVariable Long columnId,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        boardAppService.deleteColumn(id, columnId, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 看板拖拽（校验 WIP 后按状态机流转）
     *
     * @param id         看板ID
     * @param request    拖拽请求
     * @param userHeader 用户ID请求头
     * @return 流转后的工作项
     */
    @PutMapping("/{id}/drag")
    @RequirePermission("sys:pm:item:transition")
    @AuditLog(operation = "看板拖拽流转", type = OperateType.UPDATE, targetType = "pm_work_item")
    public Result<WorkItemVO> drag(@PathVariable Long id, @Valid @RequestBody BoardDragRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(boardAppService.drag(id, request, operatorId(userHeader)));
    }

    /**
     * 解析网关注入的用户ID请求头（不可解析时返回 {@code null}，由应用服务按未认证处理）
     *
     * @param userHeader 用户ID请求头
     * @return 用户ID；缺失或非法时返回 {@code null}
     */
    private static Long operatorId(String userHeader) {
        if (userHeader == null || userHeader.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userHeader.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
