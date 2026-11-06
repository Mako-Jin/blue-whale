package com.bluewhale.lms.cert.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.cert.application.command.CertificateIssueCommand;
import com.bluewhale.lms.cert.application.command.CertificateRenewCommand;
import com.bluewhale.lms.cert.application.command.CertificateRevokeCommand;
import com.bluewhale.lms.cert.application.service.CertificateAppService;
import com.bluewhale.lms.cert.application.support.CertificateIssueValidator;
import com.bluewhale.lms.cert.application.view.CertificateView;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertStatus;
import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.Certificate;
import com.bluewhale.lms.cert.domain.model.CertificateQuery;
import com.bluewhale.lms.cert.domain.repository.CertTemplateRepository;
import com.bluewhale.lms.cert.domain.repository.CertificateRepository;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 证书应用服务实现。
 *
 * <p>发证这一段是整个上下文最容易出错的地方，四条规则在这里落地：</p>
 * <ol>
 *   <li><b>依据以模板为准</b>：调用方传的 {@code sourceType} 只用于提示，实际一律取模板声明的
 *       {@code issueBasis}。若允许调用方指定，只要把 {@code EXAM} 传成 {@code MANUAL} 就能跳过
 *       「考试必须通过」的校验，模板也就白配了。传了不一致的值直接拒绝，避免调用方误以为生效；</li>
 *   <li><b>同一来源只发一次</b>：重复发证返回已有证书（幂等），但已过期要提示走换发、已撤销要
 *       提示人工确认——幂等只对「同一件正确的事重复做」成立，对失效的证书返回成功会误导调用方；</li>
 *   <li><b>取号不用 COUNT</b>：流水号取当年 {@code MAX(seq_no) + 1}。用条数取号时，一旦有证书被
 *       逻辑删除，条数回退就会把已用过的号再发一次，直接撞唯一键；</li>
 *   <li><b>撞号重试</b>：并发下两个请求可能算出同一个号，靠唯一键兜底、重新取号再试，
 *       重试用尽才报错。MyBatis 的唯一键冲突不会污染事务，重试是安全的。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CertificateAppServiceImpl implements CertificateAppService {

    /** 手工发证的来源业务 ID */
    private static final long MANUAL_SOURCE_ID = 0L;

    private final CertificateRepository certificateRepository;

    private final CertTemplateRepository certTemplateRepository;

    private final CertificateIssueValidator issueValidator;

    private final NotifyAppService notifyAppService;

    private final LmsProperties properties;

    @Override
    public PageResult<CertificateView> page(CertificateQuery query, LmsActor actor) {
        assertGate();
        LocalDateTime now = LocalDateTime.now();
        long current = Math.max(query.getCurrent() == null ? 1L : query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize() == null ? 20L : query.getSize(), 1L),
                properties.getCertPageLimit());
        long total = certificateRepository.count(query);
        List<CertificateView> views = certificateRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(certificate -> CertificateView.from(certificate, now)).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public CertificateView detail(Long id, LmsActor actor) {
        assertGate();
        return CertificateView.from(requireCertificate(id), LocalDateTime.now());
    }

    @Override
    @Transactional
    public Long issue(CertificateIssueCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Long userId = requireHolder(command.getUserId());
        CertTemplate template = requireTemplate(command.getTemplateId());
        template.requireIssuable();
        CertSource source = resolveSource(template, command.getSourceType());
        Long sourceId = resolveSourceId(command.getSourceId(), source);
        issueValidator.verify(source, userId, sourceId);
        LocalDateTime now = LocalDateTime.now();
        Optional<Long> reused = reuseExisting(template.getId(), userId, source, sourceId, now);
        if (reused.isPresent()) {
            return reused.get();
        }
        Certificate certificate = buildCertificate(template, userId, command.getUsername(), source,
                sourceId, command.getSourceTitle(), Certificate.NO_RENEW_SOURCE, command.getRemark(), now);
        Long issuedId = persistWithSeq(certificate, template);
        notifyAppService.notifyCertIssue(issuedId, certificate.getCertNo(), template.getName(), userId);
        return issuedId;
    }

    @Override
    @Transactional
    public void revoke(Long id, CertificateRevokeCommand command, LmsActor actor) {
        assertGate();
        Long operator = actor.requireUserId();
        Certificate certificate = requireCertificate(id);
        certificate.revoke(command.getReason(), operator, LocalDateTime.now());
        certificateRepository.save(certificate);
    }

    @Override
    @Transactional
    public Long renew(Long id, CertificateRenewCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Certificate old = requireCertificate(id);
        old.assertRenewable();
        CertTemplate template = requireTemplate(old.getTemplateId());
        template.requireIssuable();
        LocalDateTime now = LocalDateTime.now();
        Certificate renewed = buildCertificate(template, old.getUserId(), old.getUsername(), old.getSourceType(),
                old.getSourceId(), old.getSourceTitle(), old.getId(), command.getRemark(), now);
        Long newId = persistWithSeq(renewed, template);
        old.markRenewed();
        certificateRepository.save(old);
        notifyAppService.notifyCertIssue(newId, renewed.getCertNo(), template.getName(), old.getUserId());
        return newId;
    }

    @Override
    public PageResult<CertificateView> mine(CertificateQuery query, LmsActor actor) {
        assertGate();
        query.setUserId(actor.requireUserId());
        return page(query, actor);
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 校验持证人用户 ID。
     *
     * @param userId 持证人用户 ID
     * @return 校验后的用户 ID
     */
    private Long requireHolder(Long userId) {
        if (userId == null || userId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_INVALID, "持证人不能为空");
        }
        return userId;
    }

    /**
     * 加载模板，不存在按 404 处理。
     *
     * @param id 模板 ID
     * @return 模板
     */
    private CertTemplate requireTemplate(Long id) {
        if (id == null) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_NOT_FOUND);
        }
        return certTemplateRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_NOT_FOUND));
    }

    /**
     * 加载证书，不存在按 404 处理。
     *
     * @param id 证书 ID
     * @return 证书
     */
    private Certificate requireCertificate(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_NOT_FOUND));
    }

    /**
     * 解析本次发证的来源类型：以模板声明的依据为准。
     *
     * @param template 模板
     * @param requested 调用方声明的来源类型
     * @return 实际来源类型
     */
    private CertSource resolveSource(CertTemplate template, String requested) {
        CertSource declared = template.getIssueBasis() == null ? CertSource.MANUAL : template.getIssueBasis();
        if (!StringUtils.hasText(requested)) {
            return declared;
        }
        CertSource provided = CertSource.parse(requested);
        if (provided != declared) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_SOURCE_INVALID,
                    "该模板的发证依据为 " + declared.name() + "，不接受以 " + provided.name() + " 发证");
        }
        return declared;
    }

    /**
     * 解析来源业务 ID。
     *
     * @param sourceId 命令中的来源 ID
     * @param source 来源类型
     * @return 归一后的来源 ID
     */
    private Long resolveSourceId(Long sourceId, CertSource source) {
        if (source == CertSource.MANUAL) {
            return MANUAL_SOURCE_ID;
        }
        if (sourceId == null || sourceId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_SOURCE_INVALID,
                    "发证依据为 " + source.name() + "，必须提供来源业务 ID");
        }
        return sourceId;
    }

    /**
     * 判断同一来源是否已发过证。
     *
     * @param templateId 模板 ID
     * @param userId 持证人用户 ID
     * @param source 来源类型
     * @param sourceId 来源业务 ID
     * @param now 当前时间
     * @return 已存在的有效证书 ID（可幂等返回）
     */
    private Optional<Long> reuseExisting(Long templateId, Long userId, CertSource source, Long sourceId,
                                         LocalDateTime now) {
        Optional<Certificate> existing = certificateRepository.findFirstIssue(templateId, userId, source, sourceId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Certificate certificate = existing.get();
        CertStatus status = certificate.effectiveStatus(now);
        if (status == CertStatus.ACTIVE) {
            return Optional.of(certificate.getId());
        }
        if (status == CertStatus.EXPIRED) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT,
                    "该来源的证书已过期（" + certificate.getCertNo() + "），请使用换发");
        }
        if (status == CertStatus.REVOKED) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT,
                    "该来源的证书已被撤销（" + certificate.getCertNo() + "），需人工确认后重新发证");
        }
        throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT,
                "该来源的证书已换发（" + certificate.getCertNo() + "），请以新证书为准");
    }

    /**
     * 组装新证书。
     *
     * @param template 模板
     * @param userId 持证人用户 ID
     * @param username 持证人用户名
     * @param source 来源类型
     * @param sourceId 来源业务 ID
     * @param sourceTitle 来源标题快照
     * @param renewFromId 换发来源证书 ID
     * @param remark 备注
     * @param now 发证时间
     * @return 证书（未取号）
     */
    private Certificate buildCertificate(CertTemplate template, Long userId, String username, CertSource source,
                                         Long sourceId, String sourceTitle, Long renewFromId, String remark,
                                         LocalDateTime now) {
        Certificate certificate = new Certificate();
        certificate.setTemplateId(template.getId());
        certificate.setTemplateName(template.getName());
        certificate.setCategory(template.getCategory());
        certificate.setUserId(userId);
        certificate.setUsername(username);
        certificate.setSourceType(source);
        certificate.setSourceId(sourceId);
        certificate.setSourceTitle(sourceTitle);
        certificate.setIssueYear(now.getYear());
        certificate.setIssuedAt(now);
        certificate.setValidFrom(now);
        certificate.setValidTo(template.extendValidTo(now));
        certificate.setStatus(CertStatus.ACTIVE);
        certificate.setRenewFromId(renewFromId == null ? Certificate.NO_RENEW_SOURCE : renewFromId);
        certificate.setRemark(remark);
        return certificate;
    }

    /**
     * 取号并落库，撞号时重新取号重试。
     *
     * <p>重试前必须清掉 {@code id}：MyBatis-Plus 的 insert 会先把雪花 ID 写入实体再执行 SQL，
     * 唯一键冲突时 ID 已经落到对象上了。若不清空，下一轮 {@code save} 会因为「ID 非空」
     * 走成 update，更新不到任何行却返回一个不存在的 ID。</p>
     *
     * @param certificate 证书
     * @param template 模板（提供编号前缀）
     * @return 证书 ID
     */
    private Long persistWithSeq(Certificate certificate, CertTemplate template) {
        int maxRetry = Math.max(properties.getCertIssueRetry(), 1);
        for (int attempt = 0; attempt < maxRetry; attempt++) {
            int seqNo = certificateRepository.findMaxSeqNo(certificate.getIssueYear()) + 1;
            certificate.setSeqNo(seqNo);
            certificate.setCertNo(Certificate.composeNo(template.getCertPrefix(), certificate.getIssueYear(), seqNo));
            try {
                return certificateRepository.save(certificate);
            } catch (DuplicateKeyException ex) {
                // 并发取号撞车：清空 ID 后重新取号再试，重试用尽才对外报冲突
                certificate.setId(null);
            }
        }
        throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_NO_CONFLICT);
    }
}
