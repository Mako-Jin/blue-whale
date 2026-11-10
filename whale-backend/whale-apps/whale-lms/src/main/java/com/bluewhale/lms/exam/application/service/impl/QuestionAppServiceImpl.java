package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.exam.application.command.QuestionOptionCommand;
import com.bluewhale.lms.exam.application.command.QuestionSaveCommand;
import com.bluewhale.lms.exam.application.command.QuestionStatusCommand;
import com.bluewhale.lms.exam.application.service.QuestionAppService;
import com.bluewhale.lms.exam.application.view.QuestionView;
import com.bluewhale.lms.exam.domain.model.Difficulty;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionOption;
import com.bluewhale.lms.exam.domain.model.QuestionQuery;
import com.bluewhale.lms.exam.domain.model.QuestionStatus;
import com.bluewhale.lms.exam.domain.model.QuestionType;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 题库应用服务实现。
 *
 * <p>题目状态口径：草稿只做基础校验（便于分步录入）；启用时强制完整性校验
 * （选项数量、答案归属、判断题答案取值），未启用的题目不会进入抽题池。
 * 删除前校验试卷引用，避免试卷明细指向已删题目导致考试当天取不到题。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class QuestionAppServiceImpl implements QuestionAppService {

    private final QuestionRepository questionRepository;

    private final PaperRepository paperRepository;

    private final LmsProperties properties;

    @Override
    public PageResult<QuestionView> page(QuestionQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getQuestionPageLimit());
        long total = questionRepository.count(query);
        List<QuestionView> views = questionRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(QuestionView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public QuestionView detail(Long id, LmsActor actor) {
        assertGate();
        return QuestionView.from(requireQuestion(id));
    }

    @Override
    @Transactional
    public Long create(QuestionSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        String code = Question.normalizeCode(command.getQuestionCode());
        questionRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_CODE_CONFLICT);
        });
        Question question = new Question();
        question.setQuestionCode(code);
        question.setStatus(QuestionStatus.DRAFT);
        applyCommand(question, command);
        if (Boolean.TRUE.equals(command.getActivate())) {
            question.setStatus(QuestionStatus.ACTIVE);
            question.validateForActivate();
        } else {
            question.validateBasics();
        }
        return questionRepository.save(question);
    }

    @Override
    @Transactional
    public void update(Long id, QuestionSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Question question = requireQuestion(id);
        question.assertEditable();
        String code = Question.normalizeCode(command.getQuestionCode());
        if (!code.equals(question.getQuestionCode())) {
            questionRepository.findByCode(code).ifPresent(existing -> {
                throw LmsErrorCode.exception(LmsErrorCode.QUESTION_CODE_CONFLICT);
            });
        }
        question.setQuestionCode(code);
        applyCommand(question, command);
        if (Boolean.TRUE.equals(command.getActivate())) {
            question.setStatus(QuestionStatus.ACTIVE);
            question.validateForActivate();
        } else {
            question.validateBasics();
        }
        questionRepository.save(question);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, QuestionStatusCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Question question = requireQuestion(id);
        String action = command.getAction() == null ? "" : command.getAction().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "ACTIVATE" -> {
                question.validateForActivate();
                question.setStatus(QuestionStatus.ACTIVE);
            }
            case "DISABLE" -> question.setStatus(QuestionStatus.DISABLED);
            default -> throw LmsErrorCode.exception(LmsErrorCode.QUESTION_STATE_CONFLICT,
                    "不支持的状态动作：" + command.getAction());
        }
        questionRepository.save(question);
    }

    @Override
    @Transactional
    public void delete(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Question question = requireQuestion(id);
        long referenced = paperRepository.countByQuestionId(question.getId());
        if (referenced > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_STATE_CONFLICT,
                    "该题已被 " + referenced + " 份试卷引用，请先从试卷移出后再删除");
        }
        questionRepository.deleteById(id);
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载题目，不存在按 404 处理。
     *
     * @param id 题目 ID
     * @return 题目
     */
    private Question requireQuestion(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.QUESTION_NOT_FOUND));
    }

    /**
     * 将保存命令写入题目（答案先归一，选项标记由启用校验派生）。
     *
     * @param question 目标题目
     * @param command 保存命令
     */
    private void applyCommand(Question question, QuestionSaveCommand command) {
        QuestionType type = QuestionType.parse(command.getType());
        question.setCategory(command.getCategory().trim());
        question.setType(type);
        question.setDifficulty(Difficulty.parse(command.getDifficulty()));
        question.setTags(command.getTags() == null ? new ArrayList<>() : new ArrayList<>(command.getTags()));
        question.setStem(command.getStem().trim());
        question.setStandardAnswer(Question.normalizeAnswer(type, command.getStandardAnswer()));
        question.setAnalysis(command.getAnalysis());
        question.setDefaultScore(command.getDefaultScore() == null ? BigDecimal.ONE : command.getDefaultScore());
        question.setOptions(toOptions(command.getOptions()));
    }

    /**
     * 选项命令转领域（分值 / 正确标记由后续校验派生）。
     *
     * @param commands 选项命令列表
     * @return 选项列表
     */
    private List<QuestionOption> toOptions(List<QuestionOptionCommand> commands) {
        List<QuestionOption> options = new ArrayList<>();
        if (commands == null) {
            return options;
        }
        int sortNo = 1;
        for (QuestionOptionCommand item : commands) {
            QuestionOption option = new QuestionOption();
            option.setOptionKey(item.getOptionKey());
            option.setContent(item.getContent());
            option.setSortNo(item.getSortNo() == null ? sortNo : item.getSortNo());
            options.add(option);
            sortNo++;
        }
        return options;
    }
}
