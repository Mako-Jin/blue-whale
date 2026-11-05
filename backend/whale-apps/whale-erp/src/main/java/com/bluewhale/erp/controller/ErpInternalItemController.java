package com.bluewhale.erp.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.dto.ErpInternalItemUpsertRequest;
import com.bluewhale.erp.service.ErpItemService;
import com.bluewhale.erp.vo.ErpInternalItemSyncVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 物料内网契约接口
 *
 * <p>供 whale-pdm 按 {@code pdmPartNo} 幂等同步零件物料。网关已将
 * {@code /erp/internal/**} 加入 internal-deny，外部访问 403。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/item")
@RequiredArgsConstructor
public class ErpInternalItemController {

    private final ErpItemService itemService;

    /**
     * 按 PDM 零件编码幂等写入物料
     *
     * @param request 同步请求
     * @return 同步结果
     */
    @PostMapping("/upsert")
    public Result<ErpInternalItemSyncVO> upsert(@Valid @RequestBody ErpInternalItemUpsertRequest request) {
        return Result.ok(itemService.upsertFromPdm(request));
    }
}
