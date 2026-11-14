package com.bluewhale.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义实体，对应表 {@code process_definition}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code process_definition} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_definition")
public class ProcessDefinition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程编码（业务唯一；由服务层前置校验 + active_code 生成列唯一键保证） */
    private String code;

    /** 流程名称 */
    private String name;

    /** 流程定义（JSON 文本，节点与流转，最长 64KB，即 65536 字符） */
    private String definitionJson;

    /** 版本号（默认 1，每次定义变更可递增） */
    private Integer version;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
