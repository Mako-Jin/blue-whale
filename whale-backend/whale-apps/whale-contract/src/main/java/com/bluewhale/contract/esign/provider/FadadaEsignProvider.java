package com.bluewhale.contract.esign.provider;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.stereotype.Component;

/**
 * 法大大渠道。
 *
 * <p>按 {@code providers.fadada} 的 base-url/app-id/app-secret、create/query/download
 * 路径调用开放平台 HTTP 接口；回调默认使用统一 HMAC 验签，若切换到法大大事件签名
 * 方案可覆盖 {@code verifyCallback}。</p>
 *
 * @author BlueWhale
 */
@Component
public class FadadaEsignProvider extends AbstractHmacEsignProvider {

    /**
     * 构造法大大渠道。
     *
     * @param properties 签章配置
     */
    public FadadaEsignProvider(ContractEsignProperties properties) {
        super("fadada", properties);
    }
}
