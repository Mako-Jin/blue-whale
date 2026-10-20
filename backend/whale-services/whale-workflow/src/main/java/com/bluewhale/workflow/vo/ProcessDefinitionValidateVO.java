package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义校验视图对象。
 *
 * <p>内容非法时 {@code valid=false} 且不抛异常，问题进 {@code errors[]}；合法时
 * {@code normalizedJson} 携带归一化后的规范化 JSON（{@code {type,nodes[],flows[]}}）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessDefinitionValidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否通过（无错误级问题） */
    private boolean valid;

    /** 定义模式：LEGACY / NORMALIZED / BPMN */
    private String mode;

    /** 错误级问题 */
    private List<ValidationIssueVO> errors = new ArrayList<>();

    /** 警告级问题 */
    private List<ValidationIssueVO> warnings = new ArrayList<>();

    /** 归一化后的规范化 JSON（仅合法时非空） */
    private String normalizedJson;

    /** 节点数（仅合法时非 0） */
    private int nodeCount;

    /** 流转数（仅合法时非 0） */
    private int flowCount;
}
