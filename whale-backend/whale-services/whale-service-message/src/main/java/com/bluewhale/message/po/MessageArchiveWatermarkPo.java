package com.bluewhale.message.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息归档水位，对应表 {@code msg_archive_watermark}。
 *
 * <p>归档不删除源数据，每轮若按「早于保留期」全量查询会重复导出同一批消息。
 * 本表按租户单行（{@code id} 即 {@code tenant_id}）记录上一批成功归档的最后一条
 * {@code create_time + id}，下一轮据此增量续查。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("msg_archive_watermark")
public class MessageArchiveWatermarkPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，即租户ID */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 已归档批次的最后一条 {@code msg_record.create_time} */
    private LocalDateTime lastCreateTime;

    /** 已归档批次的最后一条 {@code msg_record.id}（同一 time 内去重） */
    private Long lastId;

    /** 水位更新时间 */
    private LocalDateTime updateTime;
}
