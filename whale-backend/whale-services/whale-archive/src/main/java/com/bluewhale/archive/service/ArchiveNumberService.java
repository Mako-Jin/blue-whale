package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.mapper.ArchiveSequenceMapper;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 档号生成服务。
 *
 * <p>档号格式 {@code BW-{门类编码}-{年度}-{流水号}}，流水号按「门类编码 + 年度」原子自增。
 * 并发下依赖 {@code archive_sequence} 的唯一键行锁，不使用应用层锁，避免多实例漂移。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ArchiveNumberService {

    /** 门类编码最大长度 */
    private static final int MAX_CATEGORY_CODE_LENGTH = 32;

    private final ArchiveSequenceMapper sequenceMapper;

    private final ArchiveProperties archiveProperties;

    /**
     * 生成下一个档号。
     *
     * <p>方法在本地事务内执行，保证 {@code allocate} 与 {@code LAST_INSERT_ID()} 复用同一数据库连接。</p>
     *
     * @param categoryCode 门类编码
     * @param year 归档年度
     * @return 档号，例如 {@code BW-GL-2026-000001}
     */
    @Transactional(rollbackFor = Exception.class)
    public String next(String categoryCode, int year) {
        String code = normalizeCategoryCode(categoryCode);
        if (year < ArchiveConstants.MIN_YEAR || year > ArchiveConstants.MAX_YEAR) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "归档年度超出允许范围");
        }
        sequenceMapper.allocate(IdWorker.getId(), code, year);
        long sequence = sequenceMapper.lastAllocatedValue();
        if (sequence <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "档号流水分配失败");
        }
        return format(code, year, sequence);
    }

    /**
     * 规范化并校验门类编码。
     *
     * @param rawCode 原始编码
     * @return 去空白并转大写后的编码
     */
    private static String normalizeCategoryCode(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "门类编码不能为空");
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() > MAX_CATEGORY_CODE_LENGTH || !code.matches(ArchiveConstants.CATEGORY_CODE_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "门类编码仅允许字母、数字与下划线且不超过 32 位");
        }
        return code;
    }

    /**
     * 按配置拼接档号。
     *
     * @param categoryCode 门类编码
     * @param year 归档年度
     * @param sequence 流水号
     * @return 档号
     */
    private String format(String categoryCode, int year, long sequence) {
        String prefix = StringUtils.hasText(archiveProperties.getNumber().getPrefix())
                ? archiveProperties.getNumber().getPrefix().trim() : "BW";
        int width = Math.min(Math.max(archiveProperties.getNumber().getSequenceWidth(),
                ArchiveConstants.MIN_SEQUENCE_WIDTH), ArchiveConstants.MAX_SEQUENCE_WIDTH);
        String paddedSequence = String.format(Locale.ROOT, "%0" + width + "d", sequence);
        return prefix + "-" + categoryCode + "-" + year + "-" + paddedSequence;
    }
}
