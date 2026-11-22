package com.bluewhale.lms.exam.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 提交判分命令。
 *
 * <p>一次只判主观题：客观题在交卷时已自动判完，允许人工重判会破坏
 * 「同一份答案两套口径」。确有客观题争议时走重判流程，由新版本成绩留痕，
 * 而不是把客观题混进这张表单。</p>
 *
 * @author BlueWhale
 */
@Data
public class GradeCommand {

    /** 判分明细 */
    @Valid
    @NotEmpty(message = "{validation.lms.grade.items-required}")
    private List<GradeItemCommand> items = new ArrayList<>();

    /** 判分备注（重判时写原因） */
    @Size(max = 500, message = "{validation.lms.grade.remark-size}")
    private String remark;
}
