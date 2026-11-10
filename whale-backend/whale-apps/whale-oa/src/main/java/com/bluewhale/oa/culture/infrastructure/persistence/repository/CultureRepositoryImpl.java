package com.bluewhale.oa.culture.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.culture.domain.model.CultureEmployeeProfile;
import com.bluewhale.oa.culture.domain.model.CultureEvent;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import com.bluewhale.oa.culture.domain.model.CultureWish;
import com.bluewhale.oa.culture.domain.model.Poll;
import com.bluewhale.oa.culture.domain.model.PollOption;
import com.bluewhale.oa.culture.domain.model.PollVote;
import com.bluewhale.oa.culture.domain.model.Suggestion;
import com.bluewhale.oa.culture.domain.model.SuggestionReply;
import com.bluewhale.oa.culture.domain.repository.CultureRepository;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.CultureEventEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.CultureWishEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.PollEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.PollOptionEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.PollVoteEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.SuggestionEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.entity.SuggestionReplyEntity;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.CultureEventMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.CultureWishMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.PollMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.PollOptionMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.PollVoteMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.SuggestionMapper;
import com.bluewhale.oa.culture.infrastructure.persistence.mapper.SuggestionReplyMapper;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.EmployeeProfileEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.EmployeeProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 企业文化仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CultureRepositoryImpl implements CultureRepository {

    private static final String EMPLOYMENT_LEFT = "LEFT";

    private final CultureEventMapper eventMapper;

    private final CultureWishMapper wishMapper;

    private final PollMapper pollMapper;

    private final PollOptionMapper optionMapper;

    private final PollVoteMapper voteMapper;

    private final SuggestionMapper suggestionMapper;

    private final SuggestionReplyMapper replyMapper;

    private final EmployeeProfileMapper employeeProfileMapper;

    // ---------------------------------------------------------------- 文化动态

    @Override
    public PageResult<CultureEvent> pageEvents(CultureQuery query) {
        Page<CultureEventEntity> page = eventMapper.selectPage(query.toPage(), eventFilter(query));
        return PageResult.of(page.convert(CultureRepositoryImpl::toEvent));
    }

    @Override
    public CultureEvent findEvent(Long id) {
        CultureEventEntity entity = eventMapper.selectById(id);
        return entity == null ? null : toEvent(entity);
    }

    @Override
    public void saveEvent(CultureEvent event) {
        CultureEventEntity entity = new CultureEventEntity();
        BeanUtils.copyProperties(event, entity);
        eventMapper.insert(entity);
        event.setId(entity.getId());
    }

    @Override
    public void updateEvent(CultureEvent event) {
        CultureEventEntity entity = new CultureEventEntity();
        BeanUtils.copyProperties(event, entity);
        eventMapper.updateById(entity);
    }

    @Override
    public void deleteEvent(Long id) {
        eventMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 祝福

    @Override
    public PageResult<CultureWish> pageWishes(CultureQuery query) {
        Page<CultureWishEntity> page = wishMapper.selectPage(query.toPage(), wishFilter(query));
        return PageResult.of(page.convert(CultureRepositoryImpl::toWish));
    }

    @Override
    public boolean existsWish(String wishType, Long targetUserId, Long senderId, LocalDate wishDate) {
        Long total = wishMapper.selectCount(new LambdaQueryWrapper<CultureWishEntity>()
                .eq(CultureWishEntity::getWishType, wishType)
                .eq(CultureWishEntity::getTargetUserId, targetUserId)
                .eq(CultureWishEntity::getSenderId, senderId)
                .eq(CultureWishEntity::getWishDate, wishDate));
        return total != null && total > 0;
    }

    @Override
    public void saveWish(CultureWish wish) {
        CultureWishEntity entity = new CultureWishEntity();
        BeanUtils.copyProperties(wish, entity);
        wishMapper.insert(entity);
        wish.setId(entity.getId());
    }

    @Override
    public List<CultureEmployeeProfile> listCelebrationProfiles(LocalDate date) {
        List<EmployeeProfileEntity> entities = employeeProfileMapper.selectList(
                new LambdaQueryWrapper<EmployeeProfileEntity>()
                        .eq(EmployeeProfileEntity::getCultureVisible, Boolean.TRUE)
                        .and(wrapper -> wrapper
                                .isNull(EmployeeProfileEntity::getEmploymentStatus)
                                .or().ne(EmployeeProfileEntity::getEmploymentStatus, EMPLOYMENT_LEFT))
                        .apply("((MONTH(birthday) = {0} AND DAY(birthday) = {1})"
                                        + " OR (MONTH(hire_date) = {0} AND DAY(hire_date) = {1}))",
                                date.getMonthValue(), date.getDayOfMonth())
                        .orderByAsc(EmployeeProfileEntity::getId));
        return entities.stream().map(entity -> {
            CultureEmployeeProfile profile = new CultureEmployeeProfile();
            profile.setUserId(entity.getUserId());
            profile.setDeptId(entity.getDeptId());
            profile.setBirthday(entity.getBirthday());
            profile.setHireDate(entity.getHireDate());
            return profile;
        }).toList();
    }

    // ------------------------------------------------------------------ 投票

    @Override
    public PageResult<Poll> pagePolls(CultureQuery query) {
        Page<PollEntity> page = pollMapper.selectPage(query.toPage(), pollFilter(query));
        return PageResult.of(page.convert(CultureRepositoryImpl::toPoll));
    }

    @Override
    public Poll findPoll(Long id) {
        PollEntity entity = pollMapper.selectById(id);
        return entity == null ? null : toPoll(entity);
    }

    @Override
    public void savePoll(Poll poll) {
        PollEntity entity = new PollEntity();
        BeanUtils.copyProperties(poll, entity);
        pollMapper.insert(entity);
        poll.setId(entity.getId());
    }

    @Override
    public void updatePoll(Poll poll) {
        PollEntity entity = new PollEntity();
        BeanUtils.copyProperties(poll, entity);
        pollMapper.updateById(entity);
    }

    @Override
    public void deletePoll(Long id) {
        pollMapper.deleteById(id);
    }

    @Override
    public List<PollOption> listOptions(Long pollId) {
        return optionMapper.selectList(new LambdaQueryWrapper<PollOptionEntity>()
                        .eq(PollOptionEntity::getPollId, pollId)
                        .orderByAsc(PollOptionEntity::getSortNo)
                        .orderByAsc(PollOptionEntity::getId))
                .stream().map(CultureRepositoryImpl::toOption).toList();
    }

    @Override
    public void saveOption(PollOption option) {
        PollOptionEntity entity = new PollOptionEntity();
        BeanUtils.copyProperties(option, entity);
        optionMapper.insert(entity);
        option.setId(entity.getId());
    }

    @Override
    public void deleteOptionsByPoll(Long pollId) {
        optionMapper.delete(new LambdaQueryWrapper<PollOptionEntity>()
                .eq(PollOptionEntity::getPollId, pollId));
    }

    @Override
    public void replaceOptions(Long pollId, List<PollOption> options) {
        deleteOptionsByPoll(pollId);
        for (PollOption option : options) {
            option.setId(null);
            option.setPollId(pollId);
            saveOption(option);
        }
    }

    @Override
    public List<PollVote> listVotes(Long pollId) {
        return voteMapper.selectList(new LambdaQueryWrapper<PollVoteEntity>()
                        .eq(PollVoteEntity::getPollId, pollId)
                        .orderByAsc(PollVoteEntity::getId))
                .stream().map(CultureRepositoryImpl::toVote).toList();
    }

    @Override
    public boolean existsAnyVote(Long pollId, Long userId) {
        Long total = voteMapper.selectCount(new LambdaQueryWrapper<PollVoteEntity>()
                .eq(PollVoteEntity::getPollId, pollId)
                .eq(PollVoteEntity::getUserId, userId));
        return total != null && total > 0;
    }

    @Override
    public void saveVote(PollVote vote) {
        PollVoteEntity entity = new PollVoteEntity();
        BeanUtils.copyProperties(vote, entity);
        voteMapper.insert(entity);
        vote.setId(entity.getId());
    }

    // ------------------------------------------------------------------ 建议

    @Override
    public PageResult<Suggestion> pageSuggestions(CultureQuery query, Long submitterId) {
        Page<SuggestionEntity> page = suggestionMapper.selectPage(query.toPage(),
                suggestionFilter(query, submitterId));
        return PageResult.of(page.convert(CultureRepositoryImpl::toSuggestion));
    }

    @Override
    public Suggestion findSuggestion(Long id) {
        SuggestionEntity entity = suggestionMapper.selectById(id);
        return entity == null ? null : toSuggestion(entity);
    }

    @Override
    public void saveSuggestion(Suggestion suggestion) {
        SuggestionEntity entity = new SuggestionEntity();
        BeanUtils.copyProperties(suggestion, entity);
        suggestionMapper.insert(entity);
        suggestion.setId(entity.getId());
    }

    @Override
    public void updateSuggestion(Suggestion suggestion) {
        SuggestionEntity entity = new SuggestionEntity();
        BeanUtils.copyProperties(suggestion, entity);
        suggestionMapper.updateById(entity);
    }

    @Override
    public List<SuggestionReply> listReplies(Long suggestionId) {
        return replyMapper.selectList(new LambdaQueryWrapper<SuggestionReplyEntity>()
                        .eq(SuggestionReplyEntity::getSuggestionId, suggestionId)
                        .orderByAsc(SuggestionReplyEntity::getCreateTime)
                        .orderByAsc(SuggestionReplyEntity::getId))
                .stream().map(CultureRepositoryImpl::toReply).toList();
    }

    @Override
    public void saveReply(SuggestionReply reply) {
        SuggestionReplyEntity entity = new SuggestionReplyEntity();
        BeanUtils.copyProperties(reply, entity);
        replyMapper.insert(entity);
        reply.setId(entity.getId());
    }

    // ---------------------------------------------------------------- 公共方法

    /**
     * 构建文化动态分页查询条件，支持关键字、状态与类型过滤。
     *
     * @param query 文化查询条件
     * @return 文化动态查询包装器
     */
    private LambdaQueryWrapper<CultureEventEntity> eventFilter(CultureQuery query) {
        return new LambdaQueryWrapper<CultureEventEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(CultureEventEntity::getTitle, query.getKeyword())
                        .or().like(CultureEventEntity::getContent, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), CultureEventEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getEventType()),
                        CultureEventEntity::getEventType, query.getEventType())
                .orderByDesc(CultureEventEntity::getEventDate)
                .orderByDesc(CultureEventEntity::getId);
    }

    /**
     * 构建祝福分页查询条件，支持关键字、祝福类型与祝福对象过滤。
     *
     * @param query 文化查询条件
     * @return 祝福查询包装器
     */
    private LambdaQueryWrapper<CultureWishEntity> wishFilter(CultureQuery query) {
        return new LambdaQueryWrapper<CultureWishEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(CultureWishEntity::getTargetName, query.getKeyword())
                        .or().like(CultureWishEntity::getContent, query.getKeyword()))
                .eq(StringUtils.hasText(query.getWishType()),
                        CultureWishEntity::getWishType, query.getWishType())
                .eq(query.getTargetUserId() != null,
                        CultureWishEntity::getTargetUserId, query.getTargetUserId())
                .orderByDesc(CultureWishEntity::getWishDate)
                .orderByDesc(CultureWishEntity::getId);
    }

    /**
     * 构建投票分页查询条件，支持关键字与状态过滤。
     *
     * @param query 文化查询条件
     * @return 投票查询包装器
     */
    private LambdaQueryWrapper<PollEntity> pollFilter(CultureQuery query) {
        return new LambdaQueryWrapper<PollEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(PollEntity::getTitle, query.getKeyword())
                        .or().like(PollEntity::getDescription, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), PollEntity::getStatus, query.getStatus())
                .orderByDesc(PollEntity::getStartTime)
                .orderByDesc(PollEntity::getId);
    }

    /**
     * 构建建议分页查询条件，支持关键字、状态、类别与提交人过滤。
     *
     * @param query 文化查询条件
     * @param submitterId 提交人用户ID，为空时不按提交人过滤
     * @return 建议查询包装器
     */
    private LambdaQueryWrapper<SuggestionEntity> suggestionFilter(CultureQuery query, Long submitterId) {
        return new LambdaQueryWrapper<SuggestionEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(SuggestionEntity::getTitle, query.getKeyword())
                        .or().like(SuggestionEntity::getContent, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), SuggestionEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCategory()),
                        SuggestionEntity::getCategory, query.getCategory())
                .eq(submitterId != null, SuggestionEntity::getSubmitterId, submitterId)
                .orderByDesc(SuggestionEntity::getCreateTime)
                .orderByDesc(SuggestionEntity::getId);
    }

    /**
     * 将文化动态实体转换为领域模型。
     *
     * @param entity 文化动态实体
     * @return 文化动态领域模型
     */
    private static CultureEvent toEvent(CultureEventEntity entity) {
        CultureEvent model = new CultureEvent();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    /**
     * 将祝福实体转换为领域模型。
     *
     * @param entity 祝福实体
     * @return 祝福领域模型
     */
    private static CultureWish toWish(CultureWishEntity entity) {
        CultureWish model = new CultureWish();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    /**
     * 将投票实体转换为领域模型。
     *
     * @param entity 投票实体
     * @return 投票领域模型
     */
    private static Poll toPoll(PollEntity entity) {
        Poll model = new Poll();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    /**
     * 将投票选项实体转换为领域模型。
     *
     * @param entity 投票选项实体
     * @return 投票选项领域模型
     */
    private static PollOption toOption(PollOptionEntity entity) {
        PollOption model = new PollOption();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    /**
     * 将投票记录实体转换为领域模型。
     *
     * @param entity 投票记录实体
     * @return 投票记录领域模型
     */
    private static PollVote toVote(PollVoteEntity entity) {
        PollVote model = new PollVote();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    /**
     * 将建议实体转换为领域模型。
     *
     * @param entity 建议实体
     * @return 建议领域模型
     */
    private static Suggestion toSuggestion(SuggestionEntity entity) {
        Suggestion model = new Suggestion();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    /**
     * 将建议回复实体转换为领域模型。
     *
     * @param entity 建议回复实体
     * @return 建议回复领域模型
     */
    private static SuggestionReply toReply(SuggestionReplyEntity entity) {
        SuggestionReply model = new SuggestionReply();
        BeanUtils.copyProperties(entity, model);
        return model;
    }
}
