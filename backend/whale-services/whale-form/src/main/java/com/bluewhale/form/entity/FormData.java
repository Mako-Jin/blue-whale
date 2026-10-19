package com.bluewhale.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单数据实体，对应表 {@code form_data}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code form_data} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_data")
public class FormData extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离；取自 {@code X-Tenant-Id}，缺省 1） */
    private Long tenantId;

    /** 表单定义 ID */
    private Long definitionId;

    /** 提交时的表单定义编码（冗余，便于查询与展示） */
    private String definitionCode;

    /** 提交时的表单定义版本（冗余快照） */
    private Integer definitionVersion;

    /** 业务键（可选，用于关联外部业务单据） */
    private String bizKey;

    /** 表单数据（JSON 文本，最长 64KB，即 65536 字符） */
    private String dataJson;

    /** 提交人账号 */
    private String submitter;

    /** 状态：1 有效 / 0 作废 */
    private Integer status;
}
