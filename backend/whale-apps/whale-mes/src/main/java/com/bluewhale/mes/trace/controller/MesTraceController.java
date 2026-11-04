package com.bluewhale.mes.trace.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.trace.dto.MesBarcodeGenerateRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeQueryRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeRuleSaveRequest;
import com.bluewhale.mes.trace.dto.MesBatchQueryRequest;
import com.bluewhale.mes.trace.dto.MesBatchSaveRequest;
import com.bluewhale.mes.trace.dto.MesScanRuleSaveRequest;
import com.bluewhale.mes.trace.dto.MesScanValidateRequest;
import com.bluewhale.mes.trace.dto.MesTraceLinkRequest;
import com.bluewhale.mes.trace.service.MesBarcodeService;
import com.bluewhale.mes.trace.service.MesBatchService;
import com.bluewhale.mes.trace.service.MesScanService;
import com.bluewhale.mes.trace.vo.MesBarcodeRuleVO;
import com.bluewhale.mes.trace.vo.MesBarcodeVO;
import com.bluewhale.mes.trace.vo.MesBatchVO;
import com.bluewhale.mes.trace.vo.MesScanRuleVO;
import com.bluewhale.mes.trace.vo.MesScanValidateVO;
import com.bluewhale.mes.trace.vo.MesTraceNodeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MES 物料追溯接口
 *
 * <p>覆盖条码规则与生成、批次建档/封批、谱系建链与正反向追溯、扫码防错校验；
 * 线边库存与叫料补货见 {@code MesLineSideController}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/trace")
@RequiredArgsConstructor
public class MesTraceController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_trace";

    private final MesBarcodeService barcodeService;

    private final MesBatchService batchService;

    private final MesScanService scanService;

    /**
     * 批量生成条码
     *
     * @param request 生成请求
     * @return 条码列表
     */
    @PostMapping("/barcode/generate")
    @RequirePermission("sys:mes:trace:barcode")
    @AuditLog(operation = "MES 条码生成", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<List<MesBarcodeVO>> generate(@Valid @RequestBody MesBarcodeGenerateRequest request) {
        return Result.ok(barcodeService.generate(request));
    }

    /**
     * 作废条码
     *
     * @param id 条码 ID
     * @return 空结果
     */
    @PostMapping("/barcode/{id}/void")
    @RequirePermission("sys:mes:trace:barcode")
    @AuditLog(operation = "MES 条码作废", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> voidBarcode(@PathVariable Long id) {
        barcodeService.voidBarcode(id);
        return Result.ok();
    }

    /**
     * 分页查询条码
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/barcode/page")
    @RequirePermission("sys:mes:trace:list")
    public Result<PageResult<MesBarcodeVO>> pageBarcodes(MesBarcodeQueryRequest request) {
        return Result.ok(barcodeService.page(request));
    }

    /**
     * 保存条码规则
     *
     * @param request 规则请求
     * @return 规则视图
     */
    @PostMapping("/barcode/rule")
    @RequirePermission("sys:mes:trace:barcode")
    @AuditLog(operation = "MES 条码规则保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesBarcodeRuleVO> saveBarcodeRule(@Valid @RequestBody MesBarcodeRuleSaveRequest request) {
        return Result.ok(barcodeService.saveRule(request));
    }

    /**
     * 分页查询条码规则
     *
     * @param ruleCode 规则编码（模糊）
     * @param status   状态
     * @param current  当前页
     * @param size     每页条数
     * @return 分页结果
     */
    @GetMapping("/barcode/rule/page")
    @RequirePermission("sys:mes:trace:list")
    public Result<PageResult<MesBarcodeRuleVO>> pageBarcodeRules(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long current,
            @RequestParam(required = false) Long size) {
        return Result.ok(barcodeService.pageRules(ruleCode, status, current, size));
    }

    /**
     * 批次建档
     *
     * @param request 建档请求
     * @return 批次视图
     */
    @PostMapping("/batch")
    @RequirePermission("sys:mes:trace:batch")
    @AuditLog(operation = "MES 批次建档", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesBatchVO> createBatch(@Valid @RequestBody MesBatchSaveRequest request) {
        return Result.ok(batchService.create(request));
    }

    /**
     * 分页查询批次
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/batch/page")
    @RequirePermission("sys:mes:trace:list")
    public Result<PageResult<MesBatchVO>> pageBatches(MesBatchQueryRequest request) {
        return Result.ok(batchService.page(request));
    }

    /**
     * 封批
     *
     * @param id 批次 ID
     * @return 批次视图
     */
    @PostMapping("/batch/{id}/close")
    @RequirePermission("sys:mes:trace:batch")
    @AuditLog(operation = "MES 批次封批", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesBatchVO> closeBatch(@PathVariable Long id) {
        return Result.ok(batchService.close(id));
    }

    /**
     * 建立批次谱系链接
     *
     * @param request 建链请求
     * @return 链接 ID
     */
    @PostMapping("/trace/link")
    @RequirePermission("sys:mes:trace:batch")
    @AuditLog(operation = "MES 批次谱系建链", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<Long> link(@Valid @RequestBody MesTraceLinkRequest request) {
        return Result.ok(batchService.link(request));
    }

    /**
     * 正向追溯（下游成品）
     *
     * @param batchNo 批次号
     * @param depth   最大层级
     * @return 追溯节点
     */
    @GetMapping("/trace/forward")
    @RequirePermission("sys:mes:trace:list")
    public Result<List<MesTraceNodeVO>> forward(@RequestParam String batchNo,
                                                @RequestParam(required = false) Integer depth) {
        return Result.ok(batchService.forward(batchNo, depth));
    }

    /**
     * 反向追溯（上游来料）
     *
     * @param batchNo 批次号
     * @param depth   最大层级
     * @return 追溯节点
     */
    @GetMapping("/trace/backward")
    @RequirePermission("sys:mes:trace:list")
    public Result<List<MesTraceNodeVO>> backward(@RequestParam String batchNo,
                                                 @RequestParam(required = false) Integer depth) {
        return Result.ok(batchService.backward(batchNo, depth));
    }

    /**
     * 扫码校验
     *
     * @param request 校验请求
     * @return 校验结果
     */
    @PostMapping("/scan/validate")
    @RequirePermission("sys:mes:trace:scan")
    @AuditLog(operation = "MES 扫码防错校验", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesScanValidateVO> validateScan(@Valid @RequestBody MesScanValidateRequest request) {
        return Result.ok(scanService.validate(request));
    }

    /**
     * 保存扫码规则
     *
     * @param request 规则请求
     * @return 规则视图
     */
    @PostMapping("/scan/rule")
    @RequirePermission("sys:mes:trace:scan")
    @AuditLog(operation = "MES 扫码规则保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesScanRuleVO> saveScanRule(@Valid @RequestBody MesScanRuleSaveRequest request) {
        return Result.ok(scanService.saveRule(request));
    }

    /**
     * 分页查询扫码规则
     *
     * @param ruleCode    规则编码（模糊）
     * @param stationCode 工位编码
     * @param status      状态
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    @GetMapping("/scan/rule/page")
    @RequirePermission("sys:mes:trace:scan")
    public Result<PageResult<MesScanRuleVO>> pageScanRules(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long current,
            @RequestParam(required = false) Long size) {
        return Result.ok(scanService.pageRules(ruleCode, stationCode, status, current, size));
    }
}
