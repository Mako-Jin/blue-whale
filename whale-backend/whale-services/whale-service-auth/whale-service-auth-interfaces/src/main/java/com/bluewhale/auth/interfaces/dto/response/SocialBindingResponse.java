package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户三方绑定视图响应（权限中心扩展 M2，账号设置页数据源）。
 *
 * @author BlueWhale
 */
@Data
public class SocialBindingResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道编码（小写） */
    private String provider;
    /** 渠道显示名 */
    private String providerName;
    /** 三方昵称快照（可空） */
    private String nickname;
    /** 三方头像快照（可空） */
    private String avatar;
    /** 绑定时间 */
    private LocalDateTime boundTime;

    /**
     * 构造绑定视图响应
     *
     * @param provider 渠道编码
     * @param providerName 渠道显示名
     * @param nickname 昵称
     * @param avatar 头像
     * @param boundTime 绑定时间
     * @return 响应对象
     */
    public static SocialBindingResponse of(String provider, String providerName,
                                           String nickname, String avatar, LocalDateTime boundTime) {
        SocialBindingResponse response = new SocialBindingResponse();
        response.setProvider(provider);
        response.setProviderName(providerName);
        response.setNickname(nickname);
        response.setAvatar(avatar);
        response.setBoundTime(boundTime);
        return response;
    }

    /**
     * 便捷方法：空列表占位（避免调用方判空）
     *
     * @return 空列表
     */
    public static List<SocialBindingResponse> emptyList() {
        return new ArrayList<>();
    }
}
