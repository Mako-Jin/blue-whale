package com.bluewhale.lms.exam.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存作答命令（一次提交多题答案）。
 *
 * <p>前端可以整卷提交，也可以只提交改动过的题；服务端按题目逐条覆盖，
 * 未提交的题保持原答案。题目必须属于本次作答，否则整批失败——
 * 静默丢弃「不该出现的题」会掩盖前端错题号这类真实缺陷。</p>
 *
 * @author BlueWhale
 */
@Data
public class AttemptSaveCommand {

    /** 本次提交的答案 */
    @Valid
    @NotNull(message = "{validation.lms.attempt.answers-required}")
    private List<AttemptAnswerCommand> answers = new ArrayList<>();

    /** 前端统计的切屏次数（服务端只增不减） */
    @Min(value = 0, message = "{validation.lms.attempt.switch-count-min}")
    @Max(value = 999, message = "{validation.lms.attempt.switch-count-max}")
    private Integer switchCount;
}
