package com.bluewhale.pm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pm.application.service.ProjectMemberAppService;
import com.bluewhale.pm.interfaces.dto.ProjectMemberUpdateRequest;
import com.bluewhale.pm.interfaces.vo.ProjectMemberVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目成员接口：按成员记录 ID 变更角色或移除。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pm/member")
@RequiredArgsConstructor
public class ProjectMemberController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "pm_project_member";

    private final ProjectMemberAppService memberAppService;

    /**
     * 变更成员角色
     *
     * @param id      成员记录ID
     * @param request 角色变更请求
     * @return 变更后的成员
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pm:member:edit")
    @AuditLog(operation = "变更项目成员角色", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<ProjectMemberVO> updateRole(@PathVariable Long id,
            @Valid @RequestBody ProjectMemberUpdateRequest request) {
        return Result.ok(memberAppService.updateRole(id, request));
    }

    /**
     * 移除项目成员
     *
     * @param id 成员记录ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pm:member:delete")
    @AuditLog(operation = "移除项目成员", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> remove(@PathVariable Long id) {
        memberAppService.remove(id);
        return Result.ok();
    }
}
