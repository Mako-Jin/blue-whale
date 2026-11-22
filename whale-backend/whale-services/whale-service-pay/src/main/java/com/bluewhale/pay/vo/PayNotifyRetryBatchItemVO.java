package com.bluewhale.pay.vo;

import com.bluewhale.pay.enums.PayNotifyRetryStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * 批量重试通知任务的单项结果。
 *
 * @author BlueWhale
 */
@Data
public class PayNotifyRetryBatchItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long id;

    /** 结果状态：SUCCESS / IN_PROGRESS / RETRY_PENDING / NOT_FOUND */
    private String status;

    /** 面向操作者的说明 */
    private String message;

    /**
     * 构造单项结果。
     *
     * @param id      任务 ID
     * @param status  结果状态
     * @param message 说明
     * @return 单项结果
     */
    public static PayNotifyRetryBatchItemVO of(Long id, PayNotifyRetryStatus status, String message) {
        PayNotifyRetryBatchItemVO vo = new PayNotifyRetryBatchItemVO();
        vo.setId(id);
        vo.setStatus(status.code());
        vo.setMessage(message);
        return vo;
    }
}
