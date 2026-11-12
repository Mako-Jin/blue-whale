package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveBrandConfigCommand;
import com.bluewhale.auth.application.model.query.BrandPageQuery;
import com.bluewhale.auth.application.model.query.BrandResolveQuery;
import com.bluewhale.auth.application.model.vo.BrandConfigVO;
import com.bluewhale.auth.application.service.BrandAppService;
import com.bluewhale.auth.domain.aggregate.BrandConfigAggregate;
import com.bluewhale.auth.domain.repository.BrandConfigRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 品牌配置应用服务实现（权限中心扩展 M0）。
 *
 * <p>事务仅包裹「聚合加载 → 领域行为 → 保存」；登录页解析为纯查询，不加事务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandAppServiceImpl implements BrandAppService {

    private final BrandConfigRepository brandConfigRepository;

    /**
     * 登录页品牌解析（免登录）
     *
     * @param query 解析查询
     * @return 品牌视图对象
     */
    @Override
    public BrandConfigVO resolve(BrandResolveQuery query) {
        String targetType = StringUtils.hasText(query.targetType())
                ? query.targetType().trim().toUpperCase(Locale.ROOT)
                : BrandConfigAggregate.TARGET_PLATFORM;
        Long targetId = query.targetId() == null ? 0L : query.targetId();
        String domain = query.domain() == null ? "" : query.domain().trim();
        return brandConfigRepository.findForResolve(targetType, targetId, domain)
                .filter(BrandConfigAggregate::isEnabled)
                .map(BrandConfigVO::from)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "未找到可用的品牌配置"));
    }

    /**
     * 分页查询品牌配置
     *
     * @param query 分页查询
     * @return 品牌分页结果
     */
    @Override
    public PageResult<BrandConfigVO> page(BrandPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        BrandConfigRepository.BrandPage page = brandConfigRepository.page(
                current, size, query.keyword(), query.targetType(), query.status());
        List<BrandConfigVO> records = page.records().stream().map(BrandConfigVO::from).toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 新增品牌配置
     *
     * @param command 保存命令
     * @return 新建品牌视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BrandConfigVO create(SaveBrandConfigCommand command) {
        String targetType = normalizeTargetType(command.targetType());
        Long targetId = command.targetId() == null ? 0L : command.targetId();
        String domain = normalizeDomain(command.domain());
        brandConfigRepository.findByTarget(targetType, targetId, domain).ifPresent(existing -> {
            throw new BizException(ErrorCode.PARAM_ERROR, "该作用域与域名已存在品牌配置");
        });
        BrandConfigAggregate brand = BrandConfigAggregate.create(targetType, targetId, domain,
                command.brandName(), command.logoUrl(), command.loginTitle(), command.institutionName(),
                command.copyright(), command.primaryColor(), command.backgroundUrl(),
                command.priority() == null ? 0 : command.priority());
        if (command.status() != null && command.status() == BrandConfigAggregate.STATUS_DISABLED) {
            brand.disable();
        }
        return BrandConfigVO.from(brandConfigRepository.save(brand));
    }

    /**
     * 编辑品牌配置
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的品牌视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BrandConfigVO update(Long id, SaveBrandConfigCommand command) {
        BrandConfigAggregate brand = require(id);
        brand.updateTheme(command.brandName(), command.logoUrl(), command.loginTitle(),
                command.institutionName(), command.copyright(), command.primaryColor(),
                command.backgroundUrl(), command.priority() == null ? 0 : command.priority());
        if (command.status() != null) {
            if (command.status() == BrandConfigAggregate.STATUS_ENABLED) {
                brand.enable();
            } else {
                brand.disable();
            }
        }
        return BrandConfigVO.from(brandConfigRepository.save(brand));
    }

    /**
     * 删除品牌配置（逻辑删除）
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        brandConfigRepository.deleteById(id);
    }

    /**
     * 按主键加载品牌配置，不存在时抛 404
     *
     * @param id 主键
     * @return 品牌聚合
     */
    private BrandConfigAggregate require(Long id) {
        return brandConfigRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "品牌配置不存在"));
    }

    /**
     * 规范化作用域类型（空值回落 PLATFORM）
     *
     * @param targetType 作用域类型
     * @return 大写作用域类型
     */
    private String normalizeTargetType(String targetType) {
        return StringUtils.hasText(targetType)
                ? targetType.trim().toUpperCase(Locale.ROOT)
                : BrandConfigAggregate.TARGET_PLATFORM;
    }

    /**
     * 规范化域名（空值回落空串）
     *
     * @param domain 域名
     * @return 去空白后的域名
     */
    private String normalizeDomain(String domain) {
        return domain == null ? "" : domain.trim();
    }
}
