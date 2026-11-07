package com.bluewhale.contract.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.contract.service.ContractService;
import com.bluewhale.contract.vo.ContractVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract 面向 CRM 的合同投影内网只读接口。
 *
 * <p>仅返回台账级字段，不返回合同正文、相对方、版本与流程记录；
 * 仅限服务间 Nacos 直连，网关 {@code internal-deny} 统一拦截外部访问。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/contract/internal")
@RequiredArgsConstructor
public class ContractInternalController {

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
