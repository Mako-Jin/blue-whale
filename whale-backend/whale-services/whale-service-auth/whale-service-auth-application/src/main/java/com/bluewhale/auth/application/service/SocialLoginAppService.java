package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SocialAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialBindAuthorizeCommand;
import com.bluewhale.auth.application.model.command.SocialCallbackCommand;
import com.bluewhale.auth.application.model.command.SocialLoginByCodeCommand;
import com.bluewhale.auth.application.model.command.SocialUnbindCommand;
import com.bluewhale.auth.application.model.result.LoginResult;
import com.bluewhale.auth.application.model.result.SocialAuthorizeResult;
import com.bluewhale.auth.application.model.result.SocialBindingResult;
import com.bluewhale.auth.application.model.result.SocialCallbackResult;

import java.util.List;

/**
 * 三方登录应用服务（权限中心扩展 M2）：授权起始、回调裁决与绑定管理。
 *
 * <p>账号落地三态（计划 §7-4）：已绑定→直接登录；同邮箱未绑定→要求先登录再绑定
 * （防账号劫持）；无记录→受租户 {@code auth.social.self_register_enabled} 开关约束自动建号
 * （{@code register_source=SOCIAL}）。所有分支均以 302 回跳前端并携带一次性登录码或错误标记。</p>
 *
 * @author BlueWhale
 */
public interface SocialLoginAppService {

    /**
     * 登录意图：生成渠道授权页 URL（免登录）
     *
     * @param command 授权起始命令（渠道、目标租户编码、前端回跳路径）
     * @return 授权页 URL
     */
    SocialAuthorizeResult startLogin(SocialAuthorizeCommand command);

    /**
     * 绑定意图：为当前登录用户生成渠道授权页 URL（须已登录）
     *
     * @param command 绑定授权起始命令（渠道、前端回跳路径）
     * @return 授权页 URL
     */
    SocialAuthorizeResult startBind(SocialBindAuthorizeCommand command);

    /**
     * 渠道回调裁决：换票、按绑定/邮箱/自助注册三态落地
     *
     * @param command 回调命令（渠道、授权码、state）
     * @return 回调处理结果（含前端回跳 URL）
     */
    SocialCallbackResult completeCallback(SocialCallbackCommand command);

    /**
     * 一次性登录码换令牌（免登录；码一次性、短时效）
     *
     * @param command 换令牌命令
     * @return 登录结果（同口令登录，refreshToken 仅由接口层写 Cookie）
     */
    LoginResult exchangeLoginCode(SocialLoginByCodeCommand command);

    /**
     * 当前用户的有效三方绑定列表（须已登录）
     *
     * @return 绑定列表（按绑定时间升序）
     */
    List<SocialBindingResult> listBindings();

    /**
     * 解绑当前用户的指定渠道绑定（须已登录；解绑后该三方身份不再可登录）
     *
     * @param command 解绑命令
     */
    void unbind(SocialUnbindCommand command);
}
