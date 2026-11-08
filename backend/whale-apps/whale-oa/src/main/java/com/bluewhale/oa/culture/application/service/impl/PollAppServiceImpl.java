package com.bluewhale.oa.culture.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.PollAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import com.bluewhale.oa.culture.domain.model.Poll;
import com.bluewhale.oa.culture.domain.model.PollOption;
import com.bluewhale.oa.culture.domain.model.PollStatus;
import com.bluewhale.oa.culture.domain.model.PollVote;
import com.bluewhale.oa.culture.domain.repository.CultureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投票问卷应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PollAppServiceImpl implements PollAppService {

    private final CultureRepository repository;

    @Override
    public PageResult<CultureViews.Poll> page(CultureQuery query) {
        PageResult<Poll> source = repository.pagePolls(query);
        List<CultureViews.Poll> records = source.getRecords().stream()
                .map(PollAppServiceImpl::toView).toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public CultureViews.PollDetail detail(Long id, Long userId) {
        Poll poll = requirePoll(id);
        List<PollOption> options = repository.listOptions(id);
        List<PollVote> votes = repository.listVotes(id);
        Map<Long, Long> counts = new LinkedHashMap<>();
        Set<Long> myOptions = new HashSet<>();
        for (PollVote vote : votes) {
            counts.merge(vote.getOptionId(), 1L, Long::sum);
            if (userId != null && userId.equals(vote.getUserId())) {
                myOptions.add(vote.getOptionId());
            }
        }
        CultureViews.PollDetail detail = new CultureViews.PollDetail();
        fillView(detail, poll);
        detail.setOptions(options.stream()
                .map(option -> toOptionView(option, counts.getOrDefault(option.getId(), 0L),
                        myOptions.contains(option.getId()))).toList());
        detail.setMyVoted(!myOptions.isEmpty());
        detail.setTotalVotes((long) votes.size());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Poll create(CultureCommands.PollSave command, Long creatorId, String creatorName) {
        Poll poll = new Poll();
        fill(poll, command);
        poll.setStatus(PollStatus.DRAFT.name());
        poll.setCreatorId(creatorId);
        poll.setCreatorName(creatorName);
        repository.savePoll(poll);
        List<PollOption> options = buildOptions(command.getOptions());
        if (!options.isEmpty()) {
            repository.replaceOptions(poll.getId(), options);
        }
        return toView(poll);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Poll update(Long id, CultureCommands.PollSave command) {
        Poll poll = requirePoll(id);
        poll.assertEditable();
        fill(poll, command);
        repository.updatePoll(poll);
        repository.replaceOptions(id, buildOptions(command.getOptions()));
        return toView(poll);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Poll poll = requirePoll(id);
        poll.assertEditable();
        repository.deletePoll(id);
        repository.deleteOptionsByPoll(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Poll publish(Long id) {
        Poll poll = requirePoll(id);
        if (PollStatus.of(poll.getStatus()) == PollStatus.PUBLISHED) {
            return toView(poll);
        }
        poll.publish(repository.listOptions(id).size());
        repository.updatePoll(poll);
        return toView(poll);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Poll close(Long id) {
        Poll poll = requirePoll(id);
        poll.close();
        repository.updatePoll(poll);
        return toView(poll);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vote(Long id, CultureCommands.VoteCommand command, Long userId, String userName) {
        Poll poll = requirePoll(id);
        poll.assertVotable(LocalDateTime.now());
        List<Long> optionIds = command.getOptionIds();
        if (optionIds == null || optionIds.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.POLL_OPTION_REQUIRED);
        }
        Set<Long> selected = new HashSet<>(optionIds);
        if (selected.size() != optionIds.size()) {
            throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "投票选项不能重复");
        }
        if (repository.existsAnyVote(id, userId)) {
            throw OaErrorCode.exception(OaErrorCode.POLL_ALREADY_VOTED);
        }
        Set<Long> allowed = new HashSet<>();
        repository.listOptions(id).forEach(option -> allowed.add(option.getId()));
        if (!allowed.containsAll(selected)) {
            throw OaErrorCode.exception(OaErrorCode.POLL_OPTION_REQUIRED);
        }
        if (!poll.isMultiple()) {
            if (selected.size() != 1) {
                throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "单选投票只能选择 1 个选项");
            }
        } else {
            int maxChoices = poll.getMaxChoices() == null ? 1 : poll.getMaxChoices();
            if (selected.size() > maxChoices) {
                throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "最多可选择 " + maxChoices + " 个选项");
            }
        }
        for (Long optionId : selected) {
            PollVote vote = new PollVote();
            vote.setPollId(id);
            vote.setOptionId(optionId);
            vote.setUserId(userId);
            vote.setUserName(userName);
            repository.saveVote(vote);
        }
    }

    /**
     * 查询投票，不存在时抛出业务异常。
     *
     * @param id 投票主键
     * @return 投票实体
     */
    private Poll requirePoll(Long id) {
        Poll poll = repository.findPoll(id);
        if (poll == null) {
            throw OaErrorCode.exception(OaErrorCode.POLL_NOT_FOUND);
        }
        return poll;
    }

    /**
     * 用保存命令填充投票实体并校验必填项与时间区间。
     *
     * @param poll 待填充的投票实体
     * @param command 投票保存命令
     */
    private void fill(Poll poll, CultureCommands.PollSave command) {
        if (!StringUtils.hasText(command.getTitle())) {
            throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "投票标题不能为空");
        }
        boolean multiple = Boolean.TRUE.equals(command.getMultipleChoice());
        int maxChoices = command.getMaxChoices() == null ? 1 : command.getMaxChoices();
        if (!multiple) {
            maxChoices = 1;
        } else if (maxChoices < 1) {
            throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "多选最多可选数量必须大于 0");
        }
        if (command.getStartTime() != null && command.getEndTime() != null
                && command.getStartTime().isAfter(command.getEndTime())) {
            throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "投票开始时间不能晚于结束时间");
        }
        poll.setTitle(command.getTitle().trim());
        poll.setDescription(command.getDescription());
        poll.setMultipleChoice(multiple ? 1 : 0);
        poll.setAnonymous(Boolean.TRUE.equals(command.getAnonymous()) ? 1 : 0);
        poll.setMaxChoices(maxChoices);
        poll.setStartTime(command.getStartTime());
        poll.setEndTime(command.getEndTime());
    }

    /**
     * 根据选项保存命令构建投票选项列表。
     *
     * @param commands 选项保存命令列表
     * @return 投票选项实体列表，命令为空时返回空列表
     */
    private static List<PollOption> buildOptions(List<CultureCommands.PollOptionSave> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<PollOption> options = new ArrayList<>();
        int sortNo = 0;
        for (CultureCommands.PollOptionSave command : commands) {
            if (!StringUtils.hasText(command.getOptionText())) {
                throw OaErrorCode.exception(OaErrorCode.POLL_OPTION_REQUIRED, "投票选项内容不能为空");
            }
            PollOption option = new PollOption();
            option.setOptionText(command.getOptionText().trim());
            option.setSortNo(command.getSortNo() == null ? sortNo : command.getSortNo());
            options.add(option);
            sortNo++;
        }
        return options;
    }

    /**
     * 将投票实体转换为投票视图。
     *
     * @param poll 投票实体
     * @return 投票视图
     */
    private static CultureViews.Poll toView(Poll poll) {
        CultureViews.Poll view = new CultureViews.Poll();
        fillView(view, poll);
        return view;
    }

    /**
     * 用投票实体填充投票视图字段。
     *
     * @param view 待填充的投票视图
     * @param poll 投票实体
     */
    private static void fillView(CultureViews.Poll view, Poll poll) {
        view.setId(poll.getId());
        view.setTitle(poll.getTitle());
        view.setDescription(poll.getDescription());
        view.setMultipleChoice(poll.isMultiple());
        view.setAnonymous(poll.getAnonymous() != null && poll.getAnonymous() == 1);
        view.setMaxChoices(poll.getMaxChoices());
        view.setStartTime(poll.getStartTime());
        view.setEndTime(poll.getEndTime());
        view.setStatus(poll.getStatus());
        view.setCreatorId(poll.getCreatorId());
        view.setCreatorName(poll.getCreatorName());
        view.setCreateTime(poll.getCreateTime());
    }

    /**
     * 将投票选项实体转换为带票数统计的选项视图。
     *
     * @param option 选项实体
     * @param voteCount 该选项的得票数
     * @param myChoice 当前用户是否选择了该选项
     * @return 投票选项视图
     */
    private static CultureViews.PollOption toOptionView(PollOption option, Long voteCount, boolean myChoice) {
        CultureViews.PollOption view = new CultureViews.PollOption();
        view.setId(option.getId());
        view.setPollId(option.getPollId());
        view.setOptionText(option.getOptionText());
        view.setSortNo(option.getSortNo());
        view.setVoteCount(voteCount);
        view.setMyChoice(myChoice);
        return view;
    }
}
