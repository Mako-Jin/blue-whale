package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.util.Arrays;

/**
 * 三方登录渠道值对象（权限中心扩展 M2）。
 *
 * <p>首批渠道：钉钉（{@code dingtalk}）、飞书（{@code feishu}）、企业微信（{@code wecom}）。
 * 渠道编码对外统一小写，与网关路径段、绑定表 {@code provider} 列一致；领域内统一使用本枚举，
 * 禁止裸字符串比较。</p>
 *
 * @author BlueWhale
 */
public enum SocialProvider {

    /** 钉钉扫码登录 */
    DINGTALK("dingtalk", "钉钉"),
    /** 飞书扫码登录 */
    FEISHU("feishu", "飞书"),
    /** 企业微信扫码登录 */
    WECOM("wecom", "企业微信");

    /** 渠道编码（小写，对外契约与存储值） */
    private final String code;
    /** 渠道显示名 */
    private final String displayName;

    /**
     * 私有构造器
     *
     * @param code 渠道编码（小写）
     * @param displayName 渠道显示名
     */
    SocialProvider(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 获取渠道编码
     *
     * @return 渠道编码（小写）
     */
    public String code() {
        return code;
    }

    /**
     * 获取渠道显示名
     *
     * @return 显示名
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 按编码解析渠道（大小写不敏感）
     *
     * @param code 渠道编码
     * @return 渠道枚举
     * @throws DomainException 编码为空或不在支持列表时抛出
     */
    public static SocialProvider of(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "三方登录渠道不能为空");
        }
        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.PARAM_ERROR,
                        "不支持的三方登录渠道: " + code));
    }
}
