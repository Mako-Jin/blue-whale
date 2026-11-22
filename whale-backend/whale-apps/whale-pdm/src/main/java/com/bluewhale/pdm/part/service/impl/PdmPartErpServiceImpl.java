package com.bluewhale.pdm.part.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.integration.client.PdmIntErpClient;
import com.bluewhale.pdm.integration.client.PdmIntErpItemInfo;
import com.bluewhale.pdm.integration.client.PdmIntErpStockInfo;
import com.bluewhale.pdm.part.service.PdmPartErpService;
import com.bluewhale.pdm.part.vo.PdmPartErpStockVO;
import com.bluewhale.pdm.part.vo.PdmPartErpWarehouseVO;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 零件 ERP 物料/库存只读查询实现
 *
 * <p>优先使用零件维护的 {@code erpItemCode}，未维护时以零件编码作为 ERP 物料编码（与同步口径一致）。
 * ERP 调用失败时返回降级结果，避免影响零件详情主流程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmPartErpServiceImpl implements PdmPartErpService {

    /** ERP 不可用降级提示 */
    private static final String DEGRADED_MESSAGE = "ERP 暂不可用，物料与库存信息未获取";

    private final PdmProductMapper productMapper;

    private final PdmIntErpClient erpClient;

    @Override
    public PdmPartErpStockVO getStock(Long partId) {
        PdmProduct part = partId == null ? null : productMapper.selectById(partId);
        if (part == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.part.part-found", "零件不存在");
        }
        String erpItemCode = StringUtils.hasText(part.getErpItemCode())
                ? part.getErpItemCode() : part.getCode();
        PdmPartErpStockVO vo = new PdmPartErpStockVO();
        vo.setPartId(part.getId());
        vo.setPartCode(part.getCode());
        vo.setErpItemCode(erpItemCode);
        vo.setItemExists(false);
        vo.setTotalQuantity(BigDecimal.ZERO);
        vo.setWarehouses(List.of());
        vo.setDegraded(false);
        try {
            Long tenantId = TenantContext.requireId();
            PdmIntErpItemInfo item = erpClient.getItem(tenantId, erpItemCode);
            PdmIntErpStockInfo stock = erpClient.getStock(tenantId, erpItemCode);
            vo.setItemExists(item != null && item.exists());
            vo.setItemName(item == null ? null : item.name());
            vo.setItemStatus(item == null ? null : item.status());
            if (stock != null) {
                vo.setTotalQuantity(stock.totalQuantity() == null ? BigDecimal.ZERO : stock.totalQuantity());
                vo.setWarehouses(stock.warehouses() == null ? List.of() : stock.warehouses().stream()
                        .map(PdmPartErpServiceImpl::toWarehouseVo)
                        .toList());
            }
        } catch (RuntimeException ex) {
            log.warn("ERP 库存读取降级: partId={}, erpItemCode={}, reason={}", partId, erpItemCode, ex.getMessage());
            vo.setDegraded(true);
            vo.setMessage(DEGRADED_MESSAGE);
            vo.setTotalQuantity(BigDecimal.ZERO);
            vo.setWarehouses(List.of());
        }
        return vo;
    }

    /**
     * 仓库库存转视图对象
     *
     * @param warehouse 客户端仓库库存
     * @return 视图对象
     */
    private static PdmPartErpWarehouseVO toWarehouseVo(
            com.bluewhale.pdm.integration.client.PdmIntErpStockWarehouse warehouse) {
        PdmPartErpWarehouseVO vo = new PdmPartErpWarehouseVO();
        vo.setWarehouseId(warehouse.warehouseId());
        vo.setWarehouseCode(warehouse.warehouseCode());
        vo.setWarehouseName(warehouse.warehouseName());
        vo.setQuantity(warehouse.quantity() == null ? BigDecimal.ZERO : warehouse.quantity());
        return vo;
    }
}
