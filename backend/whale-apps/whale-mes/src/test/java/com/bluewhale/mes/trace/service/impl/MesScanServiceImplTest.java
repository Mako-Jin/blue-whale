package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.trace.dto.MesScanRuleSaveRequest;
import com.bluewhale.mes.trace.dto.MesScanValidateRequest;
import com.bluewhale.mes.trace.entity.MesBarcode;
import com.bluewhale.mes.trace.entity.MesScanRule;
import com.bluewhale.mes.trace.mapper.MesBarcodeMapper;
import com.bluewhale.mes.trace.mapper.MesScanRuleMapper;
import com.bluewhale.mes.trace.vo.MesScanValidateVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 扫码防错服务单元测试（M2）。
 *
 * <p>覆盖条码不存在/作废阻断、错料 BLOCK 阻断与 WARN 放行、重复扫码幂等、规则编码去重。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesScanServiceImplTest {

    /** 条码主键 */
    private static final Long BARCODE_ID = 7L;

    @Mock
    private MesBarcodeMapper barcodeMapper;

    @Mock
    private MesScanRuleMapper ruleMapper;

    @InjectMocks
    private MesScanServiceImpl scanService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesBarcode.class, MesScanRule.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 校验：条码不存在阻断
     */
    @Test
    @DisplayName("扫码校验：条码不存在阻断")
    void validateShouldBlockUnknownBarcode() {
        when(barcodeMapper.selectOne(any())).thenReturn(null);

        MesScanValidateVO result = scanService.validate(request("BW-0001", null));

        assertFalse(result.getPassed());
        assertTrue(result.getBlocked());
        assertEquals("条码不存在", result.getMessage());
    }

    /**
     * 校验：作废条码阻断
     */
    @Test
    @DisplayName("扫码校验：作废条码阻断")
    void validateShouldBlockVoidBarcode() {
        MesBarcode barcode = barcode("ITEM-1", "VOID", null);
        when(barcodeMapper.selectOne(any())).thenReturn(barcode);

        MesScanValidateVO result = scanService.validate(request("BW-0001", null));

        assertFalse(result.getPassed());
        assertTrue(result.getBlocked());
        verify(barcodeMapper, never()).updateById(any(MesBarcode.class));
    }

    /**
     * 校验：错料 BLOCK 阻断且不记扫码次数
     */
    @Test
    @DisplayName("扫码校验：错料阻断")
    void validateShouldBlockWrongItem() {
        MesBarcode barcode = barcode("ITEM-1", "ACTIVE", null);
        when(barcodeMapper.selectOne(any())).thenReturn(barcode);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule("ITEM-2", "BLOCK")));

        MesScanValidateVO result = scanService.validate(request("BW-0001", null));

        assertFalse(result.getPassed());
        assertTrue(result.getBlocked());
        assertTrue(result.getMessage().contains("ITEM-2"));
        verify(barcodeMapper, never()).updateById(any(MesBarcode.class));
    }

    /**
     * 校验：错料 WARN 放行并告警
     */
    @Test
    @DisplayName("扫码校验：错料告警放行")
    void validateShouldWarnWrongItem() {
        MesBarcode barcode = barcode("ITEM-1", "ACTIVE", null);
        when(barcodeMapper.selectOne(any())).thenReturn(barcode);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule("ITEM-2", "WARN")));
        when(barcodeMapper.updateById(any(MesBarcode.class))).thenReturn(1);

        MesScanValidateVO result = scanService.validate(request("BW-0001", null));

        assertTrue(result.getPassed());
        assertFalse(result.getBlocked());
        assertTrue(result.getMessage().contains("物料防错"));
        assertEquals(1, barcode.getScanCount());
    }

    /**
     * 校验：相同幂等键重复扫码不产生副作用
     */
    @Test
    @DisplayName("扫码校验：重复扫码幂等")
    void validateShouldBeIdempotentForSameScanNo() {
        MesBarcode barcode = barcode("ITEM-1", "ACTIVE", "SCAN-1");
        barcode.setScanCount(1);
        when(barcodeMapper.selectOne(any())).thenReturn(barcode);

        MesScanValidateVO result = scanService.validate(request("BW-0001", "SCAN-1"));

        assertTrue(result.getPassed());
        assertTrue(result.getDuplicate());
        verify(barcodeMapper, never()).updateById(any(MesBarcode.class));
    }

    /**
     * 校验：物料一致通过并累计扫码次数
     */
    @Test
    @DisplayName("扫码校验：物料一致通过")
    void validateShouldPassMatchingItem() {
        MesBarcode barcode = barcode("ITEM-1", "ACTIVE", null);
        when(barcodeMapper.selectOne(any())).thenReturn(barcode);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule("ITEM-1", "BLOCK")));
        when(barcodeMapper.updateById(any(MesBarcode.class))).thenReturn(1);

        MesScanValidateVO result = scanService.validate(request("BW-0001", "SCAN-2"));

        assertTrue(result.getPassed());
        assertEquals("SCAN-2", barcode.getLastScanNo());
        assertEquals(1, barcode.getScanCount());
    }

    /**
     * 规则保存：编码重复拒绝
     */
    @Test
    @DisplayName("扫码规则：编码重复拒绝")
    void saveRuleShouldRejectDuplicateCode() {
        when(ruleMapper.selectCount(any())).thenReturn(1L);
        MesScanRuleSaveRequest request = new MesScanRuleSaveRequest();
        request.setRuleCode("SCAN-1");
        request.setRuleName("扫码规则");

        assertThrows(BizException.class, () -> scanService.saveRule(request));
        verify(ruleMapper, never()).insert(any(MesScanRule.class));
    }

    /**
     * 构建扫码请求
     *
     * @param barcode 条码值
     * @param scanNo  幂等键
     * @return 扫码请求
     */
    private MesScanValidateRequest request(String barcode, String scanNo) {
        MesScanValidateRequest request = new MesScanValidateRequest();
        request.setBarcodeValue(barcode);
        request.setStationCode("ST-1");
        request.setOperationCode("OP-1");
        request.setScanNo(scanNo);
        return request;
    }

    /**
     * 构建条码
     *
     * @param itemCode 物料编码
     * @param status   状态
     * @param lastScanNo 最近幂等键
     * @return 条码实体
     */
    private MesBarcode barcode(String itemCode, String status, String lastScanNo) {
        MesBarcode barcode = new MesBarcode();
        barcode.setId(BARCODE_ID);
        barcode.setBarcodeValue("BW-0001");
        barcode.setBatchNo("B-100");
        barcode.setItemCode(itemCode);
        barcode.setStatus(status);
        barcode.setLastScanNo(lastScanNo);
        barcode.setScanCount(0);
        return barcode;
    }

    /**
     * 构建扫码规则
     *
     * @param expectedItemCode 期望物料
     * @param action           命中处理
     * @return 规则实体
     */
    private MesScanRule rule(String expectedItemCode, String action) {
        MesScanRule rule = new MesScanRule();
        rule.setId(9L);
        rule.setRuleCode("SCAN-1");
        rule.setStationCode("ST-1");
        rule.setOperationCode("OP-1");
        rule.setExpectedItemCode(expectedItemCode);
        rule.setAction(action);
        rule.setStatus("ENABLED");
        return rule;
    }
}
