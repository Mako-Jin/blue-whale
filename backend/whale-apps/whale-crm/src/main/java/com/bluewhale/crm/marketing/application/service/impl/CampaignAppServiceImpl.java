package com.bluewhale.crm.marketing.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.marketing.application.command.AudienceLockCommand;
import com.bluewhale.crm.marketing.application.command.AudiencePreviewCommand;
import com.bluewhale.crm.marketing.application.command.CampaignCreateCommand;
import com.bluewhale.crm.marketing.application.command.CampaignRoiCommand;
import com.bluewhale.crm.marketing.application.command.CampaignStatusCommand;
import com.bluewhale.crm.marketing.application.command.CampaignUpdateCommand;
import com.bluewhale.crm.marketing.application.service.CampaignAppService;
import com.bluewhale.crm.marketing.application.view.AudiencePreviewView;
import com.bluewhale.crm.marketing.application.view.CampaignAudienceView;
import com.bluewhale.crm.marketing.application.view.CampaignView;
import com.bluewhale.crm.marketing.domain.model.AudienceCriteria;
import com.bluewhale.crm.marketing.domain.model.AudiencePreview;
import com.bluewhale.crm.marketing.domain.model.Campaign;
import com.bluewhale.crm.marketing.domain.model.CampaignAudience;
import com.bluewhale.crm.marketing.domain.model.CampaignMember;
import com.bluewhale.crm.marketing.domain.model.CampaignQuery;
import com.bluewhale.crm.marketing.domain.model.CampaignStatus;
import com.bluewhale.crm.marketing.domain.model.CampaignType;
import com.bluewhale.crm.marketing.domain.repository.CampaignAudienceRepository;
import com.bluewhale.crm.marketing.domain.repository.CampaignMemberRepository;
import com.bluewhale.crm.marketing.domain.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 营销活动应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CampaignAppServiceImpl implements CampaignAppService {

    /** 活动编码前缀 */
    private static final String CODE_PREFIX = "CAMP-";

    /** 人群快照编号前缀 */
    private static final String SNAPSHOT_PREFIX = "AUD-";

    /** 人群预览样例上限 */
    private static final int PREVIEW_SAMPLE_SIZE = 20;

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CampaignRepository campaignRepository;

    private final CampaignAudienceRepository campaignAudienceRepository;

    private final CampaignMemberRepository campaignMemberRepository;

    private final CustomerAppService customerAppService;

    private final CrmProperties crmProperties;

    private final CrmMarketingProperties marketingProperties;

    @Override
    public PageResult<CampaignView> page(CampaignQuery query, CrmActor actor) {
        ensureGate();
        if (!actor.allDataScope()) {
            query.setScopeUserId(actor.requireUserId());
        }
        List<CampaignView> records = campaignRepository.page(query).stream().map(CampaignView::from).toList();
        long total = campaignRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    public CampaignView detail(Long id, CrmActor actor) {
        ensureGate();
        return CampaignView.from(loadCampaign(id, actor));
    }

    @Override
    @Transactional
    public CampaignView create(CampaignCreateCommand command, CrmActor actor) {
        ensureGate();
        Long userId = actor.requireUserId();
        Campaign.validateBudget(command.getBudget());
        Campaign.validatePeriod(command.getPlannedStart(), command.getPlannedEnd());
        Campaign campaign = new Campaign();
        campaign.setCampaignCode(nextCode());
        campaign.setCampaignName(command.getCampaignName());
        campaign.setCampaignType(CampaignType.from(command.getCampaignType()));
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setChannel(command.getChannel());
        campaign.setBudget(command.getBudget() == null ? BigDecimal.ZERO : command.getBudget());
        campaign.setActualCost(BigDecimal.ZERO);
        campaign.setPlannedStart(command.getPlannedStart());
        campaign.setPlannedEnd(command.getPlannedEnd());
        campaign.setAudienceCount(0);
        campaign.setAttributedLeadCount(0);
        campaign.setAttributedWonAmount(BigDecimal.ZERO);
        campaign.setRoi(BigDecimal.ZERO.setScale(4));
        campaign.setOwnerId(command.getOwnerId() == null ? userId : command.getOwnerId());
        campaign.setDeptId(command.getDeptId());
        campaign.setLockVersion(0);
        campaignRepository.insert(campaign);
        return CampaignView.from(campaign);
    }

    @Override
    @Transactional
    public CampaignView update(Long id, CampaignUpdateCommand command, CrmActor actor) {
        ensureGate();
        Campaign campaign = loadCampaign(id, actor);
        Campaign.validatePeriod(command.getPlannedStart(), command.getPlannedEnd());
        BigDecimal budget = command.getBudget() == null ? campaign.getBudget() : command.getBudget();
        if (!Objects.equals(budget, campaign.getBudget())) {
            campaign.changeBudget(budget);
        }
        campaign.setCampaignName(command.getCampaignName());
        campaign.setCampaignType(CampaignType.from(command.getCampaignType()));
        campaign.setChannel(command.getChannel());
        campaign.setPlannedStart(command.getPlannedStart());
        campaign.setPlannedEnd(command.getPlannedEnd());
        if (command.getOwnerId() != null) {
            campaign.setOwnerId(command.getOwnerId());
        }
        if (command.getDeptId() != null) {
            campaign.setDeptId(command.getDeptId());
        }
        campaignRepository.update(campaign);
        return CampaignView.from(campaign);
    }

    @Override
    @Transactional
    public CampaignView changeStatus(Long id, CampaignStatusCommand command, CrmActor actor) {
        ensureGate();
        Campaign campaign = loadCampaign(id, actor);
        CampaignStatus target = CampaignStatus.from(command.getStatus());
        switch (target) {
            case RUNNING -> campaign.start();
            case ENDED -> campaign.end();
            case CANCELLED -> campaign.cancel();
            default -> throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_STATE_CONFLICT,
                    "目标状态只能为 RUNNING / ENDED / CANCELLED");
        }
        campaignRepository.update(campaign);
        return CampaignView.from(campaign);
    }

    @Override
    @Transactional
    public CampaignView updateRoi(Long id, CampaignRoiCommand command, CrmActor actor) {
        ensureGate();
        Campaign campaign = loadCampaign(id, actor);
        campaign.applyRoi(command.getAttributedLeadCount(), command.getAttributedWonAmount(),
                command.getActualCost());
        campaignRepository.update(campaign);
        return CampaignView.from(campaign);
    }

    @Override
    public AudiencePreviewView previewAudience(Long id, AudiencePreviewCommand command, CrmActor actor) {
        ensureGate();
        loadCampaign(id, actor);
        AudienceCriteria criteria = command == null || command.getCriteria() == null
                ? new AudienceCriteria() : command.getCriteria();
        AudiencePreview preview = preview(criteria, actor);
        return AudiencePreviewView.from(preview, criteria);
    }

    @Override
    @Transactional
    public CampaignAudienceView lockAudience(Long id, AudienceLockCommand command, CrmActor actor) {
        ensureGate();
        Campaign campaign = loadCampaign(id, actor);
        if (campaign.getAudienceId() != null) {
            CampaignAudience existing = campaignAudienceRepository.findById(campaign.getAudienceId());
            if (existing != null && Boolean.TRUE.equals(existing.getLocked())) {
                throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "活动人群已锁定，不可重复锁定");
            }
        }
        AudienceCriteria criteria = command == null || command.getCriteria() == null
                ? new AudienceCriteria() : command.getCriteria();
        AudiencePreview preview = preview(criteria, actor);
        List<CampaignMember> members = collectMembers(criteria, actor, preview.getCount());
        CampaignAudience audience = new CampaignAudience();
        audience.setCampaignId(campaign.getId());
        audience.setSnapshotNo(SNAPSHOT_PREFIX + campaign.getId() + "-" + System.currentTimeMillis());
        audience.setCriteriaJson(criteria.toJson());
        audience.setMemberCount(members.size());
        audience.setLocked(Boolean.FALSE);
        campaignAudienceRepository.insert(audience);
        for (CampaignMember member : members) {
            member.setAudienceId(audience.getId());
        }
        campaignMemberRepository.insertBatch(members);
        audience.lock(LocalDateTime.now());
        campaignAudienceRepository.update(audience);
        campaign.applyAudience(audience.getId(), members.size());
        campaignRepository.update(campaign);
        return CampaignAudienceView.from(audience);
    }

    /**
     * 校验需求门禁与营销开关。
     */
    private void ensureGate() {
        if (!crmProperties.isDemandGatePassed() || !marketingProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载活动并校验数据范围。
     *
     * @param id 活动 ID
     * @param actor 当前操作人
     * @return 活动
     */
    private Campaign loadCampaign(Long id, CrmActor actor) {
        Campaign campaign = id == null ? null : campaignRepository.findById(id);
        if (campaign == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(campaign.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_NOT_FOUND);
        }
        return campaign;
    }

    /**
     * 生成活动编码。
     *
     * @return 活动编码
     */
    private String nextCode() {
        String prefix = CODE_PREFIX + LocalDate.now().format(DATE_FORMAT) + "-";
        return prefix + String.format("%04d", campaignRepository.countByCodePrefix(prefix) + 1);
    }

    /**
     * 按条件预览人群。
     *
     * @param criteria 人群条件
     * @param actor 当前操作人
     * @return 预览结果
     */
    private AudiencePreview preview(AudienceCriteria criteria, CrmActor actor) {
        CustomerQuery query = toCustomerQuery(criteria);
        query.setCurrent(1);
        query.setSize(PREVIEW_SAMPLE_SIZE);
        PageResult<CustomerView> page = customerAppService.page(query, actor);
        AudiencePreview preview = new AudiencePreview();
        preview.setCount(page.getTotal());
        preview.setSamples(page.getRecords().stream()
                .map(customer -> new AudiencePreview.Sample(customer.getId(), customer.getName(),
                        customer.getLevel(), customer.getIndustry(), customer.getSource()))
                .toList());
        return preview;
    }

    /**
     * 按条件批量拉取人群成员（仅通过客户应用服务读取）。
     *
     * @param criteria 人群条件
     * @param actor 当前操作人
     * @param count 预计命中数量
     * @return 成员列表
     */
    private List<CampaignMember> collectMembers(AudienceCriteria criteria, CrmActor actor, Long count) {
        List<CampaignMember> members = new ArrayList<>();
        long expected = count == null ? 0 : count;
        if (expected <= 0) {
            return members;
        }
        int batchSize = Math.max(1, marketingProperties.getSendBatchSize());
        long current = 1;
        while (members.size() < expected) {
            CustomerQuery query = toCustomerQuery(criteria);
            query.setCurrent(current);
            query.setSize(batchSize);
            PageResult<CustomerView> page = customerAppService.page(query, actor);
            if (page.getRecords().isEmpty()) {
                break;
            }
            for (CustomerView customer : page.getRecords()) {
                members.add(buildMember(customer, actor));
            }
            if (page.getRecords().size() < batchSize) {
                break;
            }
            current++;
        }
        return members;
    }

    /**
     * 构建人群成员快照。
     *
     * @param customer 客户视图
     * @param actor 当前操作人
     * @return 成员
     */
    private CampaignMember buildMember(CustomerView customer, CrmActor actor) {
        CampaignMember member = new CampaignMember();
        member.setCustomerId(customer.getId());
        member.setExcluded(Boolean.FALSE);
        List<ContactView> contacts = customerAppService.listContacts(customer.getId(), actor);
        ContactView contact = contacts == null ? null : contacts.stream()
                .filter(item -> Boolean.TRUE.equals(item.getPrimary()))
                .findFirst().orElse(contacts.isEmpty() ? null : contacts.get(0));
        if (contact == null) {
            member.setExcluded(Boolean.TRUE);
            member.setExcludeReason("无联系方式");
            return member;
        }
        member.setContactId(contact.getId());
        member.setContactName(contact.getName());
        member.setEmailHash(sha256(contact.getEmail()));
        member.setPhoneHash(sha256(contact.getPhone()));
        return member;
    }

    /**
     * 人群条件转客户查询条件。
     *
     * @param criteria 人群条件
     * @return 客户查询条件
     */
    private CustomerQuery toCustomerQuery(AudienceCriteria criteria) {
        CustomerQuery query = new CustomerQuery();
        if (criteria != null) {
            query.setIndustry(first(criteria.getIndustries()));
            query.setLevel(first(criteria.getLevels()));
            query.setSource(first(criteria.getSources()));
            query.setOwnerId(criteria.getOwnerId());
        }
        return query;
    }

    /**
     * 取列表首项。
     *
     * @param values 列表
     * @return 首项；空返回 null
     */
    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param value 原文
     * @return 摘要；空返回 null
     */
    private String sha256(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "SHA-256 算法不可用");
        }
    }
}
