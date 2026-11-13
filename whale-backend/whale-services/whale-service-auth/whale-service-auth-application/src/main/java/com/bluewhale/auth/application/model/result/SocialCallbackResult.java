package com.bluewhale.auth.application.model.result;

/**
 * 三方回调处理结果（权限中心扩展 M2）。
 *
 * <p>无论成功失败，服务侧都以 302 重定向回前端 {@code redirect} 页面，通过
 * {@code code}/{@code error} 查询参数传达结果，前端再以一次性登录码换令牌。</p>
 *
 * @param outcome 处理结果
 * @param redirectUrl 前端回跳完整 URL（含查询参数）
 * @param provider 渠道编码（小写）
 * @param nickname 三方昵称（仅 LOGGED_IN / NEED_BIND 分支可能有值，供前端提示）
 * @author BlueWhale
 */
public record SocialCallbackResult(SocialCallbackOutcome outcome, String redirectUrl,
                                   String provider, String nickname) {

    /**
     * 回调处理结果枚举
     *
     * @author BlueWhale
     */
    public enum SocialCallbackOutcome {
        /** 绑定存在且有效：签发一次性登录码，前端凭码换令牌 */
        LOGGED_IN,
        /** 三方邮箱已存在本租户账号但未绑定：要求先用口令登录，再在账号设置中绑定（防账号劫持） */
        NEED_BIND,
        /** 无绑定记录且租户未开启自助注册：拒绝自动建号 */
        REGISTER_DISABLED,
        /** state 非法 / 过期 / 已消费（重复回调） */
        INVALID_STATE,
        /** 渠道换票失败或用户拒绝授权 */
        PROVIDER_ERROR,
        /** 绑定意图完成（绑定成功后引导前端关闭授权窗口或提示刷新） */
        BIND_DONE
    }
}
