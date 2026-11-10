package com.bluewhale.party.member.application.view;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 党员结构统计视图（M-P4）。
 *
 * <p>分桶键只放「桶名」，指标编码由 analytics 拼接（如 {@code MEMBER_AGE_} + 桶名）；
 * 桶在服务层按固定顺序预置为 0，避免为空桶时看板因为「没这个键」而漏掉一档。</p>
 *
 * @author BlueWhale
 */
@Data
public class MemberMetricView {

    /** 在册党员数 */
    private long total;

    /** 年龄分桶（键：UNDER_30 / 30_39 / 40_49 / 50_59 / 60_PLUS / UNKNOWN） */
    private Map<String, Long> ageBuckets = new LinkedHashMap<>();

    /** 党龄分桶（键：UNDER_5 / 5_10 / 11_20 / 21_30 / 31_PLUS / UNKNOWN） */
    private Map<String, Long> partyAgeBuckets = new LinkedHashMap<>();

    /** 学历分桶（键：DOCTOR / MASTER / BACHELOR / COLLEGE / SECONDARY / OTHER / UNKNOWN） */
    private Map<String, Long> educationBuckets = new LinkedHashMap<>();

    /** 距转正不足提醒天数的预备党员数 */
    private long probationDue;
}
