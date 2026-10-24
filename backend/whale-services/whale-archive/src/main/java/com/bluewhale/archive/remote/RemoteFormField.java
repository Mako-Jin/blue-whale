package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 表单字段定义（跨服务内网响应体）。
 *
 * <p>{@code attachment} 使用 {@code multiple/accept/maxCount}，{@code subtable} 使用
 * {@code children/minRows/maxRows}，与 whale-form 的 {@code FormField} 契约对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteFormField {

    /** 字段 key */
    private String key;

    /** 字段显示名 */
    private String label;

    /** 字段类型 */
    private String type;

    /** 是否必填 */
    private Boolean required;

    /** 附件是否多选 */
    private Boolean multiple;

    /** 附件允许类型 */
    private String accept;

    /** 附件最大数量 */
    private Integer maxCount;

    /** 子表字段定义 */
    private List<RemoteFormField> children;

    /** 子表最少行数 */
    private Integer minRows;

    /** 子表最多行数 */
    private Integer maxRows;

    /** 选项列表 */
    private List<String> options;
}
