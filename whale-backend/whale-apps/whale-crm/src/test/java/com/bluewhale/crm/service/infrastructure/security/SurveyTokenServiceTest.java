package com.bluewhale.crm.service.infrastructure.security;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmSurveyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SurveyTokenService} 单元测试。
 *
 * @author BlueWhale
 */
class SurveyTokenServiceTest {

    private SurveyTokenService tokenService;

    @BeforeEach
    void setUp() {
        CrmSurveyProperties properties = new CrmSurveyProperties();
        properties.setSecret("test-secret");
        tokenService = new SurveyTokenService(properties);
    }

    @Test
    @DisplayName("令牌：以问卷编号为前缀，哈希可复算且能通过校验")
    void shouldGenerateAndMatchToken() {
        String surveyNo = "CSAT-20261107-0001";

        String token = tokenService.generate(surveyNo);
        String hash = tokenService.hash(surveyNo, token);

        assertTrue(token.startsWith(surveyNo + "."));
        assertTrue(tokenService.matches(surveyNo, token, hash));
        assertEquals(hash, tokenService.hash(surveyNo, token));
        assertEquals(surveyNo, tokenService.surveyNoOf(token));
    }

    @Test
    @DisplayName("令牌：篡改 nonce 或问卷编号后校验失败")
    void shouldRejectTamperedToken() {
        String surveyNo = "CSAT-20261107-0001";
        String token = tokenService.generate(surveyNo);
        String hash = tokenService.hash(surveyNo, token);
        String otherToken = tokenService.generate(surveyNo);

        assertNotEquals(token, otherToken);
        assertFalse(tokenService.matches(surveyNo, otherToken, hash));
        assertFalse(tokenService.matches("CSAT-20261107-0002", token, hash));
    }

    @Test
    @DisplayName("令牌格式非法：抛 SURVEY_TOKEN_INVALID")
    void shouldRejectMalformedToken() {
        BizException exception = assertThrows(BizException.class, () -> tokenService.surveyNoOf("no-dot-token"));

        assertEquals(CrmErrorCode.SURVEY_TOKEN_INVALID, exception.getCode());
    }
}
