package com.bluewhale.form.service;

/**
 * 表单元数据迁移服务：将历史 {@code form_definition.schema_json} 反推为三层元数据（幂等）。
 *
 * <p>对 {@code table_definition_id IS NULL} 且 {@code schema_json} 非空的表单定义：
 * 建表定义 + 列元数据 + 字段配置 + 选项，并回填 {@code form_definition}、{@code form_table_registry}
 * 与 {@code form_data} 的表维度列。因 {@code table_code == form.code} 且 {@code table_version == form.version}，
 * 迁移后物理表名与 Wave 5.27 完全一致，存量物理表与数据零改动。</p>
 *
 * @author BlueWhale
 */
public interface FormMetadataMigrationService {

    /**
     * 迁移全部未迁移且 {@code schema_json} 非空的表单定义（幂等，重复执行不产生副作用）
     *
     * @return 本次实际迁移的表单定义数量
     */
    int migrateLegacy();
}
