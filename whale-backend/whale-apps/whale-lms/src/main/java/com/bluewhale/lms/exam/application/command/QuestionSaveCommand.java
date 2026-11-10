package com.bluewhale.lms.exam.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目保存命令（新增 / 编辑）。
 *
 * <p>选项的 {@code correct} 标记不由前端提交，服务端按标准答案派生，
 * 避免「答案选 A、但 A 未标正确」这类两处不一致。</p>
 *
 * @author BlueWhale
 */
@Data
public class QuestionSaveCommand {

    /** 题目编码（新增必填） */
    @Size(max = 64, message = "题目编码最长 64 字符")
    private String questionCode;

    /** 分类 */
    @NotBlank(message = "题目分类不能为空")
    @Size(max = 64, message = "题目分类最长 64 字符")
    private String category;

    /** 题型：SINGLE / MULTIPLE / JUDGE / SHORT_ANSWER */
    @NotBlank(message = "题型不能为空")
    private String type;

    /** 难度：EASY / NORMAL / HARD */
    private String difficulty;

    /** 标签 */
    private List<String> tags = new ArrayList<>();

    /** 题干 */
    @NotBlank(message = "题干不能为空")
    @Size(max = 2000, message = "题干最长 2000 字符")
    private String stem;

    /** 标准答案 */
    @Size(max = 1000, message = "标准答案最长 1000 字符")
    private String standardAnswer;

    /** 答案解析 */
    @Size(max = 2000, message = "答案解析最长 2000 字符")
    private String analysis;

    /** 默认分值 */
    @DecimalMin(value = "0.1", message = "默认分值必须大于 0")
    private BigDecimal defaultScore;

    /** 选项（单选 / 多选必填） */
    @Valid
    private List<QuestionOptionCommand> options = new ArrayList<>();

    /** 是否保存后立即启用（默认仅保存为草稿） */
    private Boolean activate;
}
