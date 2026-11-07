package com.bluewhale.crm.service.domain.port;

/**
 * 满意度一次性令牌端口。
 *
 * @author BlueWhale
 */
public interface SurveyTokenPort {

    /**
     * 生成一次性令牌。
     *
     * @param surveyNo 问卷编号
     * @return 令牌（surveyNo.nonce）
     */
    String generate(String surveyNo);

    /**
     * 计算令牌哈希。
     *
     * @param surveyNo 问卷编号
     * @param token 完整令牌
     * @return 十六进制哈希
     */
    String hash(String surveyNo, String token);

    /**
     * 校验令牌哈希是否匹配。
     *
     * @param surveyNo 问卷编号
     * @param token 完整令牌
     * @param expectedHash 期望哈希
     * @return 是否匹配
     */
    boolean matches(String surveyNo, String token, String expectedHash);

    /**
     * 从令牌提取问卷编号。
     *
     * @param token 完整令牌
     * @return 问卷编号
     */
    String surveyNoOf(String token);
}
