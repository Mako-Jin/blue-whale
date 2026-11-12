package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 申诉材料实体，对应表 {@code fm_appeal_material}。
 *
 * <p>只存 whale-file 的文件引用与展示用元数据快照，不存字节；
 * 表为 append-only，故不继承 BaseEntity（无 {@code update_time} / {@code deleted}）。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_appeal_material")
public class ForumAppealMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 申诉工单ID */
    private Long appealId;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名快照 */
    private String fileName;

    /** 文件字节数快照 */
    private Long fileSize;

    /** MIME 类型快照 */
    private String contentType;

    /** 展示顺序（提交顺序） */
    private Integer sortNo;

    /** 登记时间 */
    private LocalDateTime createTime;
}
