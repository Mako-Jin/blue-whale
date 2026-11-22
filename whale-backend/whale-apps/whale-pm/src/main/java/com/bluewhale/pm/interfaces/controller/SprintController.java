package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.application.service.SprintAppService;
import com.bluewhale.pm.interfaces.dto.SprintItemRequest;
import com.bluewhale.pm.interfaces.dto.SprintQueryRequest;
import com.bluewhale.pm.interfaces.dto.SprintSaveRequest;
import com.bluewhale.pm.interfaces.vo.SprintVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 迭代接口：分页 / 详情 / 维护 / 启动 / 关闭 / 工作项入迭代。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pm/v{version}/sprint", version = "1")
@RequiredArgsConstructor
public class SprintController {

    /** 审计操作对象类型：迭代 */
    private static final String TARGET_SPRINT = "pm_sprint";

    private final SprintAppService sprintAppService;

    /**
     * 分页查询迭代
     *
     * @param request    查询条件
     * @param userHeader 用户ID请求头
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pm:sprint:list")
    public Result<PageResult<SprintVO>> page(@Valid SprintQueryRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.page(request, operatorId(userHeader)));
    }

    /**
     * 迭代详情
     *
     * @param id         迭代ID
     * @param userHeader 用户ID请求头
     * @return 迭代详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pm:sprint:list")
    public Result<SprintVO> detail(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.getDetail(id, operatorId(userHeader)));
    }

    /**
     * 创建迭代
     *
     * @param request    创建请求
     * @param userHeader 用户ID请求头
     * @return 创建后的迭代
     */
    @PostMapping
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "创建迭代", type = OperateType.CREATE, targetType = TARGET_SPRINT)
    public Result<SprintVO> create(@Valid @RequestBody SprintSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.create(request, operatorId(userHeader)));
    }

    /**
     * 更新迭代
     *
     * @param id         迭代ID
     * @param request    更新请求
     * @param userHeader 用户ID请求头
     * @return 更新后的迭代
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "更新迭代", type = OperateType.UPDATE, targetType = TARGET_SPRINT)
    public Result<SprintVO> update(@PathVariable Long id, @Valid @RequestBody SprintSaveRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.update(id, request, operatorId(userHeader)));
    }

    /**
     * 删除迭代
     *
     * @param id         迭代ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "删除迭代", type = OperateType.DELETE, targetType = TARGET_SPRINT)
    public Result<Void> delete(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        sprintAppService.delete(id, operatorId(userHeader));
        return Result.ok();
    }

    /**
     * 启动迭代
     *
     * @param id         迭代ID
     * @param userHeader 用户ID请求头
     * @return 启动后的迭代
     */
    @PutMapping("/{id}/start")
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "启动迭代", type = OperateType.UPDATE, targetType = TARGET_SPRINT)
    public Result<SprintVO> start(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.start(id, operatorId(userHeader)));
    }

    /**
     * 关闭迭代
     *
     * @param id         迭代ID
     * @param userHeader 用户ID请求头
     * @return 关闭后的迭代
     */
    @PutMapping("/{id}/close")
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "关闭迭代", type = OperateType.UPDATE, targetType = TARGET_SPRINT)
    public Result<SprintVO> close(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.close(id, operatorId(userHeader)));
    }

    /**
     * 查询迭代内工作项
     *
     * @param id         迭代ID
     * @param userHeader 用户ID请求头
     * @return 工作项列表
     */
    @GetMapping("/{id}/item")
    @RequirePermission("sys:pm:sprint:list")
    public Result<List<WorkItemVO>> listItems(@PathVariable Long id,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.listItems(id, operatorId(userHeader)));
    }

    /**
     * 批量加入工作项
     *
     * @param id         迭代ID
     * @param request    工作项ID列表
     * @param userHeader 用户ID请求头
     * @return 成功加入数量
     */
    @PostMapping("/{id}/item")
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "迭代加入工作项", type = OperateType.UPDATE, targetType = TARGET_SPRINT)
    public Result<Integer> addItems(@PathVariable Long id, @Valid @RequestBody SprintItemRequest request,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        return Result.ok(sprintAppService.addItems(id, request, operatorId(userHeader)));
    }

    /**
     * 移出工作项
     *
     * @param id         迭代ID
     * @param itemId     工作项ID
     * @param userHeader 用户ID请求头
     * @return 空结果
     */
    @DeleteMapping("/{id}/item/{itemId}")
    @RequirePermission("sys:pm:sprint:manage")
    @AuditLog(operation = "迭代移出工作项", type = OperateType.UPDATE, targetType = TARGET_SPRINT)
    public Result<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId,
            @RequestHeader(value = WebHeaders.USER_ID, required = false) String userHeader) {
        sprintAppService.removeItem(id, itemId, operatorId(userHeader));
        return Result.ok();
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
