package com.bluewhale.party.common.sequence;

/**
 * 业务编号取号（支部编码 / 党员编号 / 后续提案号与工会会员号）。
 *
 * @author BlueWhale
 */
public interface PartySequenceService {

    /**
     * 取下一个流水号。
     *
     * @param seqKey 编号键（BRANCH / MEMBER / …）
     * @param seqDate 周期标识（空串=不重置；yyyy=按年重置）
     * @return 从 1 起递增的流水号
     */
    long next(String seqKey, String seqDate);

    /**
     * 取下一个支部编码（{@code BR} + 年份 + 4 位流水，按年重置）。
     *
     * @return 支部编码
     */
    String nextBranchCode();

    /**
     * 取下一个党员编号（{@code MB} + 年份 + 4 位流水，按年重置）。
     *
     * @return 党员编号
     */
    String nextMemberNo();

    /**
     * 取下一个工会会员编号（{@code UN} + 年份 + 4 位流水，按年重置）。
     *
     * @return 工会会员编号
     */
    String nextUnionMemberNo();

    /**
     * 取下一个慰问帮扶编号（{@code RF} + 年份 + 4 位流水，按年重置）。
     *
     * @return 慰问帮扶编号
     */
    String nextUnionReliefNo();

    /**
     * 取下一个节日福利批次编号（{@code WF} + 年份 + 4 位流水，按年重置）。
     *
     * @return 福利批次编号
     */
    String nextUnionWelfareNo();

    /**
     * 取下一个工会活动编号（{@code UA} + 年份 + 4 位流水，按年重置）。
     *
     * @return 工会活动编号
     */
    String nextUnionActivityNo();

    /**
     * 取下一个职代会提案编号（{@code UP} + 年份 + 4 位流水，按年重置）。
     *
     * @return 提案编号
     */
    String nextUnionProposalNo();
}
