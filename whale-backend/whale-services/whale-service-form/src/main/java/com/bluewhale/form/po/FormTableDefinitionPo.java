package com.bluewhale.form.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表定义实体，对应表 {@code form_table_definition}（三层模型的表定义层）。
 *
 * <p>描述一张业务表（编码 + 版本）；其列定义见 {@link FormColumnMetaPo}，可被多张表单视图
 * （{@link FormDefinitionPo}）共享（表:表单 = 1:N）。物理表名 {@code form_data_{tableCode}_v{version}}
 * 由本实体决定。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_table_definition")
public class FormTableDefinitionPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表编码（业务唯一；物理表名来源，规则 {@code ^[a-z][a-z0-9_]{0,39}$}） */
    private String tableCode;

    /** 表名称 */
    private String tableName;

    /** 版本号（默认 1） */
    private Integer version;

    /** 备注 */
    private String remark;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;
}
