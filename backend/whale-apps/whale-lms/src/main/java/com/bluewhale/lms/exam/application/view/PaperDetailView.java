package com.bluewhale.lms.exam.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 试卷详情视图（含题目清单）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperDetailView extends PaperView {

    /** 抽题规则 JSON 快照 */
    private String ruleConfig;

    /** 题目清单 */
    private List<PaperQuestionView> questions;
}
