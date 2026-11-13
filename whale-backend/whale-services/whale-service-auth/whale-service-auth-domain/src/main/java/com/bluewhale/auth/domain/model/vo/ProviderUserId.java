package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

/**
 * 三方用户唯一标识值对象（权限中心扩展 M2）。
 *
 * <p>对应绑定表 {@code provider_user_id} 列：钉钉 openid、飞书 open_id、企业微信 userid。
 * 与 {@code SocialProvider} 组合后全局唯一，是三方身份的事实主键。</p>
 *
 * @param value 三方用户唯一标识
 * @author BlueWhale
 */
public record ProviderUserId(String value) {

    /** 最大长度（与绑定表列宽一致） */
    private static final int MAX_LENGTH = 128;

    /**
     * 紧凑构造器：集中校验非空与长度
     */
    public ProviderUserId {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "三方用户标识不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "三方用户标识超长（最多 " + MAX_LENGTH + " 字符）");
        }
        value = trimmed;
    }

    /**
     * 工厂方法
     *
     * @param value 三方用户标识
     * @return 值对象
     */
    public static ProviderUserId of(String value) {
        return new ProviderUserId(value);
    }
}
