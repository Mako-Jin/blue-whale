package com.bluewhale.erp.hr.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.hr.application.command.OrgUnitSaveCommand;
import com.bluewhale.erp.hr.application.service.OrgAppService;
import com.bluewhale.erp.hr.application.view.OrgUnitView;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织单元接口：列表、新增、编辑。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/hr/org", version = "1")
@RequiredArgsConstructor
public class HrOrgController {

    private final OrgAppService orgAppService;

    /**
     * 组织列表。
     *
     * @param query 查询条件
     * @return 组织列表
     */
    @GetMapping
    @RequirePermission("sys:erp:hr:org:list")
    public Result<List<OrgUnitView>> list(@ModelAttribute HrQuery query) {
        return Result.ok(orgAppService.list(query));
    }

    /**
     * 新增组织。
     *
     * @param command 保存命令
     * @return 组织
     */
    @PostMapping
    @RequirePermission("sys:erp:hr:org:add")
    @AuditLog(operation = "新增组织单元", targetType = "erp_hr_org_unit", type = OperateType.CREATE)
    public Result<OrgUnitView> create(@Valid @RequestBody OrgUnitSaveCommand command) {
        return Result.ok(orgAppService.create(command));
    }

    /**
     * 编辑组织。
     *
     * @param code 组织编码
     * @param command 保存命令
     * @return 组织
     */
    @PutMapping("/{code}")
    @RequirePermission("sys:erp:hr:org:update")
    @AuditLog(operation = "编辑组织单元", targetType = "erp_hr_org_unit", type = OperateType.UPDATE)
    public Result<OrgUnitView> update(@PathVariable String code,
                                      @Valid @RequestBody OrgUnitSaveCommand command) {
        return Result.ok(orgAppService.update(code, command));
    }
}
