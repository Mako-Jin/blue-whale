package com.bluewhale.contract.esign.provider;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.stereotype.Component;

/**
 * e签宝渠道。
 *
 * <p>按 {@code providers.esignbao} 的 base-url/app-id/app-secret、create/query/download
 * 路径调用开放平台 HTTP 接口；回调默认使用统一 HMAC 验签，若切换到 e签宝 RSA/SM2
 * 方案可覆盖 {@code verifyCallback}。</p>
 *
 * @author BlueWhale
 */
@Component
public class EsignbaoEsignProvider extends AbstractHmacEsignProvider {

    /**
     * 构造 e签宝渠道。
     *
     * @param properties 签章配置
     */
    public EsignbaoEsignProvider(ContractEsignProperties properties) {
        super("esignbao", properties);
    }
}
