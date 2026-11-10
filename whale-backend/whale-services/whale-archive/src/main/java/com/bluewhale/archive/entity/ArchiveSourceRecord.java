package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 档案同步台账实体，对应表 {@code archive_source_record}。
 *
 * <p>{@code (sourceType, sourceId)} 唯一，作为增量同步的幂等键；失败时记录
 * {@code retryCount} 与 {@code lastError}，由下一轮同步重试。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_source_record")
public class ArchiveSourceRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 来源类型：WORKFLOW 工作流实例 */
    private String sourceType;

    /** 来源实例ID */
    private String sourceId;

    /** 同步状态：0 待处理 / 1 成功 / 2 失败 */
    private Integer syncStatus;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最近一次失败原因（已脱敏；成功时清空，需允许写入 NULL） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;

    /** 最近一次同步时间 */
    private LocalDateTime lastSyncAt;
}
