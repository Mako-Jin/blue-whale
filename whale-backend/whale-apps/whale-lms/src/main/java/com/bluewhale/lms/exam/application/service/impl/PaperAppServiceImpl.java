package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.exam.application.command.PaperGenerateCommand;
import com.bluewhale.lms.exam.application.command.PaperQuestionAddCommand;
import com.bluewhale.lms.exam.application.command.PaperRuleCommand;
import com.bluewhale.lms.exam.application.command.PaperSaveCommand;
import com.bluewhale.lms.exam.application.service.PaperAppService;
import com.bluewhale.lms.exam.application.view.PaperDetailView;
import com.bluewhale.lms.exam.application.view.PaperGenerateResultView;
import com.bluewhale.lms.exam.application.view.PaperQuestionView;
import com.bluewhale.lms.exam.application.view.PaperView;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuery;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;
import com.bluewhale.lms.exam.domain.model.PaperStatus;
import com.bluewhale.lms.exam.domain.model.PickOrder;
import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import com.bluewhale.lms.exam.domain.repository.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试卷应用服务实现。
 *
 * <p>抽题可复现是本类的核心约束：排序键由 {@link PickOrder#keyOf} 从
 * {@code seed + questionId} 纯哈希派生，不使用 {@code Random}，因此同一份试卷
 * 用同一 seed 重复生成得到同一套题；显式换 seed 才会得到新组合。候选不足时
 * 直接失败而不用其它题补齐——「悄悄少抽两道」到了考试当天才发现，代价更大。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PaperAppServiceImpl implements PaperAppService {

    /** 手工加题来源 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 规则抽题来源 */
    private static final String SOURCE_RULE = "RULE";

    private final PaperRepository paperRepository;

    private final QuestionRepository questionRepository;

    private final LmsProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<PaperView> page(PaperQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getPaperPageLimit());
        long total = paperRepository.count(query);
        List<PaperView> views = paperRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(PaperView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public PaperDetailView detail(Long id, LmsActor actor) {
        assertGate();
        Paper paper = requirePaper(id);
        PaperDetailView view = new PaperDetailView();
        view.setId(paper.getId());
        view.setPaperCode(paper.getPaperCode());
        view.setTitle(paper.getTitle());
        view.setCourseId(paper.getCourseId());
        view.setDurationMinutes(paper.getDurationMinutes());
        view.setTotalScore(paper.getTotalScore());
        view.setPassScore(paper.getPassScore());
        view.setQuestionCount(paper.getQuestionCount());
        view.setSeed(paper.getSeed());
        view.setStatus(paper.getStatus() == null ? null : paper.getStatus().name());
        view.setRemark(paper.getRemark());
        view.setCreateTime(paper.getCreateTime());
        view.setRuleConfig(paper.getRuleConfig());
        view.setQuestions(buildQuestionViews(paper.getId()));
        return view;
    }

    @Override
    @Transactional
    public Long create(PaperSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        String code = Paper.normalizeCode(command.getPaperCode());
        paperRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_CODE_CONFLICT);
        });
        Paper paper = new Paper();
        paper.setPaperCode(code);
        paper.setStatus(PaperStatus.DRAFT);
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setQuestionCount(0);
        applyCommand(paper, command);
        paper.validateBasics();
        return paperRepository.save(paper);
    }

    @Override
    @Transactional
    public void update(Long id, PaperSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(id);
        paper.assertEditable();
        String code = Paper.normalizeCode(command.getPaperCode());
        if (!code.equals(paper.getPaperCode())) {
            paperRepository.findByCode(code).ifPresent(existing -> {
                throw LmsErrorCode.exception(LmsErrorCode.PAPER_CODE_CONFLICT);
            });
        }
        paper.setPaperCode(code);
        applyCommand(paper, command);
        paper.validateBasics();
        paperRepository.save(paper);
    }

    @Override
    @Transactional
    public void delete(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(id);
        paper.assertEditable();
        paperRepository.deleteAllQuestions(id);
        paperRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void addQuestion(Long paperId, PaperQuestionAddCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(paperId);
        paper.assertEditable();
        Question question = questionRepository.findById(command.getQuestionId())
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.QUESTION_NOT_FOUND));
        if (question.getStatus() == null || !question.getStatus().canPick()) {
            throw LmsErrorCode.exception(LmsErrorCode.QUESTION_STATE_CONFLICT, "仅启用题目可加入试卷");
        }
        BigDecimal score = command.getScore() == null ? question.getDefaultScore() : command.getScore();
        List<PaperQuestion> existing = paperRepository.listQuestions(paperId);
        Optional<PaperQuestion> exists = existing.stream()
                .filter(item -> item.getQuestionId().equals(question.getId()))
                .findFirst();
        if (exists.isPresent()) {
            PaperQuestion paperQuestion = exists.get();
            paperQuestion.setScore(score);
            paperRepository.saveQuestion(paperQuestion);
        } else {
            if (existing.size() + 1 > properties.getPaperQuestionLimit()) {
                throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                        "单份试卷题目数不得超过 " + properties.getPaperQuestionLimit());
            }
            PaperQuestion paperQuestion = new PaperQuestion();
            paperQuestion.setPaperId(paperId);
            paperQuestion.setQuestionId(question.getId());
            paperQuestion.setScore(score);
            paperQuestion.setSortNo(existing.size() + 1);
            paperQuestion.setSource(SOURCE_MANUAL);
            paperRepository.saveQuestion(paperQuestion);
        }
        recalculate(paper);
    }

    @Override
    @Transactional
    public void removeQuestion(Long paperId, Long questionId, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(paperId);
        paper.assertEditable();
        paperRepository.deleteQuestion(paperId, questionId);
        renumber(paperId);
        recalculate(paper);
    }

    @Override
    @Transactional
    public PaperGenerateResultView generate(Long paperId, PaperGenerateCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(paperId);
        paper.assertEditable();
        String seed = StringUtils.hasText(command.getSeed())
                ? command.getSeed().trim() : paper.getPaperCode();
        List<PaperQuestion> picked = pickByRules(paper, command, seed);
        paperRepository.deleteQuestionsBySource(paperId, SOURCE_RULE);
        for (PaperQuestion item : picked) {
            paperRepository.saveQuestion(item);
        }
        paper.setSeed(seed);
        paper.setRuleConfig(writeRules(command.getRules()));
        List<PaperQuestion> all = paperRepository.listQuestions(paperId);
        paper.applyTotals(sumScore(all), all.size());
        paperRepository.save(paper);
        PaperGenerateResultView view = new PaperGenerateResultView();
        view.setPicked(picked.size());
        view.setQuestionCount(all.size());
        view.setTotalScore(paper.getTotalScore());
        view.setSeed(seed);
        return view;
    }

    @Override
    @Transactional
    public void publish(Long paperId, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(paperId);
        List<PaperQuestion> all = paperRepository.listQuestions(paperId);
        paper.publish(sumScore(all), all.size());
        paperRepository.save(paper);
    }

    @Override
    @Transactional
    public void archive(Long paperId, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Paper paper = requirePaper(paperId);
        if (paper.getStatus() == null || !paper.getStatus().canUseForExam()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT, "仅已发布试卷可归档");
        }
        paper.setStatus(PaperStatus.ARCHIVED);
        paperRepository.save(paper);
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
     * 加载试卷，不存在按 404 处理。
     *
     * @param id 试卷 ID
     * @return 试卷
     */
    private Paper requirePaper(Long id) {
        return paperRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.PAPER_NOT_FOUND));
    }

    /**
     * 将保存命令写入试卷。
     *
     * @param paper 目标试卷
     * @param command 保存命令
     */
    private void applyCommand(Paper paper, PaperSaveCommand command) {
        paper.setTitle(command.getTitle().trim());
        paper.setCourseId(command.getCourseId());
        paper.setDurationMinutes(command.getDurationMinutes());
        paper.setPassScore(command.getPassScore() == null ? BigDecimal.ZERO : command.getPassScore());
        paper.setRemark(command.getRemark());
    }

    /**
     * 按规则抽题：跨规则不重复，候选不足直接失败。
     *
     * @param paper 试卷
     * @param command 抽题命令
     * @param seed 实际使用的种子
     * @return 待落库的试卷题目
     */
    private List<PaperQuestion> pickByRules(Paper paper, PaperGenerateCommand command, String seed) {
        List<PaperQuestion> existing = paperRepository.listQuestions(paper.getId());
        Set<Long> used = existing.stream()
                .map(PaperQuestion::getQuestionId)
                .collect(Collectors.toCollection(HashSet::new));
        List<PaperQuestion> picked = new ArrayList<>();
        int sortNo = existing.size() + 1;
        for (PaperRuleCommand rule : command.getRules()) {
            List<Question> candidates = questionRepository
                    .listPickCandidates(rule.getCategory(), rule.getType(), rule.getDifficulty())
                    .stream().filter(item -> !used.contains(item.getId())).toList();
            if (candidates.size() < rule.getCount()) {
                throw LmsErrorCode.exception(LmsErrorCode.PAPER_RULE_INSUFFICIENT,
                        "规则（" + describeRule(rule) + "）需要 " + rule.getCount()
                                + " 道，可用候选仅 " + candidates.size() + " 道");
            }
            List<Question> ordered = new ArrayList<>(candidates);
            ordered.sort(Comparator
                    .comparingLong((Question item) -> PickOrder.keyOf(seed, item.getId()))
                    .thenComparing(Question::getId));
            for (int index = 0; index < rule.getCount(); index++) {
                Question question = ordered.get(index);
                used.add(question.getId());
                picked.add(buildPaperQuestion(paper.getId(), question, rule.getScore(), sortNo));
                sortNo++;
            }
        }
        if (existing.size() + picked.size() > properties.getPaperQuestionLimit()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                    "单份试卷题目数不得超过 " + properties.getPaperQuestionLimit());
        }
        return picked;
    }

    /**
     * 组装试卷题目。
     *
     * @param paperId 试卷 ID
     * @param question 题目
     * @param score 指定分值（为空取题目默认分值）
     * @param sortNo 题序
     * @return 试卷题目
     */
    private PaperQuestion buildPaperQuestion(Long paperId, Question question, BigDecimal score, int sortNo) {
        PaperQuestion item = new PaperQuestion();
        item.setPaperId(paperId);
        item.setQuestionId(question.getId());
        item.setScore(score == null ? question.getDefaultScore() : score);
        item.setSortNo(sortNo);
        item.setSource(SOURCE_RULE);
        return item;
    }

    /**
     * 规则描述（用于不足时的报错文案）。
     *
     * @param rule 规则
     * @return 描述
     */
    private String describeRule(PaperRuleCommand rule) {
        return "分类=" + (rule.getCategory() == null ? "不限" : rule.getCategory())
                + ", 题型=" + (rule.getType() == null ? "不限" : rule.getType())
                + ", 难度=" + (rule.getDifficulty() == null ? "不限" : rule.getDifficulty());
    }

    /**
     * 序列化抽题规则快照。
     *
     * @param rules 规则列表
     * @return JSON 字符串
     */
    private String writeRules(List<PaperRuleCommand> rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException ex) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_RULE_INVALID, "抽题规则序列化失败");
        }
    }

    /**
     * 重排题序（移出题目后补空位）。
     *
     * @param paperId 试卷 ID
     */
    private void renumber(Long paperId) {
        List<PaperQuestion> all = paperRepository.listQuestions(paperId);
        int sortNo = 1;
        for (PaperQuestion item : all) {
            if (item.getSortNo() == null || item.getSortNo() != sortNo) {
                item.setSortNo(sortNo);
                paperRepository.saveQuestion(item);
            }
            sortNo++;
        }
    }

    /**
     * 重算试卷总分与题目数并回写。
     *
     * @param paper 试卷
     */
    private void recalculate(Paper paper) {
        List<PaperQuestion> all = paperRepository.listQuestions(paper.getId());
        paper.applyTotals(sumScore(all), all.size());
        paperRepository.save(paper);
    }

    /**
     * 汇总题目分值。
     *
     * @param items 试卷题目
     * @return 总分
     */
    private BigDecimal sumScore(List<PaperQuestion> items) {
        return items.stream()
                .map(item -> item.getScore() == null ? BigDecimal.ZERO : item.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 组装试卷题目视图（含题干，便于审卷）。
     *
     * @param paperId 试卷 ID
     * @return 题目视图列表
     */
    private List<PaperQuestionView> buildQuestionViews(Long paperId) {
        List<PaperQuestion> items = paperRepository.listQuestions(paperId);
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> questions = items.stream()
                .map(PaperQuestion::getQuestionId)
                .distinct()
                .map(questionRepository::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<PaperQuestionView> views = new ArrayList<>();
        for (PaperQuestion item : items) {
            Question question = questions.get(item.getQuestionId());
            PaperQuestionView view = new PaperQuestionView();
            view.setQuestionId(item.getQuestionId());
            view.setScore(item.getScore());
            view.setSortNo(item.getSortNo());
            view.setSource(item.getSource());
            if (question != null) {
                view.setQuestionCode(question.getQuestionCode());
                view.setStem(question.getStem());
                view.setType(question.getType() == null ? null : question.getType().name());
            }
            views.add(view);
        }
        return views;
    }
}
