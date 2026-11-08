package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量触达明细视图。
 *
 * @author BlueWhale
 */
@Data
public class MarketingSendItemView {

    /** 明细 ID */
    private Long id;

    /** 触达任务 ID */
    private Long sendId;

    /** 客户 ID */
    private Long customerId;

    /** 联系人 ID */
    private Long contactId;

    /** 联系人姓名 */
    private String contactName;

    /** 收件人 */
    private String recipient;

    /** 渠道 */
    private String channel;

    /** 明细状态 */
    private String status;

    /** 消息服务消息ID */
    private Long msgId;

    /** 失败原因 */
    private String error;

    /** 发送时间 */
    private LocalDateTime sentAt;

    /** 渠道确认送达时间 */
    private LocalDateTime deliveredAt;

    /** 已读标记：0 未读 / 1 已读 */
    private Integer readFlag;

    /**
     * 由领域对象构建视图。
     *
     * @param item 明细
     * @return 视图
     */
    public static MarketingSendItemView from(MarketingSendItem item) {
        MarketingSendItemView view = new MarketingSendItemView();
        view.setId(item.getId());
        view.setSendId(item.getSendId());
        view.setCustomerId(item.getCustomerId());
        view.setContactId(item.getContactId());
        view.setContactName(item.getContactName());
        view.setRecipient(item.getRecipient());
        view.setChannel(item.getChannel() == null ? null : item.getChannel().name());
        view.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        view.setMsgId(item.getMsgId());
        view.setError(item.getError());
        view.setSentAt(item.getSentAt());
        view.setDeliveredAt(item.getDeliveredAt());
        view.setReadFlag(item.getReadFlag());
        return view;
    }
}
