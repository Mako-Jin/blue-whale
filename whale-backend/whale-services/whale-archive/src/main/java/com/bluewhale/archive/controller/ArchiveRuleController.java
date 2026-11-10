package com.bluewhale.archive.controller;

import com.bluewhale.archive.dto.ArchiveRuleQueryRequest;
import com.bluewhale.archive.dto.ArchiveRuleSaveRequest;
import com.bluewhale.archive.service.ArchiveRuleService;
import com.bluewhale.archive.vo.ArchiveRuleVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
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

/**
 * 归档规则管理接口。
 *
 * <p>路径前缀 {@code /archive/rule}，权限码 {@code sys:archive:rule:list} 与
 * {@code sys:archive:rule:manage}；写操作均落审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/archive/rule")
@RequiredArgsConstructor
public class ArchiveRuleController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "archive_rule";

    private final ArchiveRuleService ruleService;

    /**
     * 分页查询归档规则。
     *
     * @param request 查询条件
     * @return 规则分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:archive:rule:list")
    public Result<PageResult<ArchiveRuleVO>> page(ArchiveRuleQueryRequest request) {
        return Result.ok(ruleService.page(request));
    }

    /**
     * 新增归档规则。
     *
     * @param request 保存请求
     * @return 新规则视图
     */
    @PostMapping
    @RequirePermission("sys:archive:rule:manage")
    @AuditLog(operation = "新增归档规则", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ArchiveRuleVO> create(@Valid @RequestBody ArchiveRuleSaveRequest request) {
        return Result.ok(ruleService.create(request));
    }

    /**
     * 修改归档规则。
     *
     * @param id 规则ID
     * @param request 保存请求
     * @return 修改后的规则视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:archive:rule:manage")
    @AuditLog(operation = "修改归档规则", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ArchiveRuleVO> update(@PathVariable Long id,
                                        @Valid @RequestBody ArchiveRuleSaveRequest request) {
        return Result.ok(ruleService.update(id, request));
    }

    /**
     * 删除归档规则。
     *
     * @param id 规则ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:archive:rule:manage")
    @AuditLog(operation = "删除归档规则", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
