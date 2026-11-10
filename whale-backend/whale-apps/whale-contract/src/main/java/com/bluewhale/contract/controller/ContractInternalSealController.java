package com.bluewhale.contract.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.contract.service.SealService;
import com.bluewhale.contract.vo.SealVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract 面向 OA 的印章内网只读接口。
 *
 * <p>仅限服务间 Nacos 直连，网关 {@code internal-deny} 统一拦截外部访问。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/contract/internal/seal")
@RequiredArgsConstructor
public class ContractInternalSealController {

    private final SealService sealService;

    /**
     * 查询用户可用印章列表。
     *
     * @param userId 用户ID
     * @return 印章简要列表
     */
    @GetMapping("/granted")
    public Result<List<Map<String, Object>>> granted(@RequestParam Long userId) {
        List<SealVO> seals = sealService.grantedSeals(userId);
        return Result.ok(seals.stream().map(seal -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", seal.getId());
            row.put("name", seal.getName());
            row.put("sealType", seal.getSealType());
            return row;
        }).toList());
    }

    /**
     * 校验用户对印章的授权。
     *
     * @param sealId 印章ID
     * @param userId 用户ID
     * @return 授权结果
     */
    @GetMapping("/{sealId}/grant-check")
    public Result<Map<String, Object>> grantCheck(@PathVariable Long sealId, @RequestParam Long userId) {
        boolean granted = sealService.isGranted(sealId, userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("granted", granted);
        data.put("sealId", sealId);
        data.put("userId", userId);
        return Result.ok(data);
    }
}
