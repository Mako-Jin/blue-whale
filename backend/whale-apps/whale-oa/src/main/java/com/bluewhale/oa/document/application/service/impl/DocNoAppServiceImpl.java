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
     * 生成兜底文号，格式为 {@code 前缀 + yyyyMM + "-" + 三位序号}。
     *
     * @return 兜底文号
     */
    private String fallbackNo() {
        String period = LocalDate.now().format(MONTH_FORMATTER);
        long value = sequenceRepository.nextValue("DOC:FALLBACK", period);
        return FALLBACK_PREFIX + period + "-" + String.format(Locale.ROOT, "%03d", value);
    }

    /**
     * 将保存命令写入编号规则实体，并对可选字段填入默认值。
     *
     * @param rule 编号规则实体
     * @param command 编号规则保存命令
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
     * 规范化文种，缺省为发文，非法文种抛出业务异常。
     *
     * @param docType 文种
     * @return 规范化后的文种
     */
    private String normalizeDocType(String docType) {
        String value = StringUtils.hasText(docType) ? docType.trim().toUpperCase() : DocStatus.TYPE_OUTGOING;
        if (!DOC_TYPES.contains(value)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }
        return value;
    }

    /**
     * 规范化期间格式，缺省为按月，非法格式抛出业务异常。
     *
     * @param periodFormat 期间格式
     * @return 规范化后的期间格式
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
     * 按期间格式计算当前期间值，无期间时返回空串。
     *
     * @param periodFormat 期间格式
     * @return 期间值
     */
    private String periodValue(String periodFormat) {
        return switch (periodFormat) {
            case DocStatus.PERIOD_YEAR -> LocalDate.now().format(YEAR_FORMATTER);
            case DocStatus.PERIOD_MONTH -> LocalDate.now().format(MONTH_FORMATTER);
            default -> "";
        };
    }

    /**
     * 将编号规则实体转换为编号规则视图。
     *
     * @param rule 编号规则实体
     * @return 编号规则视图
     */
    private DocumentViews.NoRule toView(DocNoRule rule) {
        DocumentViews.NoRule view = new DocumentViews.NoRule();
        BeanUtils.copyProperties(rule, view);
        return view;
    }
}
