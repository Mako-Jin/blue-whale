package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.dto.MesSpcRuleQueryRequest;
import com.bluewhale.mes.quality.dto.MesSpcRuleSaveRequest;
import com.bluewhale.mes.quality.dto.MesSpcSampleQueryRequest;
import com.bluewhale.mes.quality.dto.MesSpcSampleRequest;
import com.bluewhale.mes.quality.service.MesSpcService;
import com.bluewhale.mes.quality.vo.MesSpcChartPointVO;
import com.bluewhale.mes.quality.vo.MesSpcRuleVO;
import com.bluewhale.mes.quality.vo.MesSpcSampleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MES SPC 接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/quality/spc", version = "1")
@RequiredArgsConstructor
public class MesSpcController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_quality";

    private final MesSpcService spcService;

    /**
     * 保存 SPC 规则
     *
     * @param request 规则请求
     * @return 规则视图
     */
    @PostMapping("/rule")
    @RequirePermission("sys:mes:quality:spc")
    @AuditLog(operation = "MES SPC 规则保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesSpcRuleVO> saveRule(@Valid @RequestBody MesSpcRuleSaveRequest request) {
        return Result.ok(spcService.saveRule(request));
    }

    /**
     * 分页查询 SPC 规则
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/rule/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesSpcRuleVO>> pageRules(MesSpcRuleQueryRequest request) {
        return Result.ok(spcService.pageRules(request));
    }

    /**
     * 录入 SPC 样本
     *
     * @param request 样本请求
     * @return 样本视图
     */
    @PostMapping("/sample")
    @RequirePermission("sys:mes:quality:spc")
    @AuditLog(operation = "MES SPC 样本录入", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesSpcSampleVO> submitSample(@Valid @RequestBody MesSpcSampleRequest request) {
        return Result.ok(spcService.submitSample(request));
    }

    /**
     * 分页查询 SPC 样本
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/sample/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesSpcSampleVO>> pageSamples(MesSpcSampleQueryRequest request) {
        return Result.ok(spcService.pageSamples(request));
    }

    /**
     * 查询控制图表点
     *
     * @param planItemId 检验计划项 ID
     * @param ruleId     规则 ID
     * @param limit      最大子组数
     * @return 图表点
     */
    @GetMapping("/chart")
    @RequirePermission("sys:mes:quality:list")
    public Result<List<MesSpcChartPointVO>> chart(@RequestParam Long planItemId,
                                                  @RequestParam(required = false) Long ruleId,
                                                  @RequestParam(required = false) Integer limit) {
        return Result.ok(spcService.chart(planItemId, ruleId, limit));
    }
}
