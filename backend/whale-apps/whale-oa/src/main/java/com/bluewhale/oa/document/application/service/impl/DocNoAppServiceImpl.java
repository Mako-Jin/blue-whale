package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.domain.repository.SequenceRepository;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocNoAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocNoRule;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 公文文号应用服务实现。
 *
 * <p>文号按规则生成：{@code prefix + 期间 + separator + 补零流水}；
 * 期间为 {@code NONE} 时省略期间与分隔符；流水由 {@code oa_sequence} 原子递增。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DocNoAppServiceImpl implements DocNoAppService {

    /** 合法适用范围 */
    private static final Set<String> DOC_TYPES = Set.of(DocStatus.TYPE_OUTGOING, DocStatus.TYPE_INCOMING);

    /** 合法期间格式 */
    private static final Set<String> PERIOD_FORMATS = Set.of(
            DocStatus.PERIOD_YEAR, DocStatus.PERIOD_MONTH, DocStatus.PERIOD_NONE);

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT);

    /** 无期间时序列使用的固定期间键 */
    private static final String PERIOD_ALL = "ALL";

    /** 默认规则编码：发文 */
    private static final String DEFAULT_OUTGOING_RULE = "OUT_DEFAULT";

    /** 默认规则编码：收文 */
    private static final String DEFAULT_INCOMING_RULE = "IN_DEFAULT";

    /** 回落文号前缀 */
    private static final String FALLBACK_PREFIX = "BW-DOC-";

    private final DocumentRepository repository;

    private final SequenceRepository sequenceRepository;

    @Override
    public List<DocumentViews.NoRule> all() {
        return repository.listRules().stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.NoRule create(DocumentCommands.NoRuleSave command) {
        if (repository.findRuleByCode(command.getCode().trim()) != null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NO_RULE_EXISTS);
        }
        DocNoRule rule = new DocNoRule();
        apply(rule, command);
        repository.saveRule(rule);
        return toView(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.NoRule update(Long id, DocumentCommands.NoRuleSave command) {
        DocNoRule rule = repository.findRule(id);
        if (rule == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NO_RULE_NOT_FOUND);
        }
        DocNoRule sameCode = repository.findRuleByCode(command.getCode().trim());
        if (sameCode != null && !sameCode.getId().equals(id)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NO_RULE_EXISTS);
        }
        apply(rule, command);
        repository.updateRule(rule);
        return toView(rule);
    }

    @Override
    public String nextNo(String ruleCode) {
        DocNoRule rule = StringUtils.hasText(ruleCode) ? repository.findRuleByCode(ruleCode.trim()) : null;
        if (rule == null || rule.getEnabled() == null || rule.getEnabled() != 1) {
            return fallbackNo();
        }
        String periodFormat = normalizePeriod(rule.getPeriodFormat());
        String period = periodValue(periodFormat);
        String sequencePeriod = period.isEmpty() ? PERIOD_ALL : period;
        int width = rule.getSerialLength() == null ? 3 : Math.min(6, Math.max(3, rule.getSerialLength()));
        long value = sequenceRepository.nextValue("DOC:" + rule.getCode(), sequencePeriod);
        String serial = String.format(Locale.ROOT, "%0" + width + "d", value);
        String prefix = rule.getPrefix() == null ? "" : rule.getPrefix();
        String separator = rule.getSeparator() == null ? "" : rule.getSeparator();
        if (period.isEmpty()) {
            return prefix + serial;
        }
        return prefix + period + separator + serial;
    }

    /**
     * 生成回落文号：固定前缀 + 年月 + 三位流水。
     *
     * @return 回落文号
     */
    private String fallbackNo() {
        String period = LocalDate.now().format(MONTH_FORMATTER);
        long value = sequenceRepository.nextValue("DOC:FALLBACK", period);
        return FALLBACK_PREFIX + period + "-" + String.format(Locale.ROOT, "%03d", value);
    }

    /**
     * 用保存命令回填文号规则，并归一化单据类型、期间与流水长度等字段。
     *
     * @param rule 待填充的文号规则
     * @param command 文号规则保存命令
     */
    private void apply(DocNoRule rule, DocumentCommands.NoRuleSave command) {
        rule.setCode(command.getCode().trim());
        rule.setName(command.getName().trim());
        rule.setDocType(normalizeDocType(command.getDocType()));
        rule.setPrefix(StringUtils.hasText(command.getPrefix()) ? command.getPrefix() : FALLBACK_PREFIX);
        rule.setPeriodFormat(normalizePeriod(command.getPeriodFormat()));
        rule.setSerialLength(command.getSerialLength() == null ? 3 : command.getSerialLength());
        rule.setSeparator(command.getSeparator() == null ? "-" : command.getSeparator());
        rule.setEnabled(command.getEnabled() == null ? 1 : (command.getEnabled() == 0 ? 0 : 1));
        rule.setRemark(command.getRemark());
    }

    /**
     * 归一化文号适用单据类型，非法值抛出业务异常。
     *
     * @param docType 原始单据类型
     * @return 归一化后的单据类型
     */
    private String normalizeDocType(String docType) {
        String value = StringUtils.hasText(docType) ? docType.trim().toUpperCase() : DocStatus.TYPE_OUTGOING;
        if (!DOC_TYPES.contains(value)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }
        return value;
    }

    /**
     * 归一化文号期间格式，非法值抛出业务异常。
     *
     * @param periodFormat 原始期间格式
     * @return 归一化后的期间格式
     */
    private String normalizePeriod(String periodFormat) {
        String value = StringUtils.hasText(periodFormat)
                ? periodFormat.trim().toUpperCase() : DocStatus.PERIOD_MONTH;
        if (!PERIOD_FORMATS.contains(value)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }
        return value;
    }

    /**
     * 按期间格式计算当前期间值（年或年月），无期间时返回空串。
     *
     * @param periodFormat 期间格式
     * @return 当前期间值
     */
    private String periodValue(String periodFormat) {
        return switch (periodFormat) {
            case DocStatus.PERIOD_YEAR -> LocalDate.now().format(YEAR_FORMATTER);
            case DocStatus.PERIOD_MONTH -> LocalDate.now().format(MONTH_FORMATTER);
            default -> "";
        };
    }

    /**
     * 将文号规则领域对象转换为视图对象。
     *
     * @param rule 文号规则领域对象
     * @return 文号规则视图
     */
    private DocumentViews.NoRule toView(DocNoRule rule) {
        DocumentViews.NoRule view = new DocumentViews.NoRule();
        BeanUtils.copyProperties(rule, view);
        return view;
    }
}
