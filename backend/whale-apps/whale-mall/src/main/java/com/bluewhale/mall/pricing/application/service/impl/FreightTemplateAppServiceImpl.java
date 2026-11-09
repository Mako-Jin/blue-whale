package com.bluewhale.mall.pricing.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.pricing.application.command.FreightTemplateSaveCommand;
import com.bluewhale.mall.pricing.application.service.FreightTemplateAppService;
import com.bluewhale.mall.pricing.application.view.FreightTemplateView;
import com.bluewhale.mall.pricing.domain.model.FreightChargeType;
import com.bluewhale.mall.pricing.domain.model.FreightTemplate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateQuery;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateStatus;
import com.bluewhale.mall.pricing.domain.repository.FreightTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 运费模板应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FreightTemplateAppServiceImpl implements FreightTemplateAppService {

    private final FreightTemplateRepository freightTemplateRepository;

    private final MallProperties properties;

    @Override
    public PageResult<FreightTemplateView> page(FreightTemplateQuery query, Long current, Long size,
                                                MallActor actor) {
        actor.requireUserId();
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        FreightTemplateQuery condition = query == null ? new FreightTemplateQuery() : query;
        List<FreightTemplate> templates = freightTemplateRepository.page(condition,
                (pageNo - 1L) * pageSize, (int) pageSize);
        List<FreightTemplateView> views = new ArrayList<>(templates.size());
        for (FreightTemplate template : templates) {
            views.add(toView(template));
        }
        return PageResult.of(views, freightTemplateRepository.count(condition), pageNo, pageSize);
    }

    @Override
    public FreightTemplateView detail(Long id, MallActor actor) {
        actor.requireUserId();
        return toView(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FreightTemplateView create(FreightTemplateSaveCommand command, MallActor actor) {
        actor.requireUserId();
        FreightTemplate template = new FreightTemplate();
        apply(template, command);
        template.validate();
        if (freightTemplateRepository.existsName(template.getTemplateName(), null)) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_NAME_CONFLICT);
        }
        freightTemplateRepository.save(template);
        return toView(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FreightTemplateView update(Long id, FreightTemplateSaveCommand command, MallActor actor) {
        actor.requireUserId();
        FreightTemplate template = requireTemplate(id);
        apply(template, command);
        template.validate();
        if (freightTemplateRepository.existsName(template.getTemplateName(), id)) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_NAME_CONFLICT);
        }
        freightTemplateRepository.save(template);
        return toView(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status, MallActor actor) {
        actor.requireUserId();
        FreightTemplate template = requireTemplate(id);
        FreightTemplateStatus target = FreightTemplateStatus.parse(status);
        template.setStatus(target);
        freightTemplateRepository.updateStatus(template.getId(), target.name());
    }

    @Override
    public BigDecimal freightFor(Long freightTemplateId, BigDecimal goodsAmount) {
        return freightFor(freightTemplateId, goodsAmount, null);
    }

    @Override
    public BigDecimal freightFor(Long freightTemplateId, BigDecimal goodsAmount, Integer quantity) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (freightTemplateId == null || goodsAmount == null) {
            return zero;
        }
        return freightTemplateRepository.findById(freightTemplateId)
                .filter(FreightTemplate::isActive)
                .map(template -> template.freightFor(goodsAmount, quantity))
                .orElse(zero);
    }

    /**
     * 按主键取模板，不存在则抛业务异常。
     *
     * @param id 模板 ID
     * @return 模板
     */
    private FreightTemplate requireTemplate(Long id) {
        return freightTemplateRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_NOT_FOUND));
    }

    /**
     * 把命令字段写入领域模型。
     *
     * @param template 目标模板
     * @param command  保存命令
     */
    private void apply(FreightTemplate template, FreightTemplateSaveCommand command) {
        template.setTemplateName(command.getTemplateName());
        template.setChargeType(parseChargeType(command.getChargeType()));
        template.setBaseAmount(command.getBaseAmount());
        template.setFreeThreshold(command.getFreeThreshold());
        template.setPiecePrice(command.getPiecePrice());
        template.setPieceFreeThreshold(command.getPieceFreeThreshold());
        template.setStatus(FreightTemplateStatus.parse(command.getStatus()));
        template.setRemark(command.getRemark() == null ? null : command.getRemark().trim());
    }

    /**
     * 解析计费方式，无法识别时抛业务异常（不静默回落）。
     *
     * @param value 计费方式文本
     * @return 计费方式
     */
    private FreightChargeType parseChargeType(String value) {
        if (!StringUtils.hasText(value)) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID, "必须指定计费方式");
        }
        try {
            return FreightChargeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw MallErrorCode.exception(MallErrorCode.FREIGHT_TEMPLATE_INVALID,
                    "无法识别的计费方式：" + value);
        }
    }

    /**
     * 领域模型转视图。
     *
     * @param template 模板
     * @return 视图
     */
    private FreightTemplateView toView(FreightTemplate template) {
        FreightTemplateView view = new FreightTemplateView();
        view.setId(template.getId());
        view.setTemplateName(template.getTemplateName());
        view.setChargeType(template.getChargeType() == null ? null : template.getChargeType().name());
        view.setBaseAmount(template.getBaseAmount());
        view.setFreeThreshold(template.getFreeThreshold());
        view.setPiecePrice(template.getPiecePrice());
        view.setPieceFreeThreshold(template.getPieceFreeThreshold());
        view.setStatus(template.getStatus() == null ? null : template.getStatus().name());
        view.setRemark(template.getRemark());
        view.setCreateTime(template.getCreateTime());
        view.setUpdateTime(template.getUpdateTime());
        return view;
    }
}
