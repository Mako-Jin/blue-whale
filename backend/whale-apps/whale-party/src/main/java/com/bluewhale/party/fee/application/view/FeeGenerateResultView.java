package com.bluewhale.party.fee.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 党费生成结果视图。
 *
 * <p>四个计数分开报：{@code created} 是本次新建账单数、{@code skipped} 是已存在或有状态无需缴、
 * {@code noRule} 是基数没匹配到档位的人。把后两者合成一个「跳过数」，
 * 运维就分不清「这个月已经生成过了」和「有 3 个人的基数没档位、需要补配置」。</p>
 *
 * @author BlueWhale
 */
@Data
public class FeeGenerateResultView {

    /** 所属月份 */
    private String periodMonth;

    /** 本次新建账单数 */
    private int created;

    /** 跳过数（已生成或无需缴纳） */
    private int skipped;

    /** 未匹配档位人数 */
    private int noRule;

    /** 本次生成账单的应缴合计 */
    private BigDecimal dueTotal = BigDecimal.ZERO;

    /** 未匹配档位的党员姓名（最多列 20 个，便于直接补配置） */
    private List<String> noRuleMembers = new ArrayList<>();
}
