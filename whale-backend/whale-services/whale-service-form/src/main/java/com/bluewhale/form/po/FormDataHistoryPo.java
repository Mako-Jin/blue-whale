package com.bluewhale.form.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单数据变更历史实体，对应表 {@code form_data_history}。
 *
 * <p>只追加、不更新、不删除，也不参与逻辑删除（表无 {@code deleted} 列），
 * 因此不继承 {@code BaseEntity}；主键与创建时间由 MyBatis-Plus 分别按雪花算法与
 * 插入填充策略生成。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("form_data_history")
public class FormDataHistoryPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 表单数据ID（{@code form_data.id}） */
    private Long dataId;

    /** 表单编码（冗余，便于按表单查询） */
    private String definitionCode;

    /** 变更类型：CREATE / UPDATE / DELETE */
    private String changeType;

    /** 变更前数据JSON（CREATE 为空） */
    private String beforeJson;

    /** 变更后数据JSON（DELETE 为空） */
    private String afterJson;

    /** 本次变更的字段编码（逗号分隔） */
    private String changedKeys;

    /** 操作人账号 */
    private String operator;

    /** 变更时间 */
    private LocalDateTime changeTime;

    /** 写入时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
