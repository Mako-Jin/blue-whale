package com.bluewhale.crm.marketing.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.marketing.application.command.PartnerCreateCommand;
import com.bluewhale.crm.marketing.application.command.PartnerReportCreateCommand;
import com.bluewhale.crm.marketing.application.command.PartnerReportResolveCommand;
import com.bluewhale.crm.marketing.application.command.PartnerUpdateCommand;
import com.bluewhale.crm.marketing.application.service.PartnerAppService;
import com.bluewhale.crm.marketing.application.view.PartnerReportView;
import com.bluewhale.crm.marketing.application.view.PartnerView;
import com.bluewhale.crm.marketing.domain.model.Partner;
import com.bluewhale.crm.marketing.domain.model.PartnerQuery;
import com.bluewhale.crm.marketing.domain.model.PartnerReport;
import com.bluewhale.crm.marketing.domain.model.PartnerReportQuery;
import com.bluewhale.crm.marketing.domain.model.PartnerReportStatus;
import com.bluewhale.crm.marketing.domain.model.PartnerStatus;
import com.bluewhale.crm.marketing.domain.model.PartnerType;
import com.bluewhale.crm.marketing.domain.repository.PartnerReportRepository;
import com.bluewhale.crm.marketing.domain.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 渠道伙伴与渠道报单应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PartnerAppServiceImpl implements PartnerAppService {

    /** 报单号前缀 */
    private static final String REPORT_PREFIX = "PR-";

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PartnerRepository partnerRepository;

    private final PartnerReportRepository partnerReportRepository;

    private final CustomerAppService customerAppService;

    private final CrmProperties crmProperties;

    private final CrmMarketingProperties marketingProperties;

    @Override
    public PageResult<PartnerView> pagePartners(PartnerQuery query, CrmActor actor) {
        ensureGate();
        if (!actor.allDataScope()) {
            query.setScopeUserId(actor.requireUserId());
        }
        List<PartnerView> records = partnerRepository.page(query).stream().map(PartnerView::from).toList();
        long total = partnerRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    public PartnerView partnerDetail(Long id, CrmActor actor) {
        ensureGate();
        return PartnerView.from(loadPartner(id, actor));
    }

    @Override
    @Transactional
    public PartnerView createPartner(PartnerCreateCommand command, CrmActor actor) {
        ensureGate();
        Long userId = actor.requireUserId();
        if (partnerRepository.existsByCode(command.getPartnerCode(), null)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_STATE_CONFLICT, "渠道伙伴编码已存在");
        }
        Partner partner = new Partner();
        partner.setPartnerCode(command.getPartnerCode());
        partner.setPartnerName(command.getPartnerName());
        partner.setPartnerType(PartnerType.from(command.getPartnerType()));
        partner.setLevel(command.getLevel());
        partner.setRegion(command.getRegion());
        partner.setContactName(command.getContactName());
        partner.setContactPhone(command.getContactPhone());
        partner.setStatus(PartnerStatus.ENABLED);
        partner.setOwnerId(command.getOwnerId() == null ? userId : command.getOwnerId());
        partner.setDeptId(command.getDeptId());
        partner.setLockVersion(0);
        partnerRepository.insert(partner);
        return PartnerView.from(partner);
    }

    @Override
    @Transactional
    public PartnerView updatePartner(Long id, PartnerUpdateCommand command, CrmActor actor) {
        ensureGate();
        Partner partner = loadPartner(id, actor);
        partner.setPartnerName(command.getPartnerName());
        partner.setPartnerType(PartnerType.from(command.getPartnerType()));
        partner.setLevel(command.getLevel());
        partner.setRegion(command.getRegion());
        partner.setContactName(command.getContactName());
        partner.setContactPhone(command.getContactPhone());
        if (command.getStatus() != null) {
            partner.setStatus(PartnerStatus.from(command.getStatus()));
        }
        if (command.getOwnerId() != null) {
            partner.setOwnerId(command.getOwnerId());
        }
        if (command.getDeptId() != null) {
            partner.setDeptId(command.getDeptId());
        }
        partnerRepository.update(partner);
        return PartnerView.from(partner);
    }

    @Override
    public PageResult<PartnerReportView> pageReports(PartnerReportQuery query, CrmActor actor) {
        ensureGate();
        if (!actor.allDataScope()) {
            query.setScopeUserId(actor.requireUserId());
        }
        List<PartnerReportView> records = partnerReportRepository.page(query).stream()
                .map(PartnerReportView::from).toList();
        long total = partnerReportRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    public PartnerReportView reportDetail(Long id, CrmActor actor) {
        ensureGate();
        return PartnerReportView.from(loadReport(id, actor));
    }

    @Override
    @Transactional
    public PartnerReportView createReport(PartnerReportCreateCommand command, CrmActor actor) {
        ensureGate();
        Long userId = actor.requireUserId();
        Partner partner = loadPartner(command.getPartnerId(), actor);
        partner.requireEnabled();
        CustomerDetailView detail = customerAppService.detail(command.getCustomerId(), actor);
        boolean conflict = partnerReportRepository.existsActiveReport(command.getCustomerId(),
                command.getProjectName(), null);
        PartnerReport report = new PartnerReport();
        report.setReportNo(nextReportNo());
        report.setPartnerId(partner.getId());
        report.setPartnerName(partner.getPartnerName());
        report.setCustomerId(command.getCustomerId());
        report.setCustomerName(detail.getCustomer() == null ? null : detail.getCustomer().getName());
        report.setProjectName(command.getProjectName());
        report.setEstimatedAmount(command.getEstimatedAmount() == null
                ? BigDecimal.ZERO : command.getEstimatedAmount());
        report.setStatus(PartnerReport.initialStatus(conflict, marketingProperties.isAutoApproveReport()));
        report.setOwnerId(command.getOwnerId() == null ? userId : command.getOwnerId());
        report.setDeptId(command.getDeptId());
        report.setLockVersion(0);
        if (conflict) {
            report.markConflict("同一客户同一项目存在待审核或已核准报单");
        } else if (report.getStatus() == PartnerReportStatus.APPROVED) {
            report.setApprovedAmount(report.getEstimatedAmount());
            report.setResolvedBy(userId);
            report.setResolvedAt(LocalDateTime.now());
        }
        partnerReportRepository.insert(report);
        return PartnerReportView.from(report);
    }

    @Override
    @Transactional
    public PartnerReportView resolveReport(Long id, PartnerReportResolveCommand command, CrmActor actor) {
        ensureGate();
        PartnerReport report = loadReport(id, actor);
        report.resolve(PartnerReportStatus.from(command.getStatus()), command.getApprovedAmount(),
                actor.requireUserId(), LocalDateTime.now());
        partnerReportRepository.update(report);
        return PartnerReportView.from(report);
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
     * 加载渠道伙伴并校验数据范围。
     *
     * @param id 伙伴 ID
     * @param actor 当前操作人
     * @return 伙伴
     */
    private Partner loadPartner(Long id, CrmActor actor) {
        Partner partner = id == null ? null : partnerRepository.findById(id);
        if (partner == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(partner.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_NOT_FOUND);
        }
        return partner;
    }

    /**
     * 加载渠道报单并校验数据范围。
     *
     * @param id 报单 ID
     * @param actor 当前操作人
     * @return 报单
     */
    private PartnerReport loadReport(Long id, CrmActor actor) {
        PartnerReport report = id == null ? null : partnerReportRepository.findById(id);
        if (report == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_REPORT_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(report.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_REPORT_NOT_FOUND);
        }
        return report;
    }

    /**
     * 生成报单号。
     *
     * @return 报单号
     */
    private String nextReportNo() {
        String prefix = REPORT_PREFIX + LocalDate.now().format(DATE_FORMAT) + "-";
        return prefix + String.format("%04d", partnerReportRepository.countByReportNoPrefix(prefix) + 1);
    }
}
