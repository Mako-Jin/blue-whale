package com.bluewhale.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单物理表登记实体，对应表 {@code form_table_registry}。
 *
 * <p>登记「表单定义（编码 + 版本）↔ 物理表」的映射，并保留发布时的 {@code schema_json} 快照，
 * 使任意版本的表单数据都能按其 {@code definition_version} 路由到对应物理表并解析出列集合。</p>
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code form_table_registry} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_table_registry")
public class FormTableRegistry extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 表单定义 ID */
    private Long definitionId;

    /** 表单定义编码 */
    private String definitionCode;

    /** 表单定义版本 */
    private Integer definitionVersion;

    /** 物理表名（{@code form_data_{code}_v{version}}） */
    private String physicalTable;

    /** 动态字段数量 */
    private Integer fieldCount;

    /** 发布时的表单结构定义快照（JSON 文本，读旧版本数据的列来源） */
    private String schemaJson;

    /** 状态：1 生效 / 0 废弃 */
    private Integer status;
}
