package com.bluewhale.lms.exam.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则抽题命令。
 *
 * <p>{@code seed} 不传时用试卷编码派生，保证「同一份试卷重复生成结果一致」；
 * 显式传新 seed 才会重新洗牌。</p>
 *
 * @author BlueWhale
 */
@Data
public class PaperGenerateCommand {

    /** 抽题种子（为空则用试卷编码） */
    @Size(max = 64, message = "{validation.lms.paper-generate.seed-size}")
    private String seed;

    /** 抽题规则（多条规则按顺序执行，跨规则不重复取同一题） */
    @Valid
    @NotEmpty(message = "{validation.lms.paper-generate.rules-required}")
    private List<PaperRuleCommand> rules = new ArrayList<>();
}
