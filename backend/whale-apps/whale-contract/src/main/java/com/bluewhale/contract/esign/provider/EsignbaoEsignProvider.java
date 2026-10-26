package com.bluewhale.contract.esign.provider;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.stereotype.Component;

/**
 * e签宝渠道（骨架）。
 *
 * <p>接入真实开放平台时：按 {@code providers.esignbao} 的 base-url/app-id/app-secret 调用
 * 创建/查询/下载接口，并将回调验签替换为 e签宝 RSA/SM2 方案；当前骨架提供任务号生成、
 * 签署链接模板与统一 HMAC 回调验签。</p>
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
