package com.bluewhale.lms.cert.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.cert.application.command.CertificateIssueCommand;
import com.bluewhale.lms.cert.application.command.CertificateRenewCommand;
import com.bluewhale.lms.cert.application.command.CertificateRevokeCommand;
import com.bluewhale.lms.cert.application.support.CertificateIssueValidator;
import com.bluewhale.lms.cert.application.view.CertificateView;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertStatus;
import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.CertTemplateStatus;
import com.bluewhale.lms.cert.domain.model.Certificate;
import com.bluewhale.lms.cert.domain.model.CertificateQuery;
import com.bluewhale.lms.cert.domain.repository.CertTemplateRepository;
import com.bluewhale.lms.cert.domain.repository.CertificateRepository;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发证编排：依据以模板为准、同一来源幂等、取号不撞、换发留链。
 *
 * @author BlueWhale
 */
class CertificateAppServiceImplTest {

    /** 模板 ID */
    private static final long TEMPLATE_ID = 1L;

    /** 持证人用户 ID */
    private static final long HOLDER_ID = 100L;

    /** 写入后的证书 ID */
    private static final long SAVED_ID = 500L;

    /** 已签发证书 ID */
    private static final long EXISTING_ID = 300L;

    /** 操作人用户 ID */
    private static final long OPERATOR_ID = 9L;

    private CertificateRepository certificateRepository;

    private CertTemplateRepository certTemplateRepository;

    private CertificateIssueValidator issueValidator;

    private NotifyAppService notifyAppService;

    private CertificateAppServiceImpl service;

    private LmsActor admin;

    @BeforeEach
    void setUp() {
        certificateRepository = mock(CertificateRepository.class);
        certTemplateRepository = mock(CertTemplateRepository.class);
        issueValidator = mock(CertificateIssueValidator.class);
        notifyAppService = mock(NotifyAppService.class);
        service = new CertificateAppServiceImpl(certificateRepository, certTemplateRepository,
                issueValidator, notifyAppService, new LmsProperties());
        admin = new LmsActor(OPERATOR_ID, "admin", Set.of("lms_admin"), Set.of());
        when(certificateRepository.save(any())).thenAnswer(invocation -> {
            Certificate saved = invocation.getArgument(0);
            saved.setId(SAVED_ID);
            return SAVED_ID;
        });
        when(certificateRepository.findMaxSeqNo(anyInt())).thenReturn(5);
        when(certificateRepository.findFirstIssue(anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("手工发证按当年流水取号并写入证书")
    void shouldIssueManualCertificate() {
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.MANUAL)));
        Long id = service.issue(issueCommand(CertSource.MANUAL), admin);
        assertEquals(SAVED_ID, id);
        Certificate saved = captureSaved();
        int year = LocalDateTime.now().getYear();
        assertEquals("CERT-" + year + "-000006", saved.getCertNo());
        assertEquals(6, saved.getSeqNo());
        assertEquals(CertStatus.ACTIVE, saved.getStatus());
        assertEquals(Certificate.NO_RENEW_SOURCE, saved.getRenewFromId());
        assertEquals(0L, saved.getSourceId());
    }

