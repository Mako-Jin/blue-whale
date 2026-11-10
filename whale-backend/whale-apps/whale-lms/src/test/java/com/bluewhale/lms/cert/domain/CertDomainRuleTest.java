package com.bluewhale.lms.cert.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertStatus;
import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.CertTemplateStatus;
import com.bluewhale.lms.cert.domain.model.Certificate;
import com.bluewhale.lms.common.LmsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证书领域规则：编号构成、过期派生、撤销与换发的终态约束。
 *
 * <p>这些规则全部落在领域对象上而不是服务里，原因是它们与「谁在调用」无关——
 * 无论从发证、换发还是后台批处理进入，撤销过的证书都不该能换发。</p>
 *
 * @author BlueWhale
 */
class CertDomainRuleTest {

    /** 基准时间 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);

    @Test
    @DisplayName("编号由前缀-年份-6 位流水构成")
    void shouldComposeCertNo() {
        assertEquals("CERT-2026-000001", Certificate.composeNo("CERT", 2026, 1));
        assertEquals("SAFE-2026-000123", Certificate.composeNo("safe", 2026, 123));
    }

    @Test
    @DisplayName("前缀为空时回落为默认前缀")
    void shouldFallbackPrefix() {
        assertEquals("CERT-2026-000001", Certificate.composeNo("  ", 2026, 1));
        assertEquals(CertTemplate.DEFAULT_PREFIX, CertTemplate.normalizePrefix(null));
    }

    @Test
    @DisplayName("长期有效模板的截止时间为空")
    void longTermTemplateShouldHaveNoDeadline() {
        CertTemplate template = template(0);
        assertTrue(template.isLongTerm());
        assertNull(template.extendValidTo(NOW));
    }

    @Test
    @DisplayName("有限期模板按起算时间加月数")
    void finiteTemplateShouldExtendByMonths() {
        CertTemplate template = template(12);
        assertEquals(NOW.plusMonths(12), template.extendValidTo(NOW));
    }

    @Test
    @DisplayName("落库有效但已过有效期时展示为过期")
    void shouldDeriveExpiredStatus() {
        Certificate certificate = certificate(NOW.minusMonths(1));
        assertEquals(CertStatus.EXPIRED, certificate.effectiveStatus(NOW));
        assertTrue(certificate.isExpired(NOW));
        assertFalse(certificate.isExpired(NOW.minusMonths(2)));
    }

    @Test
    @DisplayName("长期有效证书不会过期")
    void longTermCertificateShouldNeverExpire() {
        Certificate certificate = certificate(null);
        assertEquals(CertStatus.ACTIVE, certificate.effectiveStatus(NOW));
        assertFalse(certificate.isExpired(NOW));
    }

    @Test
    @DisplayName("撤销后不可重复撤销")
    void shouldRejectDoubleRevoke() {
        Certificate certificate = certificate(null);
        certificate.revoke("发现证书信息有误", 9L, NOW);
        assertEquals(CertStatus.REVOKED, certificate.getStatus());
        BizException exception = assertThrows(BizException.class,
                () -> certificate.revoke("再次撤销", 9L, NOW));
        assertEquals(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("撤销必须给出原因")
    void revokeShouldRequireReason() {
        Certificate certificate = certificate(null);
        BizException exception = assertThrows(BizException.class,
                () -> certificate.revoke("   ", 9L, NOW));
        assertEquals(LmsErrorCode.CERTIFICATE_INVALID, exception.getCode());
        assertEquals(CertStatus.ACTIVE, certificate.getStatus());
    }

    @Test
    @DisplayName("已撤销的证书不可换发")
    void revokedCertificateShouldNotRenew() {
        Certificate certificate = certificate(null);
        certificate.revoke("考试作弊", 9L, NOW);
        BizException exception = assertThrows(BizException.class, certificate::assertRenewable);
        assertEquals(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已换发的证书不可再撤销（应撤销新证书）")
    void renewedCertificateShouldNotRevoke() {
        Certificate certificate = certificate(null);
        certificate.markRenewed();
        BizException exception = assertThrows(BizException.class, certificate::assertRevocable);
        assertEquals(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("换发只发生一次")
    void renewShouldHappenOnce() {
        Certificate certificate = certificate(null);
        certificate.markRenewed();
        BizException exception = assertThrows(BizException.class, certificate::assertRenewable);
        assertEquals(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("过期证书仍可撤销与换发")
    void expiredCertificateShouldStillBeActionable() {
        Certificate certificate = certificate(NOW.minusDays(1));
        certificate.assertRevocable();
        certificate.assertRenewable();
    }

    @Test
    @DisplayName("模板编码与类别归一为大写")
    void shouldNormalizeTemplateFields() {
        assertEquals("SAFE-01", CertTemplate.normalizeCode(" safe-01 "));
        assertEquals("CERT", CertTemplate.normalizePrefix(""));
    }

    @Test
    @DisplayName("模板编码为空时报参数错误")
    void shouldRejectBlankTemplateCode() {
        CertTemplate template = template(12);
        template.setTemplateCode("");
        BizException exception = assertThrows(BizException.class, template::validateBasics);
        assertEquals(LmsErrorCode.CERT_TEMPLATE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("编号前缀非法时报参数错误")
    void shouldRejectIllegalPrefix() {
        CertTemplate template = template(12);
        template.setCertPrefix("CERT-1");
        BizException exception = assertThrows(BizException.class, template::validateBasics);
        assertEquals(LmsErrorCode.CERT_TEMPLATE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("有效期月数越界时报参数错误")
    void shouldRejectIllegalValidMonths() {
        CertTemplate template = template(-1);
        BizException exception = assertThrows(BizException.class, template::validateBasics);
        assertEquals(LmsErrorCode.CERT_TEMPLATE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("停用的模板不可用于发证")
    void disabledTemplateShouldNotIssue() {
        CertTemplate template = template(12);
        template.setStatus(CertTemplateStatus.DISABLED);
        BizException exception = assertThrows(BizException.class, template::requireIssuable);
        assertEquals(LmsErrorCode.CERT_TEMPLATE_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("来源类型未知时按手工发证处理")
    void shouldParseUnknownSourceAsManual() {
        assertEquals(CertSource.MANUAL, CertSource.parse(null));
        assertEquals(CertSource.MANUAL, CertSource.parse("UNKNOWN"));
        assertEquals(CertSource.EXAM, CertSource.parse(" exam "));
        assertFalse(CertSource.MANUAL.requiresVerification());
        assertTrue(CertSource.EXAM.requiresVerification());
    }

    /**
     * 构造模板。
     *
     * @param validMonths 有效期月数
     * @return 模板
     */
    private CertTemplate template(int validMonths) {
        CertTemplate template = new CertTemplate();
        template.setTemplateCode("CERT-01");
        template.setName("安全生产培训证书");
        template.setCategory("SAFETY");
        template.setIssueBasis(CertSource.EXAM);
        template.setValidMonths(validMonths);
        template.setCertPrefix("CERT");
        template.setStatus(CertTemplateStatus.ENABLED);
        return template;
    }

    /**
     * 构造证书。
     *
     * @param validTo 有效期止（null 为长期）
     * @return 证书
     */
    private Certificate certificate(LocalDateTime validTo) {
        Certificate certificate = new Certificate();
        certificate.setCertNo("CERT-2026-000001");
        certificate.setTemplateId(1L);
        certificate.setTemplateName("安全生产培训证书");
        certificate.setUserId(100L);
        certificate.setSourceType(CertSource.MANUAL);
        certificate.setSourceId(0L);
        certificate.setIssueYear(2026);
        certificate.setSeqNo(1);
        certificate.setIssuedAt(NOW.minusMonths(6));
        certificate.setValidFrom(NOW.minusMonths(6));
        certificate.setValidTo(validTo);
        certificate.setStatus(CertStatus.ACTIVE);
        certificate.setRenewFromId(Certificate.NO_RENEW_SOURCE);
        return certificate;
    }
}
