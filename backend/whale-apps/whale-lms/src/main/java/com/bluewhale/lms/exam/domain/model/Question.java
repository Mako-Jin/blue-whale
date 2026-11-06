package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库题目聚合根（题目 + 选项）。
 *
 * <p>三条不变量：</p>
 * <ol>
 *   <li><b>答案归一</b>：单选 / 判断题大写去空白，多选题按选项键升序去重后以逗号拼接，
 *       判分时直接字符串比较即可，不依赖作答顺序；</li>
 *   <li><b>答案与选项一致</b>：单选 / 多选的每个答案键必须在选项集合内，选项的
 *       {@code correct} 标记由答案派生，避免两处写法不一致；</li>
 *   <li><b>启用前必须完整</b>：草稿允许缺答案便于分步录入，启用时强制校验，
 *       未启用的题目不会进入抽题池。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Data
public class Question {

    /** 编码上限 */
    private static final int CODE_MAX_LENGTH = 64;

    /** 分类上限 */
    private static final int CATEGORY_MAX_LENGTH = 64;

    /** 题干上限 */
    private static final int STEM_MAX_LENGTH = 2000;

    /** 解析上限 */
    private static final int ANALYSIS_MAX_LENGTH = 2000;

    /** 答案上限 */
    private static final int ANSWER_MAX_LENGTH = 1000;

    /** 选项数量下限 */
    private static final int OPTION_MIN_COUNT = 2;

    /** 选项内容上限 */
    private static final int OPTION_CONTENT_MAX_LENGTH = 500;

    /** 多选题答案分隔符 */
    private static final String ANSWER_SEPARATOR_REGEX = "[,，;；\\s]+";

    /** 主键 */
    private Long id;

    /** 题目编码（租户内唯一） */
    private String questionCode;

    /** 分类 */
    private String category;

    /** 题型 */
    private QuestionType type;

    /** 难度 */
    private Difficulty difficulty;

    /** 标签 */
    private List<String> tags = new ArrayList<>();

    /** 题干 */
    private String stem;

    /** 标准答案（归一后） */
    private String standardAnswer;

    /** 答案解析 */
    private String analysis;

    /** 默认分值 */
    private BigDecimal defaultScore;

    /** 状态 */
    private QuestionStatus status;

