package com.bluewhale.party.integration.application.view;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知扫描结果（M-P4）。
 *
 * <p>按场景回实际发出的条数：重复扫描时已发场景会返回 0，
 * 这正是「幂等重发不重复」的可观测证据。</p>
 *
 * @author BlueWhale
 */
@Data
public class NoticeScanResultView {

    /** 各场景实际发出条数（场景 → 条数） */
    private Map<String, Integer> sentByScene = new LinkedHashMap<>();

    /** 合计发出条数 */
    private int totalSent;
}
