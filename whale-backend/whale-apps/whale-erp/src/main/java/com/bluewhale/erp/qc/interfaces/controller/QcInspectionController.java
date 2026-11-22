package com.bluewhale.erp.qc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.qc.application.command.InspectionCreateCommand;
import com.bluewhale.erp.qc.application.command.InspectionRecordCommand;
import com.bluewhale.erp.qc.application.command.NcrCloseCommand;
import com.bluewhale.erp.qc.application.command.NcrCreateCommand;
import com.bluewhale.erp.qc.application.command.NcrDisposeCommand;
import com.bluewhale.erp.qc.application.service.QualityInspectionAppService;
import com.bluewhale.erp.qc.application.view.InspectionView;
import com.bluewhale.erp.qc.application.view.NcrView;
import com.bluewhale.erp.qc.application.view.QualityCostView;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量检验与不合格品接口：IQC/IPQC/FQC 检验、判定、不合格品处置与质量成本。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/qc", version = "1")
@RequiredArgsConstructor
public class QcInspectionController {

    private final QualityInspectionAppService qualityInspectionAppService;

    /**
     * 检验单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 检验单分页
     */
    @GetMapping("/inspection/page")
    @RequirePermission("sys:erp:qc:inspection:list")
    public Result<PageResult<InspectionView>> pageInspections(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(qualityInspectionAppService.pageInspections(query));
    }

    /**
     * 检验单详情。
     *
     * @param id 主键
     * @return 检验单
     */
    @GetMapping("/inspection/{id}")
    @RequirePermission("sys:erp:qc:inspection:list")
    public Result<InspectionView> detailInspection(@PathVariable Long id) {
        return Result.ok(qualityInspectionAppService.detailInspection(id));
    }

    /**
     * 新增检验单。
     *
     * @param command 创建命令
     * @return 检验单
     */
    @PostMapping("/inspection")
    @RequirePermission("sys:erp:qc:inspection:add")
    @AuditLog(operation = "新增质量检验单", targetType = "erp_qc_inspection_order", type = OperateType.CREATE)
    public Result<InspectionView> createInspection(@Valid @RequestBody InspectionCreateCommand command) {
        return Result.ok(qualityInspectionAppService.createInspection(command));
    }

    /**
     * 录入检验项。
     *
     * @param id 主键
     * @param command 录入命令
     * @return 检验单
     */
    @PostMapping("/inspection/{id}/record")
    @RequirePermission("sys:erp:qc:inspection:record")
    @AuditLog(operation = "录入质量检验项", targetType = "erp_qc_inspection_order", type = OperateType.UPDATE)
    public Result<InspectionView> recordInspection(@PathVariable Long id,
                                                   @Valid @RequestBody InspectionRecordCommand command) {
        return Result.ok(qualityInspectionAppService.recordInspection(id, command));
    }

    /**
     * 判定检验单。
     *
     * @param id 主键
     * @return 检验单
     */
    @PostMapping("/inspection/{id}/judge")
    @RequirePermission("sys:erp:qc:inspection:judge")
    @AuditLog(operation = "判定质量检验单", targetType = "erp_qc_inspection_order", type = OperateType.UPDATE)
    public Result<InspectionView> judgeInspection(@PathVariable Long id) {
        return Result.ok(qualityInspectionAppService.judgeInspection(id));
    }

    /**
     * 不合格品单分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 不合格品单分页
     */
    @GetMapping("/ncr/page")
    @RequirePermission("sys:erp:qc:ncr:list")
    public Result<PageResult<NcrView>> pageNcr(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(qualityInspectionAppService.pageNcr(query));
    }

    /**
     * 手工登记不合格品单。
     *
     * @param command 创建命令
     * @return 不合格品单
     */
    @PostMapping("/ncr")
    @RequirePermission("sys:erp:qc:ncr:dispose")
    @AuditLog(operation = "登记不合格品单", targetType = "erp_qc_ncr", type = OperateType.CREATE)
    public Result<NcrView> createNcr(@Valid @RequestBody NcrCreateCommand command) {
        return Result.ok(qualityInspectionAppService.createNcr(command));
    }

    /**
     * 处置不合格品单。
     *
     * @param id 主键
     * @param command 处置命令
     * @return 不合格品单
     */
    @PostMapping("/ncr/{id}/dispose")
    @RequirePermission("sys:erp:qc:ncr:dispose")
    @AuditLog(operation = "处置不合格品单", targetType = "erp_qc_ncr", type = OperateType.UPDATE)
    public Result<NcrView> disposeNcr(@PathVariable Long id, @Valid @RequestBody NcrDisposeCommand command) {
        return Result.ok(qualityInspectionAppService.disposeNcr(id, command));
    }

    /**
     * 关闭不合格品单。
     *
     * @param id 主键
     * @param command 关闭命令
     * @return 不合格品单
     */
    @PostMapping("/ncr/{id}/close")
    @RequirePermission("sys:erp:qc:ncr:close")
    @AuditLog(operation = "关闭不合格品单", targetType = "erp_qc_ncr", type = OperateType.UPDATE)
    public Result<NcrView> closeNcr(@PathVariable Long id, @Valid @RequestBody NcrCloseCommand command) {
        return Result.ok(qualityInspectionAppService.closeNcr(id, command));
    }

    /**
     * 质量成本分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 质量成本分页
     */
    @GetMapping("/quality-cost/page")
    @RequirePermission("sys:erp:qc:quality-cost:list")
    public Result<PageResult<QualityCostView>> pageQualityCost(
            @ModelAttribute QcQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(qualityInspectionAppService.pageQualityCost(query));
    }
}
