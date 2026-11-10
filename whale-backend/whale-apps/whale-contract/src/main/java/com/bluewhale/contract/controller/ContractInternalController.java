package com.bluewhale.contract.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.contract.dto.ContractCustomerRewriteRequest;
import com.bluewhale.contract.service.ContractService;
import com.bluewhale.contract.vo.ContractVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract 面向 CRM 的合同投影内网接口。
 *
 * <p>投影查询为只读；客户引用重写为写接口（CRM 客户合并调用，按源/目标幂等）。
 * 仅限服务间 Nacos 直连，网关 {@code internal-deny} 统一拦截外部访问。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/contract/internal")
@RequiredArgsConstructor
public class ContractInternalController {

    private static final String BIZ_TYPE_CRM_CUSTOMER = "CRM_CUSTOMER";

    private final ContractService contractService;

    /**
     * 按弱关联业务查询合同投影。
     *
     * @param bizType 业务类型（如 CRM_CUSTOMER）
     * @param bizId 业务 ID（如客户 ID）
     * @return 合同投影列表
     */
    @GetMapping("/by-biz")
    public Result<List<Map<String, Object>>> byBiz(@RequestParam String bizType,
                                                  @RequestParam String bizId) {
        List<ContractVO> contracts = contractService.listByBiz(bizType, bizId);
        return Result.ok(contracts.stream().map(ContractInternalController::toProjection).toList());
    }

    /**
     * 统计客户被合同引用的行数（客户合并预览用）。
     *
     * @param bizId 客户 ID
     * @return 引用合同数
     */
    @GetMapping("/customers/references/count")
    public Result<Long> countCustomerReferences(@RequestParam("bizId") Long bizId) {
        return Result.ok(contractService.countByBiz(BIZ_TYPE_CRM_CUSTOMER, String.valueOf(bizId)));
    }

    /**
     * 客户合并：把源客户合同引用重写到目标客户（幂等，重复执行返回 0）。
     *
     * @param request 重写请求（源/目标客户 ID）
     * @return 受影响行数
     */
    @PostMapping("/customers/references/rewrite")
    public Result<Map<String, Object>> rewriteCustomerReferences(
            @Valid @RequestBody ContractCustomerRewriteRequest request) {
        int rewritten = contractService.rewriteBizReferences(BIZ_TYPE_CRM_CUSTOMER,
                String.valueOf(request.getSourceId()), String.valueOf(request.getTargetId()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rewritten", rewritten);
        return Result.ok(payload);
    }

    /**
     * 转换为台账投影。
     *
     * @param contract 合同视图
     * @return 精简投影字段
     */
    private static Map<String, Object> toProjection(ContractVO contract) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", contract.getId());
        row.put("code", contract.getCode());
        row.put("title", contract.getTitle());
        row.put("category", contract.getCategory());
        row.put("amount", contract.getAmount());
        row.put("currency", contract.getCurrency());
        row.put("status", contract.getStatus());
        row.put("effectiveDate", contract.getEffectiveDate());
        row.put("expireDate", contract.getExpireDate());
        row.put("bizType", contract.getBizType());
        row.put("bizId", contract.getBizId());
        row.put("ownerId", contract.getOwnerId());
        row.put("deptId", contract.getDeptId());
        row.put("updateTime", contract.getUpdateTime());
        return row;
    }
}
