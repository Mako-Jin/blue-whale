package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.fee.application.command.FeePayCommand;
import com.bluewhale.party.fee.application.command.FeeRuleSaveCommand;
import com.bluewhale.party.fee.application.service.FeeAppService;
import com.bluewhale.party.fee.application.view.FeeBillView;
import com.bluewhale.party.fee.application.view.FeeGenerateResultView;
import com.bluewhale.party.fee.application.view.FeeRuleView;
import com.bluewhale.party.fee.domain.model.FeeBillQuery;
import com.bluewhale.party.interfaces.support.PageParams;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 党费台账接口（M-P1）。
 *
 * <p>「生成账单」与「登记缴费」分开：生成是批量、可重跑的月度动作，
 * 缴费是逐笔、需要留缴款日期的动作。合成一个接口会让「补缴上月」与「生成本月」
 * 抢同一个入口，出错时也分不清是规则问题还是操作问题。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/fee", version = "1")
@RequiredArgsConstructor
public class PartyFeeController {

    private final FeeAppService feeAppService;

    /**
     * 党费标准列表。
     *
     * @param request 当前请求
     * @return 档位列表
     */
    @GetMapping("/rule")
    @RequirePermission(PartyPermissions.FEE_LIST)
    public Result<List<FeeRuleView>> rules(HttpServletRequest request) {
        return Result.ok(feeAppService.rules(actor(request)));
    }

    /**
     * 新建党费档位。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 档位视图
     */
    @PostMapping("/rule")
    @RequirePermission(PartyPermissions.FEE_MANAGE)
    @AuditLog(operation = "新建党费档位", type = OperateType.CREATE, targetType = "pty_fee_rule")
    public Result<FeeRuleView> createRule(@Valid @RequestBody FeeRuleSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(feeAppService.saveRule(null, command, actor(request)));
    }

    /**
     * 编辑党费档位。
     *
     * @param id 档位 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 档位视图
     */
    @PutMapping("/rule/{id}")
    @RequirePermission(PartyPermissions.FEE_MANAGE)
    @AuditLog(operation = "编辑党费档位", type = OperateType.UPDATE, targetType = "pty_fee_rule")
    public Result<FeeRuleView> updateRule(@PathVariable("id") Long id,
                                          @Valid @RequestBody FeeRuleSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(feeAppService.saveRule(id, command, actor(request)));
    }

    /**
     * 初始化标准档位（幂等，已有档位时直接返回既有配置）。
     *
     * @param request 当前请求
     * @return 档位列表
     */
    @PostMapping("/rule/init")
    @RequirePermission(PartyPermissions.FEE_MANAGE)
    @AuditLog(operation = "初始化党费标准档位", type = OperateType.CREATE, targetType = "pty_fee_rule")
    public Result<List<FeeRuleView>> initRules(HttpServletRequest request) {
        return Result.ok(feeAppService.initDefaultRules(actor(request)));
    }

    /**
     * 党费账单分页。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @param branchId 支部 ID
     * @param status 状态
     * @param keyword 党员姓名关键字
     * @param outstanding 是否只看欠缴
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/bill/page")
    @RequirePermission(PartyPermissions.FEE_LIST)
    public Result<PageResult<FeeBillView>> bills(
            @RequestParam(value = "periodMonth", required = false) String periodMonth,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "outstanding", required = false) Boolean outstanding,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        FeeBillQuery query = new FeeBillQuery();
        query.setPeriodMonth(periodMonth);
        query.setBranchId(branchId);
        query.setStatus(status);
        query.setKeyword(keyword);
        query.setOutstanding(outstanding);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(feeAppService.bills(query, actor(request)));
    }

    /**
     * 按月份生成账单。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @param branchId 支部 ID（为空表示全租户）
     * @param request 当前请求
     * @return 生成结果
     */
    @PostMapping("/bill/generate")
    @RequirePermission(PartyPermissions.FEE_MANAGE)
    @AuditLog(operation = "生成党费账单", type = OperateType.CREATE, targetType = "pty_fee_bill")
    public Result<FeeGenerateResultView> generate(
            @RequestParam("periodMonth") String periodMonth,
            @RequestParam(value = "branchId", required = false) Long branchId,
            HttpServletRequest request) {
        return Result.ok(feeAppService.generate(periodMonth, branchId, actor(request)));
    }

    /**
     * 登记缴费。
     *
     * @param id 账单 ID
     * @param command 缴费命令
     * @param request 当前请求
     * @return 账单视图
     */
    @PostMapping("/bill/{id}/pay")
    @RequirePermission(PartyPermissions.FEE_MANAGE)
    @AuditLog(operation = "登记党费缴纳", type = OperateType.UPDATE, targetType = "pty_fee_bill")
    public Result<FeeBillView> pay(@PathVariable("id") Long id,
                                   @Valid @RequestBody FeePayCommand command,
                                   HttpServletRequest request) {
        return Result.ok(feeAppService.pay(id, command, actor(request)));
    }

    /**
     * 标记免缴。
     *
     * @param id 账单 ID
     * @param reason 免缴原因
     * @param request 当前请求
     * @return 账单视图
     */
    @PostMapping("/bill/{id}/waive")
    @RequirePermission(PartyPermissions.FEE_MANAGE)
    @AuditLog(operation = "党费免缴", type = OperateType.UPDATE, targetType = "pty_fee_bill")
    public Result<FeeBillView> waive(@PathVariable("id") Long id,
                                     @RequestParam(value = "reason", required = false) String reason,
                                     HttpServletRequest request) {
        return Result.ok(feeAppService.waive(id, reason, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private PartyActor actor(HttpServletRequest request) {
        return PartyActorContext.from(request);
    }
}
