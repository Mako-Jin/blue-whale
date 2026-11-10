package com.bluewhale.party.fee.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 党费标准（基数区间 → 比例）。
 *
 * <p>区间语义：{@code minBase <= 基数 < maxBase}，{@code maxBase} 为空表示无上限。
 * 档位必须<b>互不重叠</b>：重叠时同一基数会命中两条规则，取哪条取决于数据库返回顺序，
 * 同一个人每月缴的钱会在两次生成之间随机变化。</p>
 *
 * @author BlueWhale
 */
@Data
public class FeeRule {

    /** 比例保留小数位 */
    public static final int RATE_SCALE = 4;

    /** 档位名称最大长度 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 主键 */
    private Long id;

    /** 档位名称 */
    private String ruleName;

    /** 基数下限（含） */
    private BigDecimal minBase;

    /** 基数上限（不含），空表示无上限 */
    private BigDecimal maxBase;

    /** 缴费比例 */
    private BigDecimal rate;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /**
     * 校验档位自身合法性。
     */
    public void validate() {
        if (ruleName == null || ruleName.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "档位名称不能为空");
        }
        if (ruleName.trim().length() > NAME_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID,
                    "档位名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (minBase == null) {
            minBase = BigDecimal.ZERO;
        }
        if (minBase.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "基数下限不能为负");
        }
        if (maxBase != null && maxBase.compareTo(minBase) <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "基数上限必须大于下限");
        }
        if (rate == null || rate.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "缴费比例不能为负");
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "缴费比例不能大于 100%");
        }
        rate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        if (status == null || (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status))) {
            status = "ACTIVE";
        } else {
            status = status.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (sortNo == null) {
            sortNo = 0;
        }
        ruleName = ruleName.trim();
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * 该基数是否落在本档位内。
     *
     * @param base 党费基数
     * @return 命中返回 true
     */
    public boolean matches(BigDecimal base) {
        if (!isActive() || base == null) {
            return false;
        }
        if (base.compareTo(minBase) < 0) {
            return false;
        }
        return maxBase == null || base.compareTo(maxBase) < 0;
    }

    /**
     * 判断与另一档位是否区间重叠。
     *
     * @param other 另一档位
     * @return 重叠返回 true
     */
    public boolean overlaps(FeeRule other) {
        if (other == null) {
            return false;
        }
        BigDecimal thisMax = maxBase;
        BigDecimal otherMax = other.getMaxBase();
        boolean lowerBoundOk = thisMax == null || thisMax.compareTo(other.getMinBase()) > 0;
        boolean upperBoundOk = otherMax == null || otherMax.compareTo(this.minBase) > 0;
        return lowerBoundOk && upperBoundOk;
    }
}
