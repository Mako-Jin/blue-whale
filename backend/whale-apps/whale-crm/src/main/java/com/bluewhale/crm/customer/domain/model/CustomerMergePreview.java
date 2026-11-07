package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户合并预览（M1-4）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerMergePreview {

    /** 源客户 ID */
    private Long sourceId;

    /** 源客户编码 */
    private String sourceCode;

    /** 源客户名称 */
    private String sourceName;

    /** 源客户状态 */
    private String sourceStatus;

    /** 目标客户 ID */
    private Long targetId;

    /** 目标客户编码 */
    private String targetCode;

    /** 目标客户名称 */
    private String targetName;

    /** 目标客户状态 */
    private String targetStatus;

    /** 各上下文引用数量，key 为上下文中文名 */
    private Map<String, Long> references = new LinkedHashMap<>();

    /** 冲突与提示信息 */
    private List<String> warnings = new ArrayList<>();
}
