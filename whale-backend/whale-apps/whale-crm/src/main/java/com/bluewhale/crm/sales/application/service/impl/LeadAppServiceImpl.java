package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.domain.model.CustomerLevel;
import com.bluewhale.crm.sales.application.command.LeadActivityCreateCommand;
import com.bluewhale.crm.sales.application.command.LeadAssignCommand;
import com.bluewhale.crm.sales.application.command.LeadConvertCommand;
import com.bluewhale.crm.sales.application.command.LeadInvalidCommand;
import com.bluewhale.crm.sales.application.command.LeadSaveCommand;
import com.bluewhale.crm.sales.application.command.LeadStatusCommand;
import com.bluewhale.crm.sales.application.service.LeadAppService;
import com.bluewhale.crm.sales.application.view.LeadActivityView;
import com.bluewhale.crm.sales.application.view.LeadConvertResultView;
import com.bluewhale.crm.sales.application.view.LeadView;
import com.bluewhale.crm.sales.domain.model.Lead;
import com.bluewhale.crm.sales.domain.model.LeadActivity;
import com.bluewhale.crm.sales.domain.model.LeadQuery;
import com.bluewhale.crm.sales.domain.model.LeadStatus;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.model.OpportunityStageHistory;
import com.bluewhale.crm.sales.domain.repository.LeadActivityRepository;
import com.bluewhale.crm.sales.domain.repository.LeadRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityStageHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 线索应用服务实现（M2）。
 *
 * <p>线索转化：未指定客户时按线索信息新建客户，并同步创建首个商机；
 * 转化使用条件更新保证幂等，重复请求返回既有转化结果。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadAppServiceImpl implements LeadAppService {

    /** 线索编号前缀 */
    private static final String LEAD_PREFIX = "LEAD";

    /** 线索编号年份格式 */
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    /** 允许的活动渠道 */
    private static final Set<String> CHANNELS = Set.of("CALL", "VISIT", "EMAIL", "IM", "WECHAT", "OTHER");

    /** 线索编号序号长度 */
    private static final int SEQUENCE_LENGTH = 6;

    private final LeadRepository leadRepository;

    private final LeadActivityRepository activityRepository;

    private final OpportunityRepository opportunityRepository;

    private final OpportunityStageHistoryRepository historyRepository;

    private final CustomerAppService customerAppService;

    private final CrmProperties properties;

    @Override
    public PageResult<LeadView> page(LeadQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        List<Lead> leads = leadRepository.page(query);
        long total = leadRepository.count(query);
        List<LeadView> views = leads.stream()
                .map(lead -> LeadView.from(lead, actor.canViewSensitive()))
                .toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    public LeadView detail(Long id, CrmActor actor) {
        assertGate();
        Lead lead = requireLead(id, actor);
        return LeadView.from(lead, actor.canViewSensitive());
    }

    @Override
    public List<LeadView> duplicateCheck(String phone, String email) {
        assertGate();
        List<Lead> duplicates = leadRepository.findDuplicates(normalizePhone(phone), normalizeEmail(email), null);
        return duplicates.stream().map(lead -> LeadView.from(lead, false)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadView create(LeadSaveCommand command, CrmActor actor) {
        assertGate();
        Long actorId = actor.requireUserId();
        Lead lead = new Lead();
        lead.setLeadNo(nextLeadNo());
        lead.setName(command.getName().trim());
        lead.setSource(trimToNull(command.getSource()));
        lead.setIndustry(trimToNull(command.getIndustry()));
        lead.setRegion(trimToNull(command.getRegion()));
        lead.setContactName(trimToNull(command.getContactName()));
        lead.setContactPhone(trimToNull(command.getContactPhone()));
        lead.setContactEmail(trimToNull(command.getContactEmail()));
        lead.setStatus(LeadStatus.NEW);
        lead.setOwnerId(resolveOwner(command.getOwnerId(), actorId, actor));
        lead.setDeptId(command.getDeptId());
        lead.setRemark(trimToNull(command.getRemark()));
        lead.setNormalizedName(normalizeName(lead.getName()));
        lead.setNormalizedPhone(normalizePhone(lead.getContactPhone()));
        lead.setNormalizedEmail(normalizeEmail(lead.getContactEmail()));
        assertNoDuplicate(lead, null, command.getConfirmedDuplicate());
        leadRepository.insert(lead);
        return LeadView.from(lead, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadView update(Long id, LeadSaveCommand command, CrmActor actor) {
        assertGate();
        Lead lead = requireLead(id, actor);
        if (lead.getStatus() != null && lead.getStatus().terminal()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        lead.setName(command.getName().trim());
        lead.setSource(trimToNull(command.getSource()));
        lead.setIndustry(trimToNull(command.getIndustry()));
        lead.setRegion(trimToNull(command.getRegion()));
        lead.setContactName(trimToNull(command.getContactName()));
        lead.setContactPhone(trimToNull(command.getContactPhone()));
        lead.setContactEmail(trimToNull(command.getContactEmail()));
        lead.setRemark(trimToNull(command.getRemark()));
        lead.setNormalizedName(normalizeName(lead.getName()));
        lead.setNormalizedPhone(normalizePhone(lead.getContactPhone()));
        lead.setNormalizedEmail(normalizeEmail(lead.getContactEmail()));
        if (command.getOwnerId() != null && actor.allDataScope()) {
            lead.setOwnerId(command.getOwnerId());
            lead.setDeptId(command.getDeptId());
        }
        assertNoDuplicate(lead, id, command.getConfirmedDuplicate());
        leadRepository.update(lead);
        return LeadView.from(lead, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadView advance(Long id, LeadStatusCommand command, CrmActor actor) {
        assertGate();
        Lead lead = requireLead(id, actor);
        LeadStatus target = LeadStatus.from(command.getStatus());
        if (lead.getStatus() == target) {
            return LeadView.from(lead, actor.canViewSensitive());
        }
        if (target == LeadStatus.CONTACTED) {
            lead.markContacted();
        } else if (target == LeadStatus.QUALIFIED) {
            lead.markQualified();
        } else {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        leadRepository.update(lead);
        return LeadView.from(lead, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadView assign(Long id, LeadAssignCommand command, CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            throw CrmErrorCode.exception(CrmErrorCode.DATA_SCOPE_DENIED);
        }
        Lead lead = requireLead(id, actor);
        lead.assign(command.getOwnerId(), command.getDeptId());
        leadRepository.updateOwner(id, command.getOwnerId(), command.getDeptId());
        lead.setOwnerId(command.getOwnerId());
        lead.setDeptId(command.getDeptId());
        return LeadView.from(lead, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadView invalidate(Long id, LeadInvalidCommand command, CrmActor actor) {
        assertGate();
        Lead lead = requireLead(id, actor);
        lead.invalidate(command.getReason());
        leadRepository.update(lead);
        return LeadView.from(lead, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadConvertResultView convert(Long id, LeadConvertCommand command, CrmActor actor) {
        assertGate();
        Lead lead = requireLead(id, actor);
        if (lead.converted()) {
            return existingResult(lead);
        }
        if (lead.invalid() || !lead.canConvert()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_STATE_CONFLICT);
        }
        Long customerId = command.getCustomerId() == null
                ? createCustomer(lead, actor) : command.getCustomerId();
        customerAppService.detail(customerId, actor);
        Long ownerId = lead.getOwnerId() == null ? actor.requireUserId() : lead.getOwnerId();
        Opportunity opportunity = new Opportunity();
        opportunity.setCustomerId(customerId);
        opportunity.setName(trimToNull(command.getOpportunityName()) == null
                ? lead.getName() + "-商机" : command.getOpportunityName().trim());
        opportunity.setAmount(command.getAmount());
        opportunity.setStage(OpportunityStage.CONTACT);
        opportunity.setProbability(OpportunityStage.CONTACT.defaultProbability());
        opportunity.setSource(lead.getSource());
        opportunity.setExpectedCloseDate(command.getExpectedCloseDate());
        opportunity.setOwnerId(ownerId);
        opportunity.setDeptId(lead.getDeptId());
        LocalDateTime now = LocalDateTime.now();
        opportunity.setStageEnteredAt(now);
        opportunityRepository.insert(opportunity);
        appendHistory(opportunity, null, OpportunityStage.CONTACT, actor.requireUserId(), now);
        boolean converted = leadRepository.markConverted(id, customerId, opportunity.getId(), now);
        if (!converted) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_ALREADY_CONVERTED);
        }
        LeadConvertResultView result = new LeadConvertResultView();
        result.setLeadId(id);
        result.setCustomerId(customerId);
        result.setOpportunityId(opportunity.getId());
        result.setConvertedAt(now);
        result.setAlreadyConverted(false);
        log.info("线索转化完成 leadId={} customerId={} opportunityId={} operator={}",
                id, customerId, opportunity.getId(), actor.getUserId());
        return result;
    }

    @Override
    public PageResult<LeadActivityView> activityPage(Long id, PageQuery pageQuery, CrmActor actor) {
        assertGate();
        requireLead(id, actor);
        List<LeadActivity> activities = activityRepository.page(id, pageQuery);
        long total = activityRepository.count(id);
        List<LeadActivityView> views = activities.stream().map(LeadActivityView::from).toList();
        return PageResult.of(views, total, pageQuery.getCurrent(), pageQuery.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadActivityView addActivity(Long id, LeadActivityCreateCommand command, CrmActor actor) {
        assertGate();
        Lead lead = requireLead(id, actor);
        String channel = command.getChannel().trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "活动渠道非法");
        }
        LeadActivity activity = new LeadActivity();
        activity.setLeadId(id);
        activity.setChannel(channel);
        activity.setContent(command.getContent().trim());
        activity.setResult(trimToNull(command.getResult()));
        activity.setNextActionAt(command.getNextActionAt());
        activity.setCreatedBy(actor.requireUserId());
        activityRepository.insert(activity);
        if (lead.getStatus() == LeadStatus.NEW) {
            lead.markContacted();
            leadRepository.update(lead);
        }
        return LeadActivityView.from(activity);
    }

    /**
     * 校验业务门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 应用数据范围。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     */
    private void applyDataScope(LeadQuery query, CrmActor actor) {
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
    }

    /**
     * 加载线索并校验数据范围。
     *
     * @param id 线索 ID
     * @param actor 当前操作人
     * @return 线索
     */
    private Lead requireLead(Long id, CrmActor actor) {
        Lead lead = leadRepository.findById(id);
        if (lead == null) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_NOT_FOUND);
        }
        if (!actor.allDataScope() && !actor.requireUserId().equals(lead.getOwnerId())) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_NOT_FOUND);
        }
        return lead;
    }

    /**
     * 解析负责人（非全量数据范围只能归属本人）。
     *
     * @param requested 请求负责人
     * @param actorId 当前操作人
     * @param actor 当前操作人
     * @return 负责人
     */
    private Long resolveOwner(Long requested, Long actorId, CrmActor actor) {
        return actor.allDataScope() && requested != null ? requested : actorId;
    }

    /**
     * 疑似重复校验。
     *
     * @param lead 线索
     * @param excludeId 排除的线索 ID
     * @param confirmed 是否已确认
     */
    private void assertNoDuplicate(Lead lead, Long excludeId, Boolean confirmed) {
        if (Boolean.TRUE.equals(confirmed)) {
            return;
        }
        List<Lead> duplicates = leadRepository.findDuplicates(lead.getNormalizedPhone(),
                lead.getNormalizedEmail(), excludeId);
        if (!duplicates.isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.LEAD_DUPLICATE);
        }
    }

    /**
     * 生成下一个线索编号。
     *
     * @return 线索编号
     */
    private String nextLeadNo() {
        String prefix = LEAD_PREFIX + "-" + LocalDate.now().format(YEAR_FORMAT) + "-";
        String maxNo = leadRepository.findMaxNo(prefix);
        int sequence = 1;
        if (maxNo != null && maxNo.length() > prefix.length()) {
            try {
                sequence = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                sequence = 1;
            }
        }
        return prefix + String.format("%0" + SEQUENCE_LENGTH + "d", sequence);
    }

    /**
     * 按线索信息创建客户。
     *
     * @param lead 线索
     * @param actor 当前操作人
     * @return 客户 ID
     */
    private Long createCustomer(Lead lead, CrmActor actor) {
        CustomerSaveCommand command = new CustomerSaveCommand();
        command.setName(lead.getName());
        command.setSource(lead.getSource());
        command.setIndustry(lead.getIndustry());
        command.setRegion(lead.getRegion());
        command.setOwnerId(lead.getOwnerId());
        command.setDeptId(lead.getDeptId());
        command.setLevel(CustomerLevel.C.name());
        command.setConfirmedDuplicate(false);
        CustomerView customer = customerAppService.create(command, actor);
        return customer.getId();
    }

    /**
     * 构造已有转化结果（幂等返回）。
     *
     * @param lead 已转化线索
     * @return 转化结果
     */
    private LeadConvertResultView existingResult(Lead lead) {
        LeadConvertResultView result = new LeadConvertResultView();
        result.setLeadId(lead.getId());
        result.setCustomerId(lead.getCustomerId());
        result.setOpportunityId(lead.getOpportunityId());
        result.setConvertedAt(lead.getConvertedAt());
        result.setAlreadyConverted(true);
        return result;
    }

    /**
     * 追加阶段历史。
     *
     * @param opportunity 商机
     * @param from 原阶段
     * @param to 目标阶段
     * @param operatorId 操作人
     * @param at 时间
     */
    private void appendHistory(Opportunity opportunity, OpportunityStage from, OpportunityStage to,
                               Long operatorId, LocalDateTime at) {
        OpportunityStageHistory history = new OpportunityStageHistory();
        history.setOpportunityId(opportunity.getId());
        history.setFromStage(from);
        history.setToStage(to);
        history.setAmount(opportunity.getAmount());
        history.setProbability(opportunity.getProbability());
        history.setOperatorId(operatorId);
        history.setChangedAt(at);
        historyRepository.insert(history);
    }

    /**
     * 归一化名称。
     *
     * @param name 名称
     * @return 归一化名称
     */
    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]", "");
    }

    /**
     * 归一化电话。
     *
     * @param phone 电话
     * @return 归一化电话
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0086")) {
            digits = digits.substring(4);
        } else if (digits.startsWith("86") && digits.length() > 10) {
            digits = digits.substring(2);
        }
        return digits.isEmpty() ? null : digits;
    }

    /**
     * 归一化邮箱。
     *
     * @param email 邮箱
     * @return 归一化邮箱
     */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 去除首尾空白，空白返回 {@code null}。
     *
     * @param text 文本
     * @return 结果
     */
    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
