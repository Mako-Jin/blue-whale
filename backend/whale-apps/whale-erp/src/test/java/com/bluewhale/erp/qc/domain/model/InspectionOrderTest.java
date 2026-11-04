package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InspectionOrder} 检验单不变量单元测试。
 *
 * @author BlueWhale
 */
class InspectionOrderTest {

    @Test
    @DisplayName("检验类型非法时拒绝建单")
    void shouldRejectInvalidType() {
        InspectionOrder order = order();
        order.setInspectionType("OQC");

        BizException exception = assertThrows(BizException.class, order::validateForCreate);

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("无检验项时拒绝建单")
    void shouldRejectEmptyItems() {
        InspectionOrder order = order();
        order.setItems(new ArrayList<>());

        BizException exception = assertThrows(BizException.class, order::validateForCreate);

        assertEquals(ErpErrorCode.QC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("录入检验项后进入检验中")
    void shouldEnterInspecting() {
        InspectionOrder order = order();
        order.validateForCreate();

        order.record(line(1, "PASS"));

        assertEquals(QcStatus.INSPECTION_INSPECTING, order.getStatus());
        assertEquals("PASS", order.getItems().get(0).getResult());
    }

    @Test
    @DisplayName("检验项未全部录入结论不可判定")
    void shouldRejectIncompleteJudge() {
        InspectionOrder order = order();
        order.validateForCreate();
        order.record(line(1, "PASS"));

        BizException exception = assertThrows(BizException.class, order::judge);

        assertEquals(ErpErrorCode.INSPECTION_ITEM_INCOMPLETE, exception.getCode());
    }

    @Test
    @DisplayName("全部合格判定为 PASSED 并回写已检验数量")
    void shouldJudgePassed() {
        InspectionOrder order = order();
        order.validateForCreate();
        order.record(line(1, "PASS"));
        order.record(line(2, "PASS"));

        assertTrue(order.judge());
        assertEquals(QcStatus.INSPECTION_PASSED, order.getStatus());
        assertEquals(0, order.getInspectedQuantity().compareTo(new BigDecimal("10")));
    }

    @Test
    @DisplayName("任一不合格判定为 FAILED 并必须关联不合格品单")
    void shouldJudgeFailedAndMarkNcr() {
        InspectionOrder order = order();
        order.validateForCreate();
        order.record(line(1, "PASS"));
        order.record(line(2, "FAIL"));

        assertFalse(order.judge());
        assertEquals(QcStatus.INSPECTION_FAILED, order.getStatus());

        order.markNonconformity(9L);
        assertEquals(9L, order.getNcrId());
    }

    @Test
    @DisplayName("已判定检验单不可重复判定或录入")
    void shouldRejectDuplicateJudgeAndRecord() {
        InspectionOrder order = order();
        order.validateForCreate();
        order.record(line(1, "PASS"));
        order.record(line(2, "PASS"));
        order.judge();

        BizException judgeAgain = assertThrows(BizException.class, order::judge);
        BizException recordAgain = assertThrows(BizException.class, () -> order.record(line(1, "FAIL")));

        assertEquals(ErpErrorCode.QC_STATE_CONFLICT, judgeAgain.getCode());
        assertEquals(ErpErrorCode.QC_STATE_CONFLICT, recordAgain.getCode());
    }

    @Test
    @DisplayName("合格检验单不可关联不合格品单")
    void shouldRejectNcrForPassedOrder() {
        InspectionOrder order = order();
        order.validateForCreate();
        order.record(line(1, "PASS"));
        order.record(line(2, "PASS"));
        order.judge();

        BizException exception = assertThrows(BizException.class, () -> order.markNonconformity(9L));

        assertEquals(ErpErrorCode.QC_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造待判定检验单。
     *
     * @return 检验单
     */
    private InspectionOrder order() {
        InspectionOrder order = new InspectionOrder();
        order.setOrderNo("QC-20261104-0001");
        order.setInspectionType(QcStatus.TYPE_IQC);
        order.setSourceType(QcStatus.SOURCE_PURCHASE_RECEIPT);
        order.setSourceNo("PR-001");
        order.setItemCode("M001");
        order.setItemName("螺栓");
        order.setQuantity(new BigDecimal("10"));
        order.setInspectDate(LocalDate.of(2026, 11, 4));
        order.setStatus(QcStatus.INSPECTION_DRAFT);
        order.setItems(new ArrayList<>(List.of(item(1, "外观"), item(2, "尺寸"))));
        return order;
    }

    /**
     * 构造录入行。
     *
     * @param lineNo 行号
     * @param result 结论
     * @return 录入行
     */
    private InspectionItem line(int lineNo, String result) {
        InspectionItem source = new InspectionItem();
        source.setLineNo(lineNo);
        source.setActualValue("实测");
        source.setResult(result);
        return source;
    }

    /**
     * 构造检验项。
     *
     * @param lineNo 行号
     * @param name 名称
     * @return 检验项
     */
    private InspectionItem item(int lineNo, String name) {
        InspectionItem item = new InspectionItem();
        item.setLineNo(lineNo);
        item.setItemName(name);
        item.setStandardValue("标准");
        return item;
    }
}
