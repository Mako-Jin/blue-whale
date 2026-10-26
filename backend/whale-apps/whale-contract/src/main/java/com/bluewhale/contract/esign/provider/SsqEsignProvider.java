package com.bluewhale.contract.esign.provider;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.stereotype.Component;

/**
 * 上上签渠道（骨架）。
 *
 * <p>接入真实开放平台时按 {@code providers.ssq} 配置调用创建/查询/下载接口，
 * 回调验签替换为上上签签名方案；当前骨架提供任务号生成、签署链接模板与统一 HMAC 回调验签。</p>
 *
 * @author BlueWhale
 */
@Component
public class SsqEsignProvider extends AbstractHmacEsignProvider {

    /**
     * 构造上上签渠道。
     *
     * @param properties 签章配置
     */
    public SsqEsignProvider(ContractEsignProperties properties) {
        super("ssq", properties);
    }
}
