package com.bluewhale.contract.esign.provider;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.contract.config.ContractEsignProperties;
import org.springframework.stereotype.Component;

/**
 * 本地电子签渠道（默认）：复用平台内 PDF 盖章 + CMS 签名。
 *
 * <p>本地渠道不产生三方任务号与签署链接，签署由 {@code /contract/sign-task/{id}/sign} 完成；
 * 回调验签复用 HMAC 骨架，便于联调验证回调链路。</p>
 *
 * @author BlueWhale
 */
@Component
public class LocalEsignProvider extends AbstractHmacEsignProvider {

    /**
     * 构造本地渠道。
     *
     * @param properties 签章配置
     */
    public LocalEsignProvider(ContractEsignProperties properties) {
        super("local", properties);
    }

    @Override
    public EsignTaskResult createTask(EsignTaskCommand command) {
        return new EsignTaskResult("LOCAL-" + command.signTaskId(), null);
    }

    @Override
    public byte[] downloadSignedFile(String providerTaskId) {
        throw new BizException(ErrorCode.PARAM_ERROR, "本地签章件由本地签署接口生成，不提供下载");
    }
}
