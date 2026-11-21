package com.bluewhale.pay.vo;

import com.bluewhale.pay.enums.PayNotifyRetryStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * 手工重试通知任务的结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class PayNotifyRetryResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 结果状态：SUCCESS / IN_PROGRESS / RETRY_PENDING */
    private String status;

    /** 面向操作者的说明 */
    private String message;

    /**
     * 构造结果。
     *
     * @param status  结果状态
     * @param message 说明
     * @return 结果视图对象
     */
    public static PayNotifyRetryResultVO of(PayNotifyRetryStatus status, String message) {
        PayNotifyRetryResultVO vo = new PayNotifyRetryResultVO();
        vo.setStatus(status.code());
        vo.setMessage(message);
        return vo;
    }
}
