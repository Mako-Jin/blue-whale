package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pm.application.service.ProjectAppService;
import com.bluewhale.pm.application.service.ProjectMemberAppService;
import com.bluewhale.pm.application.service.ProjectWorkflowAppService;
import com.bluewhale.pm.interfaces.dto.ProjectMemberSaveRequest;
import com.bluewhale.pm.interfaces.dto.ProjectQueryRequest;
import com.bluewhale.pm.interfaces.dto.ProjectSaveRequest;
import com.bluewhale.pm.interfaces.dto.ProjectStatusChangeRequest;
import com.bluewhale.pm.interfaces.dto.StatusSaveRequest;
import com.bluewhale.pm.interfaces.dto.TransitionSaveRequest;
import com.bluewhale.pm.interfaces.vo.ProjectMemberVO;
import com.bluewhale.pm.interfaces.vo.ProjectVO;
import com.bluewhale.pm.interfaces.vo.StatusVO;
import com.bluewhale.pm.interfaces.vo.TransitionVO;
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
 * 项目管理接口：项目空间、成员、状态与流转配置。
 *
 * <p>服务内路径 {@code /pm/project/**}，经网关按 {@code Path=/pm/**} 转发且不剥离前缀；
 * 全部接口受 {@code sys:pm:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pm/project")
@RequiredArgsConstructor
public class ProjectController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "pm_project";

    private final ProjectAppService projectAppService;

    private final ProjectMemberAppService memberAppService;

    private final ProjectWorkflowAppService workflowAppService;

    /**
     * 分页查询项目
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pm:project:list")
    public Result<PageResult<ProjectVO>> page(ProjectQueryRequest request) {
        return Result.ok(projectAppService.page(request));
    }

    /**
     * 项目详情
     *
     * @param id 项目ID
     * @return 项目详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pm:project:list")
    public Result<ProjectVO> detail(@PathVariable Long id) {
        return Result.ok(projectAppService.getDetail(id));
    }

    /**
     * 新增项目
     *
     * @param request 新增请求
     * @return 新增后的项目
     */
    @PostMapping
    @RequirePermission("sys:pm:project:add")
    @AuditLog(operation = "新增项目", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ProjectVO> create(@Valid @RequestBody ProjectSaveRequest request) {
        return Result.ok(projectAppService.create(request));
    }

    /**
     * 更新项目
     *
     * @param id      项目ID
     * @param request 更新请求
     * @return 更新后的项目
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pm:project:edit")
    @AuditLog(operation = "更新项目", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectSaveRequest request) {
        return Result.ok(projectAppService.update(id, request));
    }

    /**
     * 变更项目状态（进行中 / 已归档）
     *
     * @param id      项目ID
     * @param request 状态变更请求
     * @return 变更后的项目
     */
    @PutMapping("/{id}/status")
    @RequirePermission("sys:pm:project:edit")
    @AuditLog(operation = "变更项目状态", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<ProjectVO> changeStatus(@PathVariable Long id,
            @Valid @RequestBody ProjectStatusChangeRequest request) {
        return Result.ok(projectAppService.changeStatus(id, request));
    }

    /**
     * 删除项目
     *
     * @param id 项目ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pm:project:delete")
    @AuditLog(operation = "删除项目", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        projectAppService.delete(id);
        return Result.ok();
    }

    /**
     * 查询项目成员
     *
     * @param id 项目ID
     * @return 成员列表
     */
    @GetMapping("/{id}/member")
    @RequirePermission("sys:pm:member:list")
    public Result<List<ProjectMemberVO>> listMembers(@PathVariable Long id) {
        return Result.ok(memberAppService.list(id));
    }

    /**
     * 新增项目成员
     *
     * @param id      项目ID
     * @param request 新增请求
     * @return 新增后的成员
     */
    @PostMapping("/{id}/member")
    @RequirePermission("sys:pm:member:add")
    @AuditLog(operation = "新增项目成员", type = OperateType.CREATE, targetType = "pm_project_member")
    public Result<ProjectMemberVO> addMember(@PathVariable Long id,
            @Valid @RequestBody ProjectMemberSaveRequest request) {
        return Result.ok(memberAppService.add(id, request));
    }

    /**
     * 查询项目状态配置
     *
     * @param id 项目ID
     * @return 状态列表
     */
    @GetMapping("/{id}/status")
    @RequirePermission("sys:pm:status:list")
    public Result<List<StatusVO>> listStatuses(@PathVariable Long id) {
        return Result.ok(workflowAppService.listStatuses(id));
    }

    /**
     * 新增项目状态
     *
     * @param id      项目ID
     * @param request 新增请求
     * @return 新增后的状态
     */
    @PostMapping("/{id}/status")
    @RequirePermission("sys:pm:status:add")
    @AuditLog(operation = "新增项目状态", type = OperateType.CREATE, targetType = "pm_status")
    public Result<StatusVO> addStatus(@PathVariable Long id, @Valid @RequestBody StatusSaveRequest request) {
        return Result.ok(workflowAppService.addStatus(id, request));
    }

    /**
     * 查询项目流转配置
     *
     * @param id 项目ID
     * @return 流转列表
     */
    @GetMapping("/{id}/transition")
    @RequirePermission("sys:pm:transition:list")
    public Result<List<TransitionVO>> listTransitions(@PathVariable Long id) {
        return Result.ok(workflowAppService.listTransitions(id));
    }

    /**
     * 新增状态流转
     *
     * @param id      项目ID
     * @param request 新增请求
     * @return 新增后的流转
     */
    @PostMapping("/{id}/transition")
    @RequirePermission("sys:pm:transition:add")
    @AuditLog(operation = "新增状态流转", type = OperateType.CREATE, targetType = "pm_transition")
    public Result<TransitionVO> addTransition(@PathVariable Long id,
            @Valid @RequestBody TransitionSaveRequest request) {
        return Result.ok(workflowAppService.addTransition(id, request));
    }
}
