package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 播放票据：短期有效的播放授权凭证，绑定租户、用户与内容三要素。
 *
 * <p>长视频一次播放会发起多次 Range 请求，票据在有效期内可复用（默认 300 秒，
 * 客户端 240 秒主动刷新），但不得跨用户、跨内容或跨租户使用。Redis 中仅保存
 * {@code tenantId|userId|contentId} 文本，票据本身为随机令牌。</p>
 *
 * @author BlueWhale
 */
@Data
public class PlayTicket {

    /** 票据令牌（随机串，不包含业务信息） */
    private String ticket;

    /** 绑定租户 ID */
    private Long tenantId;

    /** 绑定用户 ID */
    private Long userId;

    /** 绑定内容 ID */
    private Long contentId;

    /**
     * 序列化为 Redis 存储值。
     *
     * @return {@code tenantId|userId|contentId}
     */
    public String toValue() {
        return tenantId + "|" + userId + "|" + contentId;
    }

    /**
     * 从 Redis 存储值反序列化票据绑定信息。
     *
     * @param ticket 票据令牌
     * @param value 存储值
     * @return 播放票据；格式非法返回 {@code null}
     */
    public static PlayTicket parse(String ticket, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split("\\|");
        if (parts.length != 3) {
            return null;
        }
        try {
            PlayTicket parsed = new PlayTicket();
            parsed.setTicket(ticket);
            parsed.setTenantId(Long.valueOf(parts[0].trim()));
            parsed.setUserId(Long.valueOf(parts[1].trim()));
            parsed.setContentId(Long.valueOf(parts[2].trim()));
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 判断票据是否可用于当前请求的三要素。
     *
     * @param tenantId 当前租户 ID
     * @param userId 当前用户 ID
     * @param contentId 当前内容 ID
     * @return 三要素全部一致返回 {@code true}
     */
    public boolean matches(Long tenantId, Long userId, Long contentId) {
        return tenantId != null && userId != null && contentId != null
                && tenantId.equals(this.tenantId)
                && userId.equals(this.userId)
                && contentId.equals(this.contentId);
    }
}
