package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mes.trace.dto.MesBarcodeGenerateRequest;
import com.bluewhale.mes.trace.dto.MesBarcodeRuleSaveRequest;
import com.bluewhale.mes.trace.entity.MesBarcode;
import com.bluewhale.mes.trace.entity.MesBarcodeRule;
import com.bluewhale.mes.trace.entity.MesMaterialBatch;
import com.bluewhale.mes.trace.mapper.MesBarcodeMapper;
import com.bluewhale.mes.trace.mapper.MesBarcodeRuleMapper;
import com.bluewhale.mes.trace.mapper.MesMaterialBatchMapper;
import com.bluewhale.mes.trace.vo.MesBarcodeVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 条码服务单元测试（M2）。
 *
 * <p>覆盖条码批量生成的唯一性与绑定关系、停用规则/封批批次拒绝、作废幂等与规则编码去重。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesBarcodeServiceImplTest {

    /** 规则 ID */
    private static final Long RULE_ID = 3L;

    /** 批次 ID */
    private static final Long BATCH_ID = 5L;

    @Mock
    private MesBarcodeMapper barcodeMapper;

    @Mock
    private MesBarcodeRuleMapper ruleMapper;

    @Mock
    private MesMaterialBatchMapper batchMapper;

    @InjectMocks
    private MesBarcodeServiceImpl barcodeService;

    /**
     * 初始化 MyBatis-Plus 表元数据
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(MesBarcode.class, MesBarcodeRule.class, MesMaterialBatch.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    /**
     * 批量生成：条码值互不重复且绑定同一批次
     */
    @Test
    @DisplayName("条码生成：条码值唯一并绑定批次")
    void generateShouldProduceUniqueBarcodes() {
        when(ruleMapper.selectById(RULE_ID)).thenReturn(rule("ENABLED"));
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch("ACTIVE"));
        when(barcodeMapper.selectCount(any())).thenReturn(0L);
        when(barcodeMapper.insert(any(MesBarcode.class))).thenReturn(1);

        MesBarcodeGenerateRequest request = new MesBarcodeGenerateRequest();
        request.setRuleId(RULE_ID);
        request.setBatchId(BATCH_ID);
        request.setQuantity(3);
        List<MesBarcodeVO> result = barcodeService.generate(request);

        assertEquals(3, result.size());
        Set<String> values = new HashSet<>();
        for (MesBarcodeVO vo : result) {
            assertTrue(values.add(vo.getBarcodeValue()), "条码值必须唯一");
            assertEquals("BATCH-1", vo.getBatchNo());
            assertEquals("ITEM-1", vo.getItemCode());
            assertNotNull(vo.getPrintPayload());
        }
        verify(barcodeMapper, times(3)).insert(any(MesBarcode.class));
    }

    /**
     * 生成：停用规则拒绝
     */
    @Test
    @DisplayName("条码生成：停用规则拒绝")
    void generateShouldRejectDisabledRule() {
        when(ruleMapper.selectById(RULE_ID)).thenReturn(rule("DISABLED"));
        MesBarcodeGenerateRequest request = new MesBarcodeGenerateRequest();
        request.setRuleId(RULE_ID);
        request.setBatchId(BATCH_ID);
        request.setQuantity(1);

        assertThrows(BizException.class, () -> barcodeService.generate(request));
        verify(barcodeMapper, never()).insert(any(MesBarcode.class));
    }

    /**
     * 生成：封批批次拒绝
     */
    @Test
    @DisplayName("条码生成：封批批次拒绝")
    void generateShouldRejectClosedBatch() {
        when(ruleMapper.selectById(RULE_ID)).thenReturn(rule("ENABLED"));
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batch("CLOSED"));
        MesBarcodeGenerateRequest request = new MesBarcodeGenerateRequest();
        request.setRuleId(RULE_ID);
        request.setBatchId(BATCH_ID);
        request.setQuantity(1);

        assertThrows(BizException.class, () -> barcodeService.generate(request));
    }

    /**
     * 作废：已作废条码幂等返回
     */
    @Test
    @DisplayName("条码作废：幂等")
    void voidBarcodeShouldBeIdempotent() {
        MesBarcode barcode = new MesBarcode();
        barcode.setId(9L);
        barcode.setStatus("VOID");
        when(barcodeMapper.selectById(9L)).thenReturn(barcode);

        barcodeService.voidBarcode(9L);

        verify(barcodeMapper, never()).updateById(any(MesBarcode.class));
    }

    /**
     * 规则保存：编码重复拒绝
     */
    @Test
    @DisplayName("条码规则：编码重复拒绝")
    void saveRuleShouldRejectDuplicateCode() {
        when(ruleMapper.selectCount(any())).thenReturn(1L);
        MesBarcodeRuleSaveRequest request = new MesBarcodeRuleSaveRequest();
        request.setRuleCode("RULE-1");
        request.setRuleName("规则一");
        request.setPrefix("BW");

        assertThrows(BizException.class, () -> barcodeService.saveRule(request));
        verify(ruleMapper, never()).insert(any(MesBarcodeRule.class));
    }

    /**
     * 构建测试规则
     *
     * @param status 状态
     * @return 规则实体
     */
    private MesBarcodeRule rule(String status) {
        MesBarcodeRule rule = new MesBarcodeRule();
        rule.setId(RULE_ID);
        rule.setRuleCode("RULE-1");
        rule.setRuleName("规则一");
        rule.setPrefix("BW");
        rule.setCodeType("CODE128");
        rule.setSerialLength(6);
        rule.setStatus(status);
        return rule;
    }

    /**
     * 构建测试批次
     *
     * @param status 状态
     * @return 批次实体
     */
    private MesMaterialBatch batch(String status) {
        MesMaterialBatch batch = new MesMaterialBatch();
        batch.setId(BATCH_ID);
        batch.setBatchNo("BATCH-1");
        batch.setItemCode("ITEM-1");
        batch.setQuantity(new BigDecimal("100"));
        batch.setConsumedQuantity(BigDecimal.ZERO);
        batch.setStatus(status);
        return batch;
    }
}
