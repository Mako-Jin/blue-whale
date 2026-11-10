package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.MemberSaveCommand;
import com.bluewhale.mall.catalog.application.service.MemberAppService;
import com.bluewhale.mall.catalog.application.view.MemberView;
import com.bluewhale.mall.catalog.domain.model.MallMember;
import com.bluewhale.mall.catalog.domain.repository.MemberRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商城会员应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MemberAppServiceImpl implements MemberAppService {

    private final MemberRepository memberRepository;

    private final MallProperties properties;

    @Override
    public PageResult<MemberView> page(String keyword, String status, Long current, Long size, MallActor actor) {
        actor.requireUserId();
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        List<MallMember> members = memberRepository.page(trimToNull(keyword), trimToNull(status),
                (pageNo - 1L) * pageSize, (int) pageSize);
        long total = memberRepository.count(trimToNull(keyword), trimToNull(status));
        return PageResult.of(toViews(members), total, pageNo, pageSize);
    }

    @Override
    public MemberView detail(Long id, MallActor actor) {
        actor.requireUserId();
        return toView(requireMember(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(MemberSaveCommand command, MallActor actor) {
        actor.requireUserId();
        MallMember member = command == null ? null : command.toDomain();
        if (member == null) {
            throw MallErrorCode.exception(MallErrorCode.MEMBER_INVALID, "会员命令不能为空");
        }
        member.validate();
        return memberRepository.save(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id, MallActor actor) {
        actor.requireUserId();
        requireMember(id);
        memberRepository.delete(id);
    }

    @Override
    public boolean isMember(Long userId, LocalDateTime now) {
        if (userId == null) {
            return false;
        }
        MallMember member = memberRepository.findByUserId(userId);
        return member != null && member.effective(now);
    }

    /**
     * 加载会员（不存在即失败）。
     *
     * @param id 主键
     * @return 会员
     */
    private MallMember requireMember(Long id) {
        MallMember member = memberRepository.findById(id);
        if (member == null) {
            throw MallErrorCode.exception(MallErrorCode.MEMBER_INVALID, "会员不存在或已删除");
        }
        return member;
    }

    /**
     * 组装视图列表。
     *
     * @param members 会员列表
     * @return 视图列表
     */
    private List<MemberView> toViews(List<MallMember> members) {
        return members.stream().map(this::toView).toList();
    }

    /**
     * 组装单个视图。
     *
     * @param member 会员
     * @return 视图
     */
    private MemberView toView(MallMember member) {
        MemberView view = new MemberView();
        view.setId(member.getId());
        view.setUserId(member.getUserId());
        view.setMemberNo(member.getMemberNo());
        view.setLevelCode(member.getLevelCode());
        view.setValidTo(member.getValidTo());
        view.setStatus(member.getStatus() == null ? null : member.getStatus().name());
        view.setRemark(member.getRemark());
        view.setCreateTime(member.getCreateTime());
        view.setUpdateTime(member.getUpdateTime());
        return view;
    }

    /**
     * 空白转 null。
     *
     * @param value 输入
     * @return 规整结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
