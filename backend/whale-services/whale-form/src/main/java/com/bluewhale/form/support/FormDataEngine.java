package com.bluewhale.form.support;

import com.bluewhale.form.entity.FormData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单数据动态数据引擎：以物理表为落点进行结构化字段的建表、写入、读取与逻辑删除。
 *
 * <p>实现须保证：表名二次白名单校验、列名取自 schema 白名单、所有值经参数化绑定，
 * 禁止任何用户输入直接拼接进 SQL（REQ-SEC 注入防护）。</p>
 *
 * @author BlueWhale
 */
public interface FormDataEngine {

    /**
     * 执行建表语句（仅发布路径调用，DDL 由服务端依据白名单 schema 生成）
     *
     * @param ddl 建表 DDL 文本
     */
    void executeDdl(String ddl);

    /**
     * 描述物理表现有列，用于发布时计算结构差异（ADD / MODIFY / DROP）
     *
     * @param table 物理表名
     * @return 列名 → 归一化 MySQL 列类型（小写、无空格）；表不存在时返回空映射
     */
    Map<String, String> describeColumns(String table);

    /**
     * 查询物理表现有索引名，用于发布时对齐字段级自动索引
     *
     * @param table 物理表名
     * @return 现有索引名集合（含主键索引名 {@code PRIMARY}）；表不存在时返回空集合
     */
    Set<String> describeIndexes(String table);

    /**
     * 向物理表插入一条数据（主键、租户与公共列取自 {@code record}，动态列取自 {@code fields}+{@code data}）
     *
     * @param table 物理表名
     * @param record 主记录（提供 id / tenantId / definitionId / definitionVersion / bizKey / submitter / status）
     * @param fields 动态字段列表
     * @param data 字段值映射（key 为字段 key）
     */
    void insert(String table, FormData record, List<FormField> fields, Map<String, Object> data);

    /**
     * 更新物理表中一条数据的动态列（按租户与逻辑删除过滤）
     *
     * @param table 物理表名
     * @param record 主记录（提供 id / tenantId）
     * @param fields 动态字段列表（列名白名单）
     * @param data 字段值映射（key 为字段 key；缺失字段写入 {@code NULL}）
     */
    void update(String table, FormData record, List<FormField> fields, Map<String, Object> data);

    /**
     * 读取物理表中指定记录的结构化字段值（按租户与逻辑删除过滤）
     *
     * @param table 物理表名
     * @param id 记录主键
     * @param tenantId 租户ID
     * @param fields 动态字段列表
     * @return 字段值映射（key 为字段 key）；无匹配行时返回空映射
     */
    Map<String, Object> selectFields(String table, Long id, Long tenantId, List<FormField> fields);

    /**
     * 逻辑删除物理表中的一条数据（按租户过滤）
     *
     * @param table 物理表名
     * @param id 记录主键
     * @param tenantId 租户ID
     */
    void logicDelete(String table, Long id, Long tenantId);
}
