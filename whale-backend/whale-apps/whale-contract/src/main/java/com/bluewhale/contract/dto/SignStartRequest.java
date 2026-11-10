package com.bluewhale.contract.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 发起签署请求。
 *
 * <p>{@code tasks} 为空时按合同相对方中已登记 {@code signerUserId} 的签署人生成任务；
 * 全部相对方均无平台账号时，回落到合同负责人作为签署人。</p>
 *
 * @author BlueWhale
 */
@Data
public class SignStartRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指定签署任务；为空则按相对方自动生成 */
    private List<SignTaskCreateRequest> tasks;

    /** 签署渠道：默认 LOCAL */
    private String provider;
}
