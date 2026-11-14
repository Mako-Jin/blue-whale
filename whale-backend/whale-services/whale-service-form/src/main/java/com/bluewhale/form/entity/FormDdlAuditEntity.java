package com.bluewhale.form.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单物理表 DDL 审计记录（只追加，不参与逻辑删除）。
 *
 * <p>记录每次发布时实际执行的建表/变更语句，用于追溯「谁、何时、对哪个定义、执行了什么 DDL」，
 * 并为 DDL 隐式提交导致的孤儿表/半迁移提供对账依据。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("form_ddl_audit")
public class FormDdlAuditEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 定义类型：FORM 旧模型 / TABLE 元数据模型 */
    private String definitionType;

    /** 定义ID（表单定义ID或表定义ID） */
    private Long definitionId;

    /** 物理表名 */
    private String physicalTable;

    /** 实际执行的 DDL 语句 */
    private String ddlStatement;

    /** 是否成功：1 成功 / 0 失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMessage;

    /** 操作人 */
    private String operator;

    /** 创建时间 */
    private LocalDateTime createTime;
}
