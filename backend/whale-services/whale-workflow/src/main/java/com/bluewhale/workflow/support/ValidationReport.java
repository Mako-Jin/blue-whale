package com.bluewhale.workflow.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验报告：聚合错误与警告，{@link #isValid()} 仅由错误级决定。
 *
 * @author BlueWhale
 */
@Data
public class ValidationReport {

    /** 定义模式：legacy 旧格式 */
    public static final String MODE_LEGACY = "LEGACY";

    /** 定义模式：规范化 JSON */
    public static final String MODE_NORMALIZED = "NORMALIZED";

    /** 定义模式：BPMN XML */
    public static final String MODE_BPMN = "BPMN";

    /** 定义模式 */
    private String mode;

    /** 错误级问题 */
    private List<ValidationIssue> errors = new ArrayList<>();

    /** 警告级问题 */
    private List<ValidationIssue> warnings = new ArrayList<>();

    /**
     * 追加问题条目，按级别分别归入错误 / 警告。
     *
     * @param issue 问题条目
     */
    public void add(ValidationIssue issue) {
        if (issue.getLevel() == ValidationLevel.WARNING) {
            warnings.add(issue);
        } else {
            errors.add(issue);
        }
    }

    /**
     * 校验是否通过（无错误级问题）。
     *
     * @return 无错误返回 {@code true}
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
}
