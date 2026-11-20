package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 回调地址安全校验（SSRF 防护）。
 *
 * <p>回调地址由业务方在发送请求中提供，派发器会主动发起 HTTP 请求，属于典型的
 * SSRF 入口。这里拦截最危险的目标：云厂商元数据地址与链路本地网段，
 * 不限制业务内网地址（内网回调是本能力的预期用法）。</p>
 *
 * <p>限制说明：本类只做字面量校验，无法防御「允许的域名解析到内网」的 DNS 重绑定；
 * 若后续引入自定义域名白名单，应在派发侧解析后复核 IP。</p>
 *
 * @author BlueWhale
 */
public final class CallbackUrlGuard {

    /** 云厂商元数据固定主机名 */
    private static final Set<String> METADATA_HOSTS = Set.of(
            "169.254.169.254",
            "metadata.google.internal",
            "metadata.tencentyun.com",
            "100.100.100.200");

    /** 允许的协议 */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private CallbackUrlGuard() {
    }

    /**
     * 校验回调地址；为空视为未配置回调，直接通过。
     *
     * @param callbackUrl 回调地址
     */
    public static void assertAllowed(String callbackUrl) {
        if (!StringUtils.hasText(callbackUrl)) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(callbackUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "message.callback-url.invalid-format",
                    "回调地址格式非法: " + callbackUrl, callbackUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "message.callback-url.scheme-unsupported",
                    "回调地址仅支持 http / https: " + callbackUrl, callbackUrl);
        }
        if (uri.getUserInfo() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "message.callback-url.userinfo-forbidden",
                    "回调地址不允许携带用户信息: " + callbackUrl, callbackUrl);
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "message.callback-url.host-missing",
                    "回调地址缺少主机名: " + callbackUrl, callbackUrl);
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (METADATA_HOSTS.contains(normalized) || normalized.startsWith("169.254.")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "message.callback-url.metadata-forbidden",
                    "回调地址不允许指向云元数据或链路本地地址: " + callbackUrl, callbackUrl);
        }
    }
}
