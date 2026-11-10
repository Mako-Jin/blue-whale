package com.bluewhale.party.fee.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * 党费计算领域服务（无状态，纯函数）。
 *
 * <p>计算规则只有两条，但都容易写错：</p>
 * <ol>
 *   <li><b>档位匹配按下限升序取第一条命中</b>：档位本身已保证不重叠，这里排序只是为了
 *       让结果与数据库返回顺序无关——重叠档位一旦漏配校验，也会稳定命中同一档而不是随机。</li>
 *   <li><b>金额四舍五入到分</b>：用 {@code HALF_UP} 而不是默认的 {@code HALF_EVEN}，
 *       否则 0.005 会舍成 0.00，长期看每次少收半分。</li>
 * </ol>
 *
 * @author BlueWhale
 */
public final class DuesCalculator {

    /** 金额保留小数位（与表列 DECIMAL(12,2) 一致） */
    public static final int AMOUNT_SCALE = 2;

    /**
     * 私有构造器：领域服务，禁止实例化。
     */
    private DuesCalculator() {
    }

    /**
     * 按基数匹配档位。
     *
     * @param base 党费基数
     * @param rules 启用中的档位集合
     * @return 命中的档位
     */
    public static FeeRule match(BigDecimal base, List<FeeRule> rules) {
        BigDecimal safeBase = base == null ? BigDecimal.ZERO : base;
        if (rules == null || rules.isEmpty()) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_RULE_MISSING);
        }
        return rules.stream()
                .filter(rule -> rule.matches(safeBase))
                .min(Comparator.comparing(FeeRule::getMinBase))
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.FEE_RULE_MISSING,
                        "党费基数 " + safeBase.toPlainString() + " 未匹配到任何档位"));
    }

    /**
     * 按基数计算应缴金额。
     *
     * @param base 党费基数
     * @param rule 命中的档位
     * @return 应缴金额（保留两位小数）
     */
    public static BigDecimal dueOf(BigDecimal base, FeeRule rule) {
        if (rule == null) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_RULE_MISSING);
        }
        BigDecimal safeBase = base == null ? BigDecimal.ZERO : base;
        if (safeBase.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "党费基数不能为负");
        }
        return safeBase.multiply(rule.getRate()).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按基数与档位集合直接算应缴金额。
     *
     * @param base 党费基数
     * @param rules 启用中的档位集合
     * @return 应缴金额
     */
    public static BigDecimal dueOf(BigDecimal base, List<FeeRule> rules) {
        return dueOf(base, match(base, rules));
    }
}
