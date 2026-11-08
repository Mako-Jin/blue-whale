package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.MarketingSend;
import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量触达任务视图。
 *
 * @author BlueWhale
 */
@Data
public class MarketingSendView {

    /** 任务 ID */
    private Long id;

    /** 任务编号 */
    private String sendNo;

    /** 活动 ID */
    private Long campaignId;

    /** 人群快照 ID */
    private Long audienceId;

    /** 渠道 */
    private String channel;

    /** 模板编码 */
    private String templateCode;

    /** 主题 */
    private String subject;

    /** 内容文档 ID */
    private Long contentRefDocId;

    /** 状态 */
    private String status;

    /** 收件人总数 */
    private Integer totalCount;

    /** 成功数量 */
    private Integer sentCount;

    /** 跳过数量 */
    private Integer skippedCount;

    /** 失败数量 */
    private Integer failedCount;

    /** 已送达数量（含已读） */
    private Integer deliveredCount;

    /** 退信数量 */
    private Integer bouncedCount;

    /** 已读数量 */
    private Integer readCount;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 最近一次失败原因 */
    private String lastError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 触达明细（仅详情返回） */
    private List<MarketingSendItemView> items;

    /**
     * 由聚合构建视图。
     *
     * @param send 触达任务
     * @param items 明细，可为 null
     * @return 视图
     */
    public static MarketingSendView from(MarketingSend send, List<MarketingSendItem> items) {
        MarketingSendView view = new MarketingSendView();
        view.setId(send.getId());
        view.setSendNo(send.getSendNo());
        view.setCampaignId(send.getCampaignId());
        view.setAudienceId(send.getAudienceId());
        view.setChannel(send.getChannel() == null ? null : send.getChannel().name());
        view.setTemplateCode(send.getTemplateCode());
        view.setSubject(send.getSubject());
        view.setContentRefDocId(send.getContentRefDocId());
        view.setStatus(send.getStatus() == null ? null : send.getStatus().name());
        view.setTotalCount(send.getTotalCount());
        view.setSentCount(send.getSentCount());
        view.setSkippedCount(send.getSkippedCount());
        view.setFailedCount(send.getFailedCount());
        view.setDeliveredCount(send.getDeliveredCount());
        view.setBouncedCount(send.getBouncedCount());
        view.setReadCount(send.getReadCount());
        view.setExecutedAt(send.getExecutedAt());
        view.setLastError(send.getLastError());
        view.setOwnerId(send.getOwnerId());
        view.setDeptId(send.getDeptId());
        view.setCreateTime(send.getCreateTime());
        view.setUpdateTime(send.getUpdateTime());
        if (items != null) {
            view.setItems(items.stream().map(MarketingSendItemView::from).toList());
        }
        return view;
    }
}