    /** 选项（单选 / 多选） */
    private List<QuestionOption> options = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 启用前完整性校验，并归一答案、派生选项正确标记。
     */
    public void validateForActivate() {
        validateBasics();
        if (type == null) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "题型不能为空");
        }
        if (type.requiresOptions()) {
            validateOptions();
        } else if (options != null && !options.isEmpty()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "判断题与简答题不允许配置选项");
        }
        standardAnswer = normalizeAnswer(type, standardAnswer);
        validateAnswerPresence();
        if (type.requiresOptions()) {
            validateAnswerKeys();
        }
        syncOptionFlags();
    }

    /**
     * 断言可编辑。
     */
    public void assertEditable() {
        if (status != null && !status.canEdit()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_STATE_CONFLICT, "已停用题目不可编辑");
        }
    }

    /**
     * 规范化题目编码：去空白 + 大写。
     *
     * @param raw 原始编码
     * @return 规范化编码
     */
    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "题目编码不能为空");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.length() > CODE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "题目编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        return value;
    }

    /**
     * 规范化标准答案。
     *
     * @param type 题型
     * @param raw 原始答案
     * @return 归一后的答案（空值返回 null）
     */
    public static String normalizeAnswer(QuestionType type, String raw) {
        if (type == null || raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (type == QuestionType.JUDGE) {
            return normalizeJudge(value);
        }
        if (type.isMultiple()) {
            return normalizeMultiple(value);
        }
        if (type == QuestionType.SHORT_ANSWER) {
            return value;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    /**
     * 基础字段校验（草稿保存时调用；启用时会连同答案与选项一起校验）。
     */
    public void validateBasics() {
        if (stem == null || stem.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "题干不能为空");
        }
        if (stem.trim().length() > STEM_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "题干不得超过 " + STEM_MAX_LENGTH + " 字符");
        }
        if (category == null || category.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "题目分类不能为空");
        }
        if (category.trim().length() > CATEGORY_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "题目分类不得超过 " + CATEGORY_MAX_LENGTH + " 字符");
        }
        if (analysis != null && analysis.trim().length() > ANALYSIS_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "答案解析不得超过 " + ANALYSIS_MAX_LENGTH + " 字符");
        }
        if (defaultScore == null || defaultScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "题目默认分值必须大于 0");
        }
    }

    /**
     * 选项校验：数量、键唯一、内容非空。
     */
    private void validateOptions() {
        if (options == null || options.size() < OPTION_MIN_COUNT) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "选择题至少需要 " + OPTION_MIN_COUNT + " 个选项");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (QuestionOption option : options) {
            if (option.getOptionKey() == null || option.getOptionKey().isBlank()) {
                throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "选项键不能为空");
            }
            if (option.getContent() == null || option.getContent().isBlank()) {
                throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                        "选项 " + option.getOptionKey() + " 的内容不能为空");
            }
            if (option.getContent().trim().length() > OPTION_CONTENT_MAX_LENGTH) {
                throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                        "选项内容不得超过 " + OPTION_CONTENT_MAX_LENGTH + " 字符");
            }
            String key = option.getOptionKey().trim().toUpperCase(Locale.ROOT);
            if (!keys.add(key)) {
                throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "选项键重复：" + key);
            }
            option.setOptionKey(key);
        }
    }

    /**
     * 校验答案是否存在（简答题可空）。
     */
    private void validateAnswerPresence() {
        if (type == QuestionType.SHORT_ANSWER) {
            return;
        }
        if (standardAnswer == null || standardAnswer.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "启用题目必须填写标准答案");
        }
        if (standardAnswer.length() > ANSWER_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                    "标准答案不得超过 " + ANSWER_MAX_LENGTH + " 字符");
        }
        if (type == QuestionType.JUDGE && !"TRUE".equals(standardAnswer) && !"FALSE".equals(standardAnswer)) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "判断题答案只能是 TRUE 或 FALSE");
        }
    }

    /**
     * 校验答案键均在选项内（单选只允许一个键）。
     */
    private void validateAnswerKeys() {
        Set<String> keys = options.stream()
                .map(QuestionOption::getOptionKey)
                .collect(Collectors.toSet());
        List<String> answers = Arrays.asList(standardAnswer.split(","));
        if (type == QuestionType.SINGLE && answers.size() != 1) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "单选题答案只能有一个选项");
        }
        for (String answer : answers) {
            if (!keys.contains(answer)) {
                throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID,
                        "标准答案 " + answer + " 不在选项内");
            }
        }
    }

    /**
     * 按标准答案派生选项正确标记。
     */
    private void syncOptionFlags() {
        if (type == null || !type.requiresOptions() || standardAnswer == null) {
            return;
        }
        Set<String> answers = new LinkedHashSet<>(Arrays.asList(standardAnswer.split(",")));
        for (QuestionOption option : options) {
            option.setCorrect(answers.contains(option.getOptionKey()));
        }
    }

    /**
     * 判断题答案归一。
     *
     * @param value 原始值
     * @return TRUE / FALSE
     */
    private static String normalizeJudge(String value) {
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if ("TRUE".equals(upper) || "T".equals(upper) || "1".equals(upper)
                || "对".equals(value.trim()) || "正确".equals(value.trim())) {
            return "TRUE";
        }
        if ("FALSE".equals(upper) || "F".equals(upper) || "0".equals(upper)
                || "错".equals(value.trim()) || "错误".equals(value.trim())) {
            return "FALSE";
        }
        throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "判断题答案只能是 TRUE 或 FALSE");
    }

    /**
     * 多选题答案归一：去重 + 升序。
     *
     * @param value 原始值
     * @return 逗号分隔的升序答案键
     */
    private static String normalizeMultiple(String value) {
        String[] parts = value.split(ANSWER_SEPARATOR_REGEX);
        Set<String> keys = new LinkedHashSet<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                keys.add(part.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (keys.isEmpty()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_INVALID, "多选题答案不能为空");
        }
        return keys.stream().sorted().collect(Collectors.joining(","));
    }
}
