package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BOM 多级用量展开计算器（纯函数）。
 *
 * <p>累计口径：{@code cum(path) = cum(parentPath) × qty × (1 + lossRate/100)}，
 * 根路径取 1；损耗率为 0–100 百分比，空值按 0；结果保留 4 位小数（HALF_UP）。</p>
 *
 * @author BlueWhale
 */
public final class MrpExpansionCalculator {

    /** 数量精度 */
    private static final int SCALE = 4;

    /** 百分比分母 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 中间计算精度 */
    private static final int INTERMEDIATE_SCALE = 6;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private MrpExpansionCalculator() {
    }

    /**
     * 按快照展开某需求行的全部子件用量。
     *
     * @param snapshot BOM 快照
     * @param demandQuantity 需求数量
     * @return 子件贡献行（按快照深度优先顺序）
     */
    public static List<MrpContribution> expand(BomSnapshot snapshot, BigDecimal demandQuantity) {
        if (snapshot == null || snapshot.getLines() == null || snapshot.getLines().isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "BOM 快照没有可用行");
        }
        if (demandQuantity == null || demandQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.QUANTITY_INVALID);
        }
        Map<String, BigDecimal> cumulative = new HashMap<>();
        List<MrpContribution> contributions = new ArrayList<>();
        for (BomLine line : snapshot.getLines()) {
            String path = line.getPath();
            if (path == null || path.isBlank()) {
                throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID, "BOM 快照存在缺少路径的行");
            }
            BigDecimal qty = line.getQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID,
                        "BOM 路径 " + path + " 的用量必须大于 0");
            }
            String parentPath = parentPath(path);
            BigDecimal parentCumulative = BigDecimal.ONE;
            if (parentPath != null) {
                parentCumulative = cumulative.get(parentPath);
                if (parentCumulative == null) {
                    throw ErpErrorCode.exception(ErpErrorCode.MRP_INVALID,
                            "BOM 路径 " + path + " 的父级路径 " + parentPath + " 不存在");
                }
            }
            BigDecimal lossRate = line.getLossRate() == null ? BigDecimal.ZERO : line.getLossRate();
            BigDecimal lossFactor = BigDecimal.ONE.add(
                    lossRate.divide(HUNDRED, INTERMEDIATE_SCALE, RoundingMode.HALF_UP));
            BigDecimal unitQty = parentCumulative.multiply(qty).multiply(lossFactor).setScale(SCALE, RoundingMode.HALF_UP);
            cumulative.put(path, unitQty);
            MrpContribution contribution = new MrpContribution();
            contribution.setChildPartNo(line.getChildPartNo());
            contribution.setChildPartName(line.getChildPartName());
            contribution.setUom(line.getUom());
            contribution.setBomPath(path);
            contribution.setDepth(line.getDepth());
            contribution.setUnitQty(unitQty);
            contribution.setLossRate(lossRate);
            contribution.setGrossQuantity(unitQty.multiply(demandQuantity).setScale(SCALE, RoundingMode.HALF_UP));
            contributions.add(contribution);
        }
        return contributions;
    }

    /**
     * 取父级路径。
     *
     * @param path 行路径
     * @return 父级路径；根路径返回 {@code null}
     */
    private static String parentPath(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? null : path.substring(0, index);
    }
}
