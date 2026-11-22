package com.bluewhale.contract.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.contract.dto.ContractQueryRequest;
import com.bluewhale.contract.dto.ContractSaveRequest;
import com.bluewhale.contract.dto.ContractVersionSaveRequest;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.service.ContractService;
import com.bluewhale.contract.vo.ContractVO;
import com.bluewhale.contract.vo.ContractVersionVO;
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
 * 合同主流程接口
 *
 * <p>服务内路径 {@code /contract/**}，经网关按 {@code Path=/contract/**} 转发；
 * 全部接口受 {@code sys:contract:*} 权限保护，写操作与状态流转留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/contract/v{version}", version = "1")
@RequiredArgsConstructor
public class ContractController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "ct_contract";

    /** 权限码头名（用于透传给工作流） */
    private static final String PERMISSION_HEADER = WebHeaders.PERMISSION_CODES;

    private final ContractService contractService;

    /**
     * 分页查询合同
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:contract:list")
    public Result<PageResult<ContractVO>> page(ContractQueryRequest request) {
        return Result.ok(contractService.page(request));
    }

    /**
     * 合同详情
     *
     * @param id 合同ID
     * @return 合同详情（含相对方、版本、流程记录）
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:contract:list")
    public Result<ContractVO> detail(@PathVariable Long id) {
        return Result.ok(contractService.getDetail(id));
    }

    /**
     * 新建合同（草稿）
     *
     * @param request      新增请求
     * @param httpRequest  当前请求（解析操作人）
     * @return 新增后的合同
     */
    @PostMapping
    @RequirePermission("sys:contract:add")
    @AuditLog(operation = "新建合同", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ContractVO> create(@Valid @RequestBody ContractSaveRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(contractService.create(request, operator(httpRequest)));
    }

    /**
     * 更新合同（仅 DRAFT）
     *
     * @param id          合同ID
     * @param request     更新请求
     * @return 更新后的合同
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:contract:edit")
    @AuditLog(operation = "更新合同", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVO> update(@PathVariable Long id, @Valid @RequestBody ContractSaveRequest request) {
        return Result.ok(contractService.update(id, request));
    }

    /**
     * 删除合同（仅 DRAFT，逻辑删除）
     *
     * @param id 合同ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:contract:delete")
    @AuditLog(operation = "删除合同", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return Result.ok();
    }

    /**
     * 提交审批（启动流程实例）
     *
     * @param id          合同ID
     * @param httpRequest 当前请求（解析操作人）
     * @return 提交后的合同
     */
    @PostMapping("/{id}/submit")
    @RequirePermission("sys:contract:submit")
    @AuditLog(operation = "提交合同审批", type = OperateType.OTHER, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVO> submit(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(contractService.submit(id, operator(httpRequest)));
    }

    /**
     * 撤回审批（仅 APPROVING）
     *
     * @param id          合同ID
     * @param httpRequest 当前请求（解析操作人）
     * @return 撤回后的合同
     */
    @PostMapping("/{id}/withdraw")
    @RequirePermission("sys:contract:withdraw")
    @AuditLog(operation = "撤回合同审批", type = OperateType.OTHER, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVO> withdraw(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(contractService.withdraw(id, operator(httpRequest)));
    }

    /**
     * 终止合同
     *
     * @param id 合同ID
     * @return 终止后的合同
     */
    @PostMapping("/{id}/terminate")
    @RequirePermission("sys:contract:terminate")
    @AuditLog(operation = "终止合同", type = OperateType.OTHER, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVO> terminate(@PathVariable Long id) {
        return Result.ok(contractService.terminate(id));
    }

    /**
     * 合同生效（仅 SIGNED）
     *
     * @param id 合同ID
     * @return 生效后的合同
     */
    @PostMapping("/{id}/effective")
    @RequirePermission("sys:contract:effective")
    @AuditLog(operation = "合同生效", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVO> effective(@PathVariable Long id) {
        return Result.ok(contractService.effective(id));
    }

    /**
     * 归档合同（仅 EFFECTIVE）
     *
     * @param id 合同ID
     * @return 归档后的合同
     */
    @PostMapping("/{id}/archive")
    @RequirePermission("sys:contract:archive")
    @AuditLog(operation = "归档合同", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVO> archive(@PathVariable Long id) {
        return Result.ok(contractService.archive(id));
    }

    /**
     * 合同版本列表
     *
     * @param id 合同ID
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    @RequirePermission("sys:contract:list")
    public Result<List<ContractVersionVO>> versions(@PathVariable Long id) {
        return Result.ok(contractService.listVersions(id));
    }

    /**
     * 基于当前版本发起修订（版本号 +1，合同回到 DRAFT）
     *
     * @param id      合同ID
     * @param request 修订请求
     * @return 新版本
     */
    @PostMapping("/{id}/versions")
    @RequirePermission("sys:contract:edit")
    @AuditLog(operation = "发起合同修订", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ContractVersionVO> createVersion(@PathVariable Long id,
                                                   @Valid @RequestBody ContractVersionSaveRequest request) {
        return Result.ok(contractService.createVersion(id, request));
    }

    /**
     * 从网关可信头解析操作人。
     *
     * @param request 当前请求
     * @return 操作人上下文
     */
    private static OperatorContext operator(HttpServletRequest request) {
        return new OperatorContext(WebHeaders.userId(request), WebHeaders.username(request),
                request.getHeader(PERMISSION_HEADER), request.getHeader(WebHeaders.USER_ROLES));
    }
}
