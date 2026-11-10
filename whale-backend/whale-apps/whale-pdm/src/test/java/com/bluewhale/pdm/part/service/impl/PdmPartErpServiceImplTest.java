package com.bluewhale.pdm.part.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.integration.client.PdmIntErpClient;
import com.bluewhale.pdm.integration.client.PdmIntErpItemInfo;
import com.bluewhale.pdm.integration.client.PdmIntErpStockInfo;
import com.bluewhale.pdm.integration.client.PdmIntErpStockWarehouse;
import com.bluewhale.pdm.part.vo.PdmPartErpStockVO;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 零件 ERP 物料/库存只读查询单元测试
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmPartErpServiceImplTest {

    @Mock
    private PdmProductMapper productMapper;

    @Mock
    private PdmIntErpClient erpClient;

    @InjectMocks
    private PdmPartErpServiceImpl partErpService;

    @BeforeEach
    void setUpTenant() {
        com.bluewhale.common.tenant.TenantContext.set(1L);
    }

    @AfterEach
    void clearTenant() {
        com.bluewhale.common.tenant.TenantContext.clear();
    }

    /**
     * 物料与库存应映射到视图对象，优先使用 erpItemCode。
     */
    @Test
    void shouldMapErpItemAndStock() {
        when(productMapper.selectById(1L)).thenReturn(part(1L, "P-001", "ERP-001"));
        when(erpClient.getItem(1L, "ERP-001"))
                .thenReturn(new PdmIntErpItemInfo(9L, "ERP-001", "六角螺栓", "ENABLED", true));
        when(erpClient.getStock(1L, "ERP-001")).thenReturn(new PdmIntErpStockInfo(
                "ERP-001", "六角螺栓", new BigDecimal("5"),
                List.of(new PdmIntErpStockWarehouse(7L, "WH-01", "主仓", new BigDecimal("5")))));

        PdmPartErpStockVO vo = partErpService.getStock(1L);

        assertEquals("P-001", vo.getPartCode());
        assertEquals("ERP-001", vo.getErpItemCode());
        assertTrue(vo.getItemExists());
        assertEquals("六角螺栓", vo.getItemName());
        assertEquals(new BigDecimal("5"), vo.getTotalQuantity());
        assertEquals(1, vo.getWarehouses().size());
        assertEquals("主仓", vo.getWarehouses().get(0).getWarehouseName());
        assertFalse(vo.getDegraded());
    }

    /**
     * 未维护 erpItemCode 时应回退到零件编码。
     */
    @Test
    void shouldFallbackToPartCodeWhenErpItemCodeMissing() {
        when(productMapper.selectById(2L)).thenReturn(part(2L, "P-002", null));
        when(erpClient.getItem(1L, "P-002"))
                .thenReturn(new PdmIntErpItemInfo(null, "P-002", null, null, false));
        when(erpClient.getStock(1L, "P-002"))
                .thenReturn(new PdmIntErpStockInfo("P-002", null, BigDecimal.ZERO, List.of()));

        PdmPartErpStockVO vo = partErpService.getStock(2L);

        assertEquals("P-002", vo.getErpItemCode());
        assertFalse(vo.getItemExists());
        assertEquals(BigDecimal.ZERO, vo.getTotalQuantity());
        assertEquals(0, vo.getWarehouses().size());
        assertFalse(vo.getDegraded());
    }

    /**
     * ERP 调用异常时应降级返回，不向上抛错。
     */
    @Test
    void shouldDegradeWhenErpUnavailable() {
        when(productMapper.selectById(3L)).thenReturn(part(3L, "P-003", "ERP-003"));
        when(erpClient.getItem(1L, "ERP-003"))
                .thenThrow(new BizException(com.bluewhale.common.core.exception.ErrorCode.SYSTEM_ERROR, "连接失败"));

        PdmPartErpStockVO vo = partErpService.getStock(3L);

        assertTrue(vo.getDegraded());
        assertEquals("ERP 暂不可用，物料与库存信息未获取", vo.getMessage());
        assertEquals(BigDecimal.ZERO, vo.getTotalQuantity());
        assertEquals(0, vo.getWarehouses().size());
    }

    /**
     * 零件不存在应返回 NOT_FOUND。
     */
    @Test
    void shouldRejectMissingPart() {
        when(productMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> partErpService.getStock(99L));
    }

    /**
     * 构造零件
     */
    private static PdmProduct part(Long id, String code, String erpItemCode) {
        PdmProduct part = new PdmProduct();
        part.setId(id);
        part.setCode(code);
        part.setName("六角螺栓");
        part.setErpItemCode(erpItemCode);
        part.setStatus("ENABLED");
        return part;
    }
}
