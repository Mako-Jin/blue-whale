package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更通知（ECN）视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgNoticeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 通知编号 */
    private String noticeNo;

    /** 来源变更单ID */
    private Long ecoId;

    /** 来源变更单编号 */
    private String ecoNo;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 影响对象 */
    private List<PdmChgImpactObjectVO> impactObjects;

    /** 接收人ID */
    private List<Long> notifyTargets;

    /** 通知渠道 */
    private List<String> notifyChannels;

    /** 生效日期 */
    private LocalDateTime effectiveFrom;

    /** 状态 */
    private String status;

    /** 通知时间 */
    private LocalDateTime notifiedTime;

    /** 实际生效时间 */
    private LocalDateTime effectiveTime;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
