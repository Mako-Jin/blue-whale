package com.bluewhale.oa.culture.domain.repository;

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

import java.time.LocalDate;
import java.util.List;

/**
 * 企业文化仓储端口。
 *
 * @author BlueWhale
 */
public interface CultureRepository {

    // ---------------------------------------------------------------- 文化动态

    /**
     * 文化动态分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CultureEvent> pageEvents(CultureQuery query);

    /**
     * 按主键查询文化动态。
     *
     * @param id 动态ID
     * @return 动态，不存在返回 null
     */
    CultureEvent findEvent(Long id);

    /**
     * 新增文化动态。
     *
     * @param event 动态
     */
    void saveEvent(CultureEvent event);

    /**
     * 更新文化动态。
     *
     * @param event 动态
     */
    void updateEvent(CultureEvent event);

    /**
     * 逻辑删除文化动态。
     *
     * @param id 动态ID
     */
    void deleteEvent(Long id);

    // ------------------------------------------------------------------ 祝福

    /**
     * 祝福分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CultureWish> pageWishes(CultureQuery query);

    /**
     * 判断同类型、同对象、同发送人、同日期是否已送祝福。
     *
     * @param wishType 祝福类型
     * @param targetUserId 祝福对象用户ID
     * @param senderId 发送人用户ID
     * @param wishDate 祝福日期
     * @return 是否存在
     */
    boolean existsWish(String wishType, Long targetUserId, Long senderId, LocalDate wishDate);

    /**
     * 新增祝福。
     *
     * @param wish 祝福
     */
    void saveWish(CultureWish wish);

    /**
     * 查询指定日期生日 / 入职周年且公开展示、未离职的员工档案。
     *
     * @param date 日期
     * @return 员工档案
     */
    List<CultureEmployeeProfile> listCelebrationProfiles(LocalDate date);

    // ------------------------------------------------------------------ 投票

    /**
     * 投票分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Poll> pagePolls(CultureQuery query);

    /**
     * 按主键查询投票。
     *
     * @param id 投票ID
     * @return 投票，不存在返回 null
     */
    Poll findPoll(Long id);

    /**
     * 新增投票。
     *
     * @param poll 投票
     */
    void savePoll(Poll poll);

    /**
     * 更新投票。
     *
     * @param poll 投票
     */
    void updatePoll(Poll poll);

    /**
     * 逻辑删除投票。
     *
     * @param id 投票ID
     */
    void deletePoll(Long id);

    /**
     * 查询投票选项。
     *
     * @param pollId 投票ID
     * @return 选项（按排序号升序）
     */
    List<PollOption> listOptions(Long pollId);

    /**
     * 新增投票选项。
     *
     * @param option 选项
     */
    void saveOption(PollOption option);

    /**
     * 删除投票下全部选项（逻辑删除）。
     *
     * @param pollId 投票ID
     */
    void deleteOptionsByPoll(Long pollId);

    /**
     * 整体替换投票选项。
     *
     * @param pollId 投票ID
     * @param options 新选项
     */
    void replaceOptions(Long pollId, List<PollOption> options);

    /**
     * 查询投票记录。
     *
     * @param pollId 投票ID
     * @return 投票记录
     */
    List<PollVote> listVotes(Long pollId);

    /**
     * 判断用户是否已参与该投票。
     *
     * @param pollId 投票ID
     * @param userId 用户ID
     * @return 是否已投
     */
    boolean existsAnyVote(Long pollId, Long userId);

    /**
     * 新增投票记录。
     *
     * @param vote 投票记录
     */
    void saveVote(PollVote vote);

    // ------------------------------------------------------------------ 建议

    /**
     * 建议分页。
     *
     * @param query 查询条件
     * @param submitterId 提交人过滤（为 null 不过滤）
     * @return 分页结果
     */
    PageResult<Suggestion> pageSuggestions(CultureQuery query, Long submitterId);

    /**
     * 按主键查询建议。
     *
     * @param id 建议ID
     * @return 建议，不存在返回 null
     */
    Suggestion findSuggestion(Long id);

    /**
     * 新增建议。
     *
     * @param suggestion 建议
     */
    void saveSuggestion(Suggestion suggestion);

    /**
     * 更新建议。
     *
     * @param suggestion 建议
     */
    void updateSuggestion(Suggestion suggestion);

    /**
     * 查询建议回复。
     *
     * @param suggestionId 建议ID
     * @return 回复（按时间升序）
     */
    List<SuggestionReply> listReplies(Long suggestionId);

    /**
     * 新增建议回复。
     *
     * @param reply 回复
     */
    void saveReply(SuggestionReply reply);
}
