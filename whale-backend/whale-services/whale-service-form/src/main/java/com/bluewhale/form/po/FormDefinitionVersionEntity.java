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
 * 表单定义版本快照实体，对应表 {@code form_definition_version}。
 *
 * <p>记录表单定义某一版本的「定义 + 字段 + 选项」完整快照，用于版本回滚。
 * 表中无 {@code update_time / deleted}，故不继承公共实体基类。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("form_definition_version")
public class FormDefinitionVersionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 表单定义ID */
    private Long definitionId;

    /** 版本号（发布版本，可被多次编辑共享） */
    private Integer version;

    /** 快照序号（同一表单定义内单调递增，每次保存/回滚新增一条，不复用） */
    private Long snapshotSeq;

    /** 定义 + 字段 + 选项的完整快照 JSON */
    private String snapshotJson;

    /** 创建时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
