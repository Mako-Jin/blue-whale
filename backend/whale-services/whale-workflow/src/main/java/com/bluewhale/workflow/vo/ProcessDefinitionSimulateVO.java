package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义选路模拟结果。
 *
 * <p>定义结构非法时 {@code valid=false} 且 {@code steps} 为空；结构合法但运行期
 * 无法选路（如条件均未命中且无默认流）时 {@code valid=true} 并携带
 * {@code errorCode}/{@code errorMessage}，已走过的路径仍在 {@code steps} 中。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessDefinitionSimulateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 定义结构是否合法 */
    private boolean valid;

    /** 定义模式：LEGACY / NORMALIZED */
    private String mode;

    /** 结构错误 */
    private List<ValidationIssueVO> errors = new ArrayList<>();

    /** 模拟路径（按实际走位顺序） */
    private List<SimulateStepVO> steps = new ArrayList<>();

    /** 模拟是否结束（到达结束事件或无可走流转） */
    private boolean terminated;

    /** 是否到达结束事件 */
    private boolean reachedEnd;

    /** 终止节点键（结束事件键或停留节点键，可空） */
    private String terminalNodeKey;

    /** 运行期选路错误码（ValidationCode 名称，可空） */
    private String errorCode;

    /** 运行期选路错误文案（可空） */
    private String errorMessage;
}
