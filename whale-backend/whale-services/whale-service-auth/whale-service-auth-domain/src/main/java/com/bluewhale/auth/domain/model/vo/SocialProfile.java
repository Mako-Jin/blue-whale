package com.bluewhale.auth.domain.model.vo;

import java.util.Objects;

/**
 * 三方用户资料值对象（权限中心扩展 M2）：适配器换票后返回的统一身份快照。
 *
 * <p>字段可为空——不同渠道授权范围不同（如企业微信不返回邮箱），应用层按「能拿什么落什么」
 * 的口径落库，不得对空字段做强制假设。</p>
 *
 * @param provider 三方渠道
 * @param providerUserId 三方用户唯一标识
 * @param unionId 跨应用唯一标识（可空）
 * @param nickname 昵称（可空）
 * @param avatar 头像 URL（可空）
 * @param email 邮箱（可空，仅在渠道授权范围包含时返回）
 * @author BlueWhale
 */
public record SocialProfile(SocialProvider provider,
                            ProviderUserId providerUserId,
                            String unionId,
                            String nickname,
                            String avatar,
                            String email) {

    /**
     * 紧凑构造器：渠道与三方标识必填，其余文本做空白裁剪
     */
    public SocialProfile {
        Objects.requireNonNull(provider, "三方渠道不能为空");
        Objects.requireNonNull(providerUserId, "三方用户标识不能为空");
        unionId = trimToNull(unionId);
        nickname = trimToNull(nickname);
        avatar = trimToNull(avatar);
        email = trimToNull(email);
    }

    /**
     * 工厂方法
     *
     * @param provider 三方渠道
     * @param providerUserId 三方用户唯一标识
     * @param unionId 跨应用唯一标识（可空）
     * @param nickname 昵称（可空）
     * @param avatar 头像 URL（可空）
     * @param email 邮箱（可空）
     * @return 三方用户资料
     */
    public static SocialProfile of(SocialProvider provider, String providerUserId, String unionId,
                                   String nickname, String avatar, String email) {
        return new SocialProfile(provider, ProviderUserId.of(providerUserId),
                unionId, nickname, avatar, email);
    }

    /**
     * 裁剪空白并归一化空串为 {@code null}
     *
     * @param text 原始文本
     * @return 裁剪后的文本；空白返回 {@code null}
     */
    private static String trimToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
