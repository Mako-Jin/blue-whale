package com.bluewhale.form.service;

import com.bluewhale.form.model.vo.FormTableRegistryVO;

/**
 * 表单物理表归档服务（冷热分离的运维标记）。
 *
 * <p><b>边界</b>：本服务只把 {@code form_table_registry} 的旧版本物理表标记为「已归档」，
 * 不执行数据导出、不删除物理表、不改变读写路由。真正的归档动作（导出 Parquet/CSV 到对象存储、
 * 下线物理表）由运维在标记确认后按数据库手册执行，避免应用层直接删表带来不可逆风险。</p>
 *
 * @author BlueWhale
 */
public interface FormArchiveService {

    /**
     * 标记某物理表登记为已归档（幂等；当前生效版本不允许归档）
     *
     * @param tableDefinitionId 表定义ID（旧模型传 {@code null}）
     * @param registryId 物理表登记ID
     * @return 登记视图对象（含归档状态）
     */
    FormTableRegistryVO archive(Long tableDefinitionId, Long registryId);

    /**
     * 取消归档标记（幂等）
     *
     * @param tableDefinitionId 表定义ID（旧模型传 {@code null}）
     * @param registryId 物理表登记ID
     * @return 登记视图对象（含归档状态）
     */
    FormTableRegistryVO unarchive(Long tableDefinitionId, Long registryId);
}
