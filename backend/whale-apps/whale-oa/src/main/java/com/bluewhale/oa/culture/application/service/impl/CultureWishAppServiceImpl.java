package com.bluewhale.oa.culture.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.CultureWishAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.gateway.CultureUserDirectoryGateway;
import com.bluewhale.oa.culture.domain.model.CultureEmployeeProfile;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import com.bluewhale.oa.culture.domain.model.CultureWish;
import com.bluewhale.oa.culture.domain.model.CultureWishType;
import com.bluewhale.oa.culture.domain.repository.CultureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 生日 / 入职周年祝福应用服务实现。
 *
 * <p>生日与周年由员工档案派生，姓名通过通讯录网关补全，失败时降级为空姓名。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CultureWishAppServiceImpl implements CultureWishAppService {

    private final CultureRepository repository;

    private final CultureUserDirectoryGateway userDirectoryGateway;

    @Override
    public PageResult<CultureViews.Wish> page(CultureQuery query) {
        PageResult<CultureWish> source = repository.pageWishes(query);
        List<CultureViews.Wish> records = source.getRecords().stream()
                .map(CultureWishAppServiceImpl::toView).toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public List<CultureViews.Birthday> listCelebrations(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<CultureEmployeeProfile> profiles = repository.listCelebrationProfiles(target);
        if (profiles.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = userDirectoryGateway.resolveUserNames(profiles.size());
        List<CultureViews.Birthday> result = new ArrayList<>();
        for (CultureEmployeeProfile profile : profiles) {
            if (matches(profile.getBirthday(), target)) {
                result.add(toBirthday(profile, names, CultureWishType.BIRTHDAY, null));
            }
            if (matches(profile.getHireDate(), target)) {
                int years = target.getYear() - profile.getHireDate().getYear();
                if (years > 0) {
                    result.add(toBirthday(profile, names, CultureWishType.ANNIVERSARY, years));
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Wish send(CultureCommands.WishSave command, Long senderId, String senderName) {
        if (senderId == null) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "发送人不能为空");
        }
        if (command.getTargetUserId() == null || !StringUtils.hasText(command.getContent())) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "祝福对象与祝福内容不能为空");
        }
        CultureWishType type = StringUtils.hasText(command.getWishType())
                ? CultureWishType.of(command.getWishType()) : CultureWishType.BIRTHDAY;
        LocalDate today = LocalDate.now();
        if (repository.existsWish(type.name(), command.getTargetUserId(), senderId, today)) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "今天已向该同事送过同类型祝福");
        }
        CultureWish wish = new CultureWish();
        wish.setWishType(type.name());
        wish.setTargetUserId(command.getTargetUserId());
        wish.setTargetName(command.getTargetName());
        wish.setContent(command.getContent().trim());
        wish.setSenderId(senderId);
        wish.setSenderName(senderName);
        wish.setWishDate(today);
        repository.saveWish(wish);
        return toView(wish);
    }

    /**
     * 判断来源日期与目标日期的月、日是否相同。
     *
     * @param source 来源日期，可为 null
     * @param target 目标日期
     * @return 月日相同时返回 true
     */
    private static boolean matches(LocalDate source, LocalDate target) {
        return source != null
                && source.getMonthValue() == target.getMonthValue()
                && source.getDayOfMonth() == target.getDayOfMonth();
    }

    /**
     * 将员工档案组装为生日 / 入职周年视图。
     *
     * @param profile 员工档案
     * @param names 用户ID到姓名的映射
     * @param type 祝福类型
     * @param years 入职周年年限，生日时可为 null
     * @return 生日 / 入职周年视图
     */
    private static CultureViews.Birthday toBirthday(CultureEmployeeProfile profile, Map<Long, String> names,
                                                    CultureWishType type, Integer years) {
        CultureViews.Birthday view = new CultureViews.Birthday();
        view.setUserId(profile.getUserId());
        view.setUserName(names.get(profile.getUserId()));
        view.setDeptId(profile.getDeptId());
        view.setBirthday(profile.getBirthday());
        view.setHireDate(profile.getHireDate());
        view.setYears(years);
        view.setType(type.name());
        return view;
    }

    /**
     * 将祝福领域对象转换为视图对象。
     *
     * @param wish 祝福领域对象
     * @return 祝福视图
     */
    private static CultureViews.Wish toView(CultureWish wish) {
        CultureViews.Wish view = new CultureViews.Wish();
        view.setId(wish.getId());
        view.setWishType(wish.getWishType());
        view.setTargetUserId(wish.getTargetUserId());
        view.setTargetName(wish.getTargetName());
        view.setContent(wish.getContent());
        view.setSenderId(wish.getSenderId());
        view.setSenderName(wish.getSenderName());
        view.setWishDate(wish.getWishDate());
        view.setCreateTime(wish.getCreateTime());
        return view;
    }
}
