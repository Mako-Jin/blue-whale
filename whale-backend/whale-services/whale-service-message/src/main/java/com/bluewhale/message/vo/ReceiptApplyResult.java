package com.bluewhale.message.vo;

import lombok.Data;

/**
 * 回执上报结果。
 *
 * @author BlueWhale
 */
@Data
public class ReceiptApplyResult {

    /** 收到条数 */
    private int received;

    /** 落库条数（含未匹配） */
    private int accepted;

    /** 幂等命中条数（重复上报，未改动任何状态） */
    private int duplicated;

    /** 未匹配到投递记录的条数 */
    private int unmatched;

    /** 实际推进了投递状态的条数 */
    private int advanced;

    /** 进入回调任务队列的条数 */
    private int callbackQueued;
}
