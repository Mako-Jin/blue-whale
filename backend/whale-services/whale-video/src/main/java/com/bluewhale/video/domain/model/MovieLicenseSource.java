package com.bluewhale.video.domain.model;

/**
 * 电影授权来源：自有版权 / 已获授权 / 公有领域。
 *
 * <p>运营入库时必须声明一种来源，并填写授权到期时间；到期由定时任务自动下线。</p>
 *
 * @author BlueWhale
 */
public enum MovieLicenseSource {

    /** 自有版权（平台自己制作或全额买断） */
    OWNED,

    /** 已获授权（第三方授权，必须可追溯到授权编号） */
    LICENSED,

    /** 公有领域（版权已过期或放弃版权） */
    PUBLIC_DOMAIN;

    /**
     * 解析授权来源文本。
     *
     * @param value 存储 / 请求文本
     * @return 授权来源；无法识别返回 {@code null}
     */
    public static MovieLicenseSource parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (MovieLicenseSource item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }
}
