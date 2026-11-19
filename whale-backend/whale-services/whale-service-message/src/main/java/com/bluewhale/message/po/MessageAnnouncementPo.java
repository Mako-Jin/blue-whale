package com.bluewhale.message.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统公告实体，对应表 {@code msg_announcement}。
 *
 * <p>标题/正文/业务类型在关联的 {@code msg_record} 上，本表只管范围与生命周期；
 * 用户打开收件箱时由 {@code AnnouncementExpander} 惰性展开为个人投递记录。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_announcement")
public class MessageAnnouncementPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 消息ID（关联 msg_record.id，标题/正文/业务类型在此） */
    private Long recordId;

    /** 公告状态：PUBLISHED / REVOKED */
    private String status;

    /** 发布人用户ID */
    private Long publisherId;

    /** 发布时间 */
    private LocalDateTime publishTime;
}
