package com.bluewhale.auth.application.port;

import com.bluewhale.auth.domain.model.vo.SocialProfile;
import com.bluewhale.auth.domain.model.vo.SocialProvider;

/**
 * 三方身份提供者出站端口（权限中心扩展 M2）：由基础设施适配器按渠道实现。
 *
 * <p>应用层只面向「构建授权链接 + 以授权码换用户资料」两个能力，不感知各渠道
 * 协议差异（钉钉/飞书为 OAuth2 换票，企业微信为企业内应用取票）。回调地址由适配器
 * 取自 {@code whale.auth.social.callback-base-url} 配置；适配器实现须为无状态组件，
 * 渠道级配置（client-id / client-secret）由适配器自行读取。</p>
 *
 * @author BlueWhale
 */
public interface SocialIdentityProvider {

    /**
     * 本适配器支持的渠道
     *
     * @return 渠道枚举
     */
    SocialProvider support();

    /**
     * 构建渠道授权页 URL（扫码 / 确认授权入口）
     *
     * @param state 防伪随机串（原样透传给渠道，回调时校验）
     * @return 授权页完整 URL
     */
    String buildAuthorizeUrl(String state);

    /**
     * 以授权码换取三方用户资料
     *
     * @param code 渠道回调携带的授权码
     * @return 统一三方用户资料
     */
    SocialProfile exchange(String code);
}
