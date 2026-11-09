package com.bluewhale.mall.catalog.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.application.command.ActivitySaveCommand;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.application.service.ActivityAppService;
import com.bluewhale.mall.catalog.application.service.SkuOfferingService;
import com.bluewhale.mall.catalog.application.view.ActivityView;
import com.bluewhale.mall.catalog.domain.model.Activity;
import com.bluewhale.mall.catalog.domain.model.ActivityStatus;
import com.bluewhale.mall.catalog.domain.repository.ActivityRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 限时特价活动应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ActivityAppServiceImpl implements ActivityAppService {

    private final ActivityRepository activityRepository;

    private final SkuOfferingService skuOfferingService;

    private final MallProperties properties;

    @Override
    public PageResult<ActivityView> page(String code, String name, Long skuId, Long channelId, String status,
                                         Long current, Long size, MallActor actor) {
        actor.requireUserId();
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        List<Activity> activities = activityRepository.page(trimToNull(code), trimToNull(name), skuId,
                channelId, trimToNull(status), (pageNo - 1L) * pageSize, (int) pageSize);
        long total = activityRepository.count(trimToNull(code), trimToNull(name), skuId, channelId, trimToNull(status));
        return PageResult.of(toViews(activities), total, pageNo, pageSize);
    }

    @Override
    public ActivityView detail(Long id, MallActor actor) {
        actor.requireUserId();
        Activity activity = requireActivity(id);
        return toView(activity, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ActivitySaveCommand command, MallActor actor) {
        actor.requireUserId();
        Activity activity = command == null ? null : command.toDomain();
        if (activity == null) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "活动命令不能为空");
        }
        activity.validate();
        if (activityRepository.existsCode(activity.getActivityCode(), activity.getId())) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID,
                    "活动编码已存在：" + activity.getActivityCode());
        }
        // 活动价必须低于当前渠道成交价才有意义：等于或高于直接拒绝，别让运营建一个
        // 「看起来在促销、实际一分不少」的活动
        validateCheaperThanCurrent(activity);
        return activityRepository.save(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status, MallActor actor) {
        actor.requireUserId();
        requireActivity(id);
        ActivityStatus target = ActivityStatus.parse(status);
        activityRepository.updateStatus(id, target.name());
    }

    @Override
    public Map<Long, Activity> matching(Collection<Long> skuIds, Long channelId, LocalDateTime now) {
        Map<Long, Activity> result = new HashMap<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return result;
        }
        List<Activity> hits = activityRepository.matching(new LinkedHashSet<>(skuIds).stream().toList(), now);
        // 同一 SKU 命中多条活动时取活动价最低者（对买家最有利，规则可解释）
        for (Activity activity : hits) {
            if (!activity.hits(now, channelId)) {
                continue;
            }
            Activity current = result.get(activity.getSkuId());
            if (current == null || activity.getActivityPrice().compareTo(current.getActivityPrice()) < 0) {
                result.put(activity.getSkuId(), activity);
            }
        }
        return result;
    }

    /**
     * 校验活动价低于当前零售成交价。
     *
     * @param activity 活动
     */
    private void validateCheaperThanCurrent(Activity activity) {
        Map<Long, SkuOffering> offerings = skuOfferingService.resolve(List.of(activity.getSkuId()), MallMode.RETAIL);
        SkuOffering offering = offerings.get(activity.getSkuId());
        if (offering == null || !offering.isAvailable() || offering.getUnitPrice() == null) {
            return;
        }
        if (activity.getActivityPrice().compareTo(offering.getUnitPrice()) >= 0) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID,
                    "活动价必须低于当前零售价 " + offering.getUnitPrice());
        }
    }

    /**
     * 加载活动（不存在即失败）。
     *
     * @param id 主键
     * @return 活动
     */
    private Activity requireActivity(Long id) {
        Activity activity = activityRepository.findById(id);
        if (activity == null) {
            throw MallErrorCode.exception(MallErrorCode.ACTIVITY_INVALID, "活动不存在或已删除");
        }
        return activity;
    }

    /**
     * 组装视图列表。
     *
     * @param activities 活动列表
     * @return 视图列表
     */
    private List<ActivityView> toViews(List<Activity> activities) {
        return activities.stream().map(activity -> toView(activity, null)).toList();
    }

    /**
     * 组装单个视图。
     *
     * @param activity 活动
     * @param skuCode  SKU 编码（可空，不回填）
     * @return 视图
     */
    private ActivityView toView(Activity activity, String skuCode) {
        ActivityView view = new ActivityView();
        view.setId(activity.getId());
        view.setActivityCode(activity.getActivityCode());
        view.setActivityName(activity.getActivityName());
        view.setChannelId(activity.getChannelId());
        view.setSkuId(activity.getSkuId());
        view.setSkuCode(skuCode);
        view.setActivityPrice(activity.getActivityPrice());
        view.setValidFrom(activity.getValidFrom());
        view.setValidTo(activity.getValidTo());
        view.setStatus(activity.getStatus() == null ? null : activity.getStatus().name());
        view.setRemark(activity.getRemark());
        view.setCreateTime(activity.getCreateTime());
        view.setUpdateTime(activity.getUpdateTime());
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
