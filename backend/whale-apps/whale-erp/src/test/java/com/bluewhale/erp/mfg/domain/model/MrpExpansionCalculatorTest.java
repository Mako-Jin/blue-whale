package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MrpExpansionCalculator} 单元测试。
 *
 * @author BlueWhale
 */
class MrpExpansionCalculatorTest {

    @Test
    @DisplayName("多级展开：父级路径累计 + 损耗率")
    void shouldExpandMultiLevelWithLossRate() {
        BomSnapshot snapshot = snapshot();

        List<MrpContribution> contributions = MrpExpansionCalculator.expand(snapshot, new BigDecimal("10"));

        assertEquals(3, contributions.size());
        assertEquals(0, contributions.get(0).getUnitQty().compareTo(new BigDecimal("2.2000")));
        assertEquals(0, contributions.get(0).getGrossQuantity().compareTo(new BigDecimal("22.0000")));
        assertEquals(0, contributions.get(1).getUnitQty().compareTo(new BigDecimal("6.6000")));
        assertEquals(0, contributions.get(1).getGrossQuantity().compareTo(new BigDecimal("66.0000")));
        assertEquals(0, contributions.get(2).getUnitQty().compareTo(new BigDecimal("5")));
        assertEquals(0, contributions.get(2).getGrossQuantity().compareTo(new BigDecimal("50")));
        assertEquals("1.1", contributions.get(1).getBomPath());
        assertEquals(2, contributions.get(1).getDepth());
    }

    @Test
    @DisplayName("空损耗率按 0 处理")
    void shouldTreatMissingLossRateAsZero() {
        BomSnapshot snapshot = snapshot();

        List<MrpContribution> contributions = MrpExpansionCalculator.expand(snapshot, BigDecimal.ONE);

        assertEquals(0, contributions.get(2).getUnitQty().compareTo(new BigDecimal("5")));
    }

    @Test
    @DisplayName("父级路径缺失时拒绝展开")
    void shouldRejectBrokenPath() {
        BomSnapshot snapshot = new BomSnapshot();
        BomLine line = new BomLine();
        line.setPath("1.1");
        line.setDepth(2);
        line.setParentPartNo("SA");
        line.setChildPartNo("C1");
        line.setQty(BigDecimal.TEN);
        snapshot.setLines(List.of(line));

        assertThrows(BizException.class,
                () -> MrpExpansionCalculator.expand(snapshot, BigDecimal.ONE));
    }

    @Test
    @DisplayName("空快照拒绝展开")
    void shouldRejectEmptySnapshot() {
        BomSnapshot snapshot = new BomSnapshot();

        assertThrows(BizException.class,
                () -> MrpExpansionCalculator.expand(snapshot, BigDecimal.ONE));
    }

    /**
     * 构造两子件、两层级的快照。
     *
     * @return 快照
     */
    private BomSnapshot snapshot() {
        BomSnapshot snapshot = new BomSnapshot();
        snapshot.setProductCode("P1");
        snapshot.setLines(List.of(
                line("1", 1, "P1", "SA", "2", "10"),
                line("1.1", 2, "SA", "C1", "3", null),
                line("2", 1, "P1", "C2", "5", null)));
        return snapshot;
    }

    /**
     * 构造 BOM 行。
     *
     * @param path 路径
     * @param depth 层级
     * @param parent 父件
     * @param child 子件
     * @param qty 用量
     * @param lossRate 损耗率
     * @return BOM 行
     */
    private BomLine line(String path, int depth, String parent, String child, String qty, String lossRate) {
        BomLine line = new BomLine();
        line.setPath(path);
        line.setDepth(depth);
        line.setParentPartNo(parent);
        line.setChildPartNo(child);
        line.setChildPartName(child + "名称");
        line.setQty(new BigDecimal(qty));
        line.setUom("PCS");
        line.setLossRate(lossRate == null ? null : new BigDecimal(lossRate));
        return line;
    }
}
