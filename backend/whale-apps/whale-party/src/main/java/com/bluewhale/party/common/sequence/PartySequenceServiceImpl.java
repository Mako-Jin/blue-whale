package com.bluewhale.party.common.sequence;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 业务编号取号实现（基于 {@code pty_sequence} 行锁 + 原子自增）。
 *
 * <p>为什么不用「查最大编号 + 1」：支部编码与党员编号都带年份分段，
 * 用 {@code MAX()} 取号在删除过记录后会回退，且必须先知道全部编码格式才能解析，
 * 一旦将来编码规则变更，老数据的解析就会算错。流水表只做 +1，与业务编码格式解耦。</p>
 *
 * <p>为什么必须包在事务里：{@code SELECT ... FOR UPDATE} 的行锁只有在事务提交时才释放，
 * 若自动提交，两个并发事务会先后读到同一个值再各自 +1，发出重复编号。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PartySequenceServiceImpl implements PartySequenceService {

    /** 并发冲突时的最大重试次数（正常路径不会走到第二次） */
    private static final int MAX_ATTEMPTS = 5;

    /** 支部编码前缀 */
    private static final String BRANCH_CODE_PREFIX = "BR";

    /** 党员编号前缀 */
    private static final String MEMBER_CODE_PREFIX = "MB";

    /** 支部编号键 */
    private static final String SEQ_KEY_BRANCH = "BRANCH";

    /** 党员编号键 */
    private static final String SEQ_KEY_MEMBER = "MEMBER";

    /** 工会会员编号键 */
    private static final String SEQ_KEY_UNION_MEMBER = "UNION_MEMBER";

    /** 慰问帮扶编号键 */
    private static final String SEQ_KEY_UNION_RELIEF = "UNION_RELIEF";

    /** 节日福利批次编号键 */
    private static final String SEQ_KEY_UNION_WELFARE = "UNION_WELFARE";

    /** 工会活动编号键 */
    private static final String SEQ_KEY_UNION_ACTIVITY = "UNION_ACTIVITY";

    /** 职代会提案编号键 */
    private static final String SEQ_KEY_UNION_PROPOSAL = "UNION_PROPOSAL";

    /** 工会会员编号前缀 */
    private static final String UNION_MEMBER_CODE_PREFIX = "UN";

    /** 慰问帮扶编号前缀 */
    private static final String UNION_RELIEF_CODE_PREFIX = "RF";

    /** 节日福利批次编号前缀 */
    private static final String UNION_WELFARE_CODE_PREFIX = "WF";

    /** 工会活动编号前缀 */
    private static final String UNION_ACTIVITY_CODE_PREFIX = "UA";

    /** 职代会提案编号前缀 */
    private static final String UNION_PROPOSAL_CODE_PREFIX = "UP";

    /** 流水号补零宽度 */
    private static final int SERIAL_WIDTH = 4;

    /** 编号键最大长度（与表列宽一致） */
    private static final int KEY_MAX_LENGTH = 64;

    private final SequenceMapper sequenceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long next(String seqKey, String seqDate) {
        String key = normalizeKey(seqKey);
        String period = seqDate == null ? "" : seqDate.trim();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long current = sequenceMapper.lockCurrentSeq(key, period);
            if (current == null) {
                if (createRow(key, period)) {
                    return 1L;
                }
                continue;
            }
            sequenceMapper.increment(key, period);
            return current + 1;
        }
        throw PartyErrorCode.exception(PartyErrorCode.SEQUENCE_INVALID);
    }

    @Override
    public String nextBranchCode() {
        int year = LocalDate.now().getYear();
        long serial = next(SEQ_KEY_BRANCH, String.valueOf(year));
        return BRANCH_CODE_PREFIX + year + pad(serial);
    }

    @Override
    public String nextMemberNo() {
        int year = LocalDate.now().getYear();
        long serial = next(SEQ_KEY_MEMBER, String.valueOf(year));
        return MEMBER_CODE_PREFIX + year + pad(serial);
    }

    @Override
    public String nextUnionMemberNo() {
        return nextYearlyCode(SEQ_KEY_UNION_MEMBER, UNION_MEMBER_CODE_PREFIX);
    }

    @Override
    public String nextUnionReliefNo() {
        return nextYearlyCode(SEQ_KEY_UNION_RELIEF, UNION_RELIEF_CODE_PREFIX);
    }

    @Override
    public String nextUnionWelfareNo() {
        return nextYearlyCode(SEQ_KEY_UNION_WELFARE, UNION_WELFARE_CODE_PREFIX);
    }

    @Override
    public String nextUnionActivityNo() {
        return nextYearlyCode(SEQ_KEY_UNION_ACTIVITY, UNION_ACTIVITY_CODE_PREFIX);
    }

    @Override
    public String nextUnionProposalNo() {
        return nextYearlyCode(SEQ_KEY_UNION_PROPOSAL, UNION_PROPOSAL_CODE_PREFIX);
    }

    /**
     * 按年取号并拼接前缀。
     *
     * @param seqKey 编号键
     * @param prefix 编码前缀
     * @return 前缀 + 年份 + 4 位流水
     */
    private String nextYearlyCode(String seqKey, String prefix) {
        int year = LocalDate.now().getYear();
        long serial = next(seqKey, String.valueOf(year));
        return prefix + year + pad(serial);
    }

    /**
     * 新建流水行（流水号置为 1）。
     *
     * <p>并发下可能被他线程抢先插入，此时捕获唯一键冲突并让调用方重试
     * ——重试会读到对方插好的行，走自增分支，不会重复发号。</p>
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识
     * @return 插入成功返回 true，被并发抢先返回 false
     */
    private boolean createRow(String seqKey, String seqDate) {
        SequenceEntity entity = new SequenceEntity();
        entity.setSeqKey(seqKey);
        entity.setSeqDate(seqDate);
        entity.setCurrentSeq(1L);
        entity.setRemark("党政工会业务编号流水");
        try {
            sequenceMapper.insert(entity);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    /**
     * 校验并归一编号键。
     *
     * @param seqKey 编号键
     * @return 大写去空白的编号键
     */
    private String normalizeKey(String seqKey) {
        if (seqKey == null || seqKey.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.SEQUENCE_INVALID, "编号键不能为空");
        }
        String key = seqKey.trim().toUpperCase(java.util.Locale.ROOT);
        if (key.length() > KEY_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.SEQUENCE_INVALID,
                    "编号键不得超过 " + KEY_MAX_LENGTH + " 字符");
        }
        return key;
    }

    /**
     * 流水号左补零（超过宽度时按实际位数输出，不截断）。
     *
     * @param serial 流水号
     * @return 补零后的文本
     */
    private String pad(long serial) {
        return String.format("%0" + SERIAL_WIDTH + "d", serial);
    }
}
