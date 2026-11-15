package com.bluewhale.form.service;

import com.bluewhale.form.model.vo.FormTableRegistryVO;

import java.util.List;

/**
 * 表单发布服务：将表单定义的 {@code schema_json} 落为物理表并登记版本映射。
 *
 * @author BlueWhale
 */
public interface FormPublishService {

    /**
     * 发布表单定义：解析当前 {@code schema_json} → 建物理表 → 登记 registry
     *
     * @param definitionId 表单定义ID
     * @return 物理表登记视图对象
     */
    FormTableRegistryVO publish(Long definitionId);

    /**
     * 发布表单定义（可显式确认列类型收窄）
     *
     * @param definitionId 表单定义ID
     * @param force 存在列类型收窄时是否强制发布（{@code false} 时抛参数错误）
     * @return 物理表登记视图对象
     */
    FormTableRegistryVO publish(Long definitionId, boolean force);

    /**
     * 查询表单定义已发布的物理表登记列表（按版本倒序）
     *
     * @param definitionId 表单定义ID
     * @return 登记视图对象列表
     */
    List<FormTableRegistryVO> listTables(Long definitionId);

    /**
     * 发布表定义：由列元数据建物理表并登记 registry（元数据模型）
     *
     * @param tableDefinitionId 表定义ID
     * @return 物理表登记视图对象
     */
    FormTableRegistryVO publishTableDefinition(Long tableDefinitionId);

    /**
     * 发布表定义（可显式确认列类型收窄）
     *
     * @param tableDefinitionId 表定义ID
     * @param force 存在列类型收窄时是否强制发布（{@code false} 时抛参数错误）
     * @return 物理表登记视图对象
     */
    FormTableRegistryVO publishTableDefinition(Long tableDefinitionId, boolean force);

    /**
     * 查询表定义已发布的物理表登记列表（按版本倒序）
     *
     * @param tableDefinitionId 表定义ID
     * @return 登记视图对象列表
     */
    List<FormTableRegistryVO> listTablesByTableDefinition(Long tableDefinitionId);
}
