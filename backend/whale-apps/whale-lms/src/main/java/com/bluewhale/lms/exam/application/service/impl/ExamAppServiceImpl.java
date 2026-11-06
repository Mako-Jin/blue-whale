package com.bluewhale.lms.exam.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.exam.application.command.ExamSaveCommand;
import com.bluewhale.lms.exam.application.service.ExamAppService;
import com.bluewhale.lms.exam.application.view.ExamView;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamQuery;
import com.bluewhale.lms.exam.domain.model.ExamStatus;
import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.repository.ExamRepository;
import com.bluewhale.lms.exam.domain.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 考试应用服务实现。
 *
 * <p>两条约束值得单独说明：</p>
 * <ol>
 *   <li><b>只引用已发布试卷</b>：考试指向的试卷必须处于 {@code PUBLISHED}。允许指向草稿，
 *       等于允许「开考了才发现卷子里一道题都没有」，这类错误必须在发布考试前拦住；</li>
 *   <li><b>及格分不得高于试卷总分</b>：{@code passScore > 试卷总分} 会让全员永久不及格，
 *       属于配置错误而非业务选择，直接拒绝；</li>
 *   <li><b>学员只看已发布</b>：列表接口对非管理、非讲师角色强制过滤为
 *       {@code PUBLISHED}，草稿考试的及格分与题量属于内部信息，不外泄。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ExamAppServiceImpl implements ExamAppService {

    /** 讲师角色编码（可见草稿考试，便于提前校对配置） */
    private static final String LECTURER_ROLE = "lms_lecturer";

    private final ExamRepository examRepository;

    private final PaperRepository paperRepository;

    private final LmsProperties properties;

    @Override
    public PageResult<ExamView> page(ExamQuery query, LmsActor actor) {
        assertGate();
        if (!actor.canManageAll() && !actor.hasRole(LECTURER_ROLE)) {
            query.setStatus(ExamStatus.PUBLISHED.name());
        }
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getExamPageLimit());
        long total = examRepository.count(query);
        List<ExamView> views = examRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(ExamView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public ExamView detail(Long id, LmsActor actor) {
        assertGate();
        Exam exam = requireExam(id);
        ExamView view = ExamView.from(exam);
        paperRepository.findById(exam.getPaperId()).ifPresent(paper -> {
            view.setPaperTitle(paper.getTitle());
            view.setPaperTotalScore(paper.getTotalScore());
        });
        return view;
    }

    @Override
    @Transactional
    public Long create(ExamSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        String code = Exam.normalizeCode(command.getExamCode());
        examRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_CODE_CONFLICT);
        });
        Exam exam = new Exam();
        exam.setExamCode(code);
        exam.setStatus(ExamStatus.DRAFT);
        applyCommand(exam, command);
        exam.validateBasics();
        return examRepository.save(exam);
    }

    @Override
    @Transactional
    public void update(Long id, ExamSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Exam exam = requireExam(id);
        exam.assertEditable();
        String code = Exam.normalizeCode(command.getExamCode());
        if (!code.equals(exam.getExamCode())) {
            examRepository.findByCode(code).ifPresent(existing -> {
                throw LmsErrorCode.exception(LmsErrorCode.EXAM_CODE_CONFLICT);
            });
        }
        exam.setExamCode(code);
        applyCommand(exam, command);
        exam.validateBasics();
        examRepository.save(exam);
    }

    @Override
    @Transactional
    public void delete(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Exam exam = requireExam(id);
        if (exam.getStatus() == null || !exam.getStatus().canDelete()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "仅草稿考试可删除");
        }
        examRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void publish(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Exam exam = requireExam(id);
        requirePublishedPaper(exam.getPaperId(), exam.getPassScore());
        exam.publish();
        examRepository.save(exam);
    }

    @Override
    @Transactional
    public void close(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        Exam exam = requireExam(id);
        exam.close();
        examRepository.save(exam);
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
     * 加载考试，不存在按 404 处理。
     *
     * @param id 考试 ID
     * @return 考试
     */
    private Exam requireExam(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.EXAM_NOT_FOUND));
    }

    /**
     * 校验试卷可用：必须存在、已发布、及格分不超过总分。
     *
     * @param paperId 试卷 ID
     * @param passScore 及格分
     * @return 试卷
     */
    private Paper requirePublishedPaper(Long paperId, BigDecimal passScore) {
        Paper paper = paperRepository.findById(paperId).orElseThrow(() -> LmsErrorCode.exception(
                LmsErrorCode.EXAM_INVALID, "考试引用的试卷不存在"));
        if (paper.getStatus() == null || !paper.getStatus().canUseForExam()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "考试只能引用已发布试卷");
        }
        BigDecimal total = Optional.ofNullable(paper.getTotalScore()).orElse(BigDecimal.ZERO);
        if (passScore != null && passScore.compareTo(total) > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID,
                    "及格分 " + passScore.stripTrailingZeros().toPlainString()
                            + " 不得高于试卷总分 " + total.stripTrailingZeros().toPlainString());
        }
        return paper;
    }

    /**
     * 将保存命令写入考试，并校验试卷可用性。
     *
     * @param exam 目标考试
     * @param command 保存命令
     */
    private void applyCommand(Exam exam, ExamSaveCommand command) {
        exam.setTitle(command.getTitle().trim());
        exam.setPaperId(command.getPaperId());
        exam.setCourseId(command.getCourseId());
        exam.setStartTime(command.getStartTime());
        exam.setEndTime(command.getEndTime());
        exam.setDurationMinutes(command.getDurationMinutes());
        exam.setMaxAttempts(command.getMaxAttempts());
        exam.setShuffle(Boolean.TRUE.equals(command.getShuffle()));
        exam.setPassScore(command.getPassScore() == null ? BigDecimal.ZERO : command.getPassScore());
        exam.setSwitchLimit(command.getSwitchLimit() == null ? 0 : command.getSwitchLimit());
        exam.setRemark(command.getRemark());
        requirePublishedPaper(exam.getPaperId(), exam.getPassScore());
    }
}