    @Test
    @DisplayName("有期限模板按发证时间加月数")
    void shouldComputeValidTo() {
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.MANUAL)));
        service.issue(issueCommand(CertSource.MANUAL), admin);
        Certificate saved = captureSaved();
        assertEquals(saved.getIssuedAt().plusMonths(12), saved.getValidTo());
    }

    @Test
    @DisplayName("长期模板不写截止时间")
    void shouldLeaveValidToNullForLongTerm() {
        CertTemplate template = template(CertSource.MANUAL);
        template.setValidMonths(0);
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        service.issue(issueCommand(CertSource.MANUAL), admin);
        assertNull(captureSaved().getValidTo());
    }

    @Test
    @DisplayName("发证依据以模板为准，传入不一致的值直接拒绝")
    void shouldRejectMismatchedSource() {
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.EXAM)));
        CertificateIssueCommand command = issueCommand(CertSource.MANUAL);
        command.setSourceId(null);
        BizException exception = assertThrows(BizException.class, () -> service.issue(command, admin));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
        verify(certificateRepository, never()).save(any());
    }

    @Test
    @DisplayName("依据为考试时缺少来源 ID 直接拒绝")
    void shouldRejectMissingSourceId() {
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.EXAM)));
        CertificateIssueCommand command = issueCommand(CertSource.EXAM);
        command.setSourceId(null);
        BizException exception = assertThrows(BizException.class, () -> service.issue(command, admin));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("停用模板不可发证")
    void shouldRejectDisabledTemplate() {
        CertTemplate template = template(CertSource.MANUAL);
        template.setStatus(CertTemplateStatus.DISABLED);
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        BizException exception = assertThrows(BizException.class,
                () -> service.issue(issueCommand(CertSource.MANUAL), admin));
        assertEquals(LmsErrorCode.CERT_TEMPLATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("同一来源重复发证返回已有证书")
    void shouldReturnExistingCertificate() {
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.EXAM)));
        when(certificateRepository.findFirstIssue(TEMPLATE_ID, HOLDER_ID, CertSource.EXAM, 77L))
                .thenReturn(Optional.of(certificate(CertStatus.ACTIVE, null)));
        CertificateIssueCommand command = issueCommand(CertSource.EXAM);
        command.setSourceId(77L);
        assertEquals(EXISTING_ID, service.issue(command, admin));
        verify(certificateRepository, never()).save(any());
    }

    @Test
    @DisplayName("来源证书已过期时提示走换发")
    void shouldRejectExpiredExistingCertificate() {
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.EXAM)));
        when(certificateRepository.findFirstIssue(TEMPLATE_ID, HOLDER_ID, CertSource.EXAM, 77L))
                .thenReturn(Optional.of(certificate(CertStatus.ACTIVE, LocalDateTime.now().minusDays(1))));
        CertificateIssueCommand command = issueCommand(CertSource.EXAM);
        command.setSourceId(77L);
        BizException exception = assertThrows(BizException.class, () -> service.issue(command, admin));
        assertEquals(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, exception.getCode());
        assertEquals("该来源的证书已过期（CERT-2026-000001），请使用换发", exception.getMessage());
    }

    @Test
    @DisplayName("换发生成新证书并把原证书置为已换发")
    void shouldRenewCertificate() {
        Certificate old = certificate(CertStatus.ACTIVE, null);
        when(certificateRepository.findById(EXISTING_ID)).thenReturn(Optional.of(old));
        when(certTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(CertSource.MANUAL)));
        Long newId = service.renew(EXISTING_ID, new CertificateRenewCommand(), admin);
        assertEquals(SAVED_ID, newId);
        assertEquals(CertStatus.RENEWED, old.getStatus());
        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository, times(2)).save(captor.capture());
        Certificate renewed = captor.getAllValues().get(0);
        assertEquals(EXISTING_ID, renewed.getRenewFromId());
        assertEquals(HOLDER_ID, renewed.getUserId());
        assertEquals(old.getSourceType(), renewed.getSourceType());
    }

    @Test
    @DisplayName("已撤销的证书不可换发")
    void shouldRejectRenewRevoked() {
        when(certificateRepository.findById(EXISTING_ID))
                .thenReturn(Optional.of(certificate(CertStatus.REVOKED, null)));
        BizException exception = assertThrows(BizException.class,
                () -> service.renew(EXISTING_ID, new CertificateRenewCommand(), admin));
        assertEquals(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("撤销写入原因与操作人")
    void shouldRevokeWithReason() {
        Certificate certificate = certificate(CertStatus.ACTIVE, null);
        when(certificateRepository.findById(EXISTING_ID)).thenReturn(Optional.of(certificate));
        CertificateRevokeCommand command = new CertificateRevokeCommand();
        command.setReason(" 考试存在违纪行为 ");
        service.revoke(EXISTING_ID, command, admin);
        assertEquals(CertStatus.REVOKED, certificate.getStatus());
        assertEquals("考试存在违纪行为", certificate.getRevokeReason());
        assertEquals(OPERATOR_ID, certificate.getRevokedBy());
    }

    @Test
    @DisplayName("我的证书强制以当前登录人为持证人")
    void mineShouldForceCurrentUser() {
        CertificateQuery query = new CertificateQuery();
        query.setUserId(999L);
        when(certificateRepository.count(any())).thenReturn(0L);
        when(certificateRepository.page(any(), anyLong(), anyInt())).thenReturn(List.of());
        PageResult<CertificateView> result = service.mine(query, admin);
        assertEquals(0L, result.getTotal());
        assertEquals(OPERATOR_ID, query.getUserId());
    }

    /**
     * 捕获写入的证书。
     *
     * @return 证书
     */
    private Certificate captureSaved() {
        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).save(captor.capture());
        return captor.getValue();
    }

    /**
     * 构造成证书模板。
     *
     * @param basis 发证依据
     * @return 模板
     */
    private CertTemplate template(CertSource basis) {
        CertTemplate template = new CertTemplate();
        template.setId(TEMPLATE_ID);
        template.setTemplateCode("CERT-01");
        template.setName("安全生产培训证书");
        template.setCategory("SAFETY");
        template.setIssueBasis(basis);
        template.setValidMonths(12);
        template.setCertPrefix("CERT");
        template.setStatus(CertTemplateStatus.ENABLED);
        return template;
    }

    /**
     * 构造发证命令。
     *
     * @param source 来源类型
     * @return 命令
     */
    private CertificateIssueCommand issueCommand(CertSource source) {
        CertificateIssueCommand command = new CertificateIssueCommand();
        command.setTemplateId(TEMPLATE_ID);
        command.setUserId(HOLDER_ID);
        command.setUsername("zhangsan");
        command.setSourceType(source.name());
        command.setSourceId(source == CertSource.MANUAL ? null : 77L);
        return command;
    }

    /**
     * 构造成证书。
     *
     * @param status 状态
     * @param validTo 有效期止
     * @return 证书
     */
    private Certificate certificate(CertStatus status, LocalDateTime validTo) {
        Certificate certificate = new Certificate();
        certificate.setId(EXISTING_ID);
        certificate.setCertNo("CERT-2026-000001");
        certificate.setTemplateId(TEMPLATE_ID);
        certificate.setTemplateName("安全生产培训证书");
        certificate.setCategory("SAFETY");
        certificate.setUserId(HOLDER_ID);
        certificate.setUsername("zhangsan");
        certificate.setSourceType(CertSource.EXAM);
        certificate.setSourceId(77L);
        certificate.setIssueYear(2026);
        certificate.setSeqNo(1);
        certificate.setIssuedAt(LocalDateTime.now().minusMonths(6));
        certificate.setValidFrom(LocalDateTime.now().minusMonths(6));
        certificate.setValidTo(validTo);
        certificate.setStatus(status);
        certificate.setRenewFromId(Certificate.NO_RENEW_SOURCE);
        return certificate;
    }
}
