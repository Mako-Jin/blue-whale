package com.bluewhale.pay.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量重试通知任务请求。
 *
 * @author BlueWhale
 */
@Data
public class PayNotifyRetryBatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID 列表（按顺序去重后逐项重试） */
    @NotEmpty(message = "{validation.pay.notify-retry.ids-required}")
    @Size(max = 100, message = "{validation.pay.notify-retry.ids-size}")
    private List<Long> ids;
}
