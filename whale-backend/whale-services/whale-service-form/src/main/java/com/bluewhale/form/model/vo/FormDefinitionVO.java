package com.bluewhale.form.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 表单定义视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表单编码 */
    private String code;

    /** 表单名称 */
    private String name;

    /** 所属表定义ID（元数据模型；{@code null} 为旧模型） */
    private Long tableDefinitionId;

    /** 表单结构定义（发布快照 JSON 文本；分页摘要不返回，为空时不序列化） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String schemaJson;

    /** 版本号 */
    private Integer version;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 表单分类 */
    private String category;

    /** 标签（JSON 字符串数组文本，如 {@code ["报销","财务"]}） */
    private String tags;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 字段配置集合（含选项；详情接口返回） */
    private List<FormFieldConfigVO> fields;
}
