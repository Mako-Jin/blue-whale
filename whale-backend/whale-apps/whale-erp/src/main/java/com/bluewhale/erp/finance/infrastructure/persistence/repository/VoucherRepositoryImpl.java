package com.bluewhale.erp.finance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.finance.domain.model.FinanceStatus;
import com.bluewhale.erp.finance.domain.model.Voucher;
import com.bluewhale.erp.finance.domain.model.VoucherLine;
import com.bluewhale.erp.finance.domain.model.VoucherQuery;
import com.bluewhale.erp.finance.domain.repository.VoucherRepository;
import com.bluewhale.erp.finance.infrastructure.persistence.entity.VoucherEntity;
import com.bluewhale.erp.finance.infrastructure.persistence.entity.VoucherLineEntity;
import com.bluewhale.erp.finance.infrastructure.persistence.mapper.VoucherLineMapper;
import com.bluewhale.erp.finance.infrastructure.persistence.mapper.VoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 凭证仓储实现（MyBatis-Plus）。
 *
 * <p>账簿查询分两步：先取已记账/已红冲凭证，再按凭证 ID 取分录，
 * 避免在应用层做跨表 JOIN，保持模块内聚。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class VoucherRepositoryImpl implements VoucherRepository {

    private final VoucherMapper voucherMapper;

    private final VoucherLineMapper voucherLineMapper;

    @Override
    public List<Voucher> page(VoucherQuery query) {
        Page<VoucherEntity> page = voucherMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(VoucherQuery query) {
        return voucherMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Voucher> findById(Long id) {
        VoucherEntity entity = voucherMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Voucher voucher = toDomain(entity);
        voucher.setLines(voucherLineMapper.selectList(new LambdaQueryWrapper<VoucherLineEntity>()
                        .eq(VoucherLineEntity::getVoucherId, id)
                        .orderByAsc(VoucherLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(voucher);
    }

    @Override
    public Optional<Voucher> findBySource(String sourceType, String sourceId) {
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getSourceType, sourceType)
                .eq(VoucherEntity::getSourceId, sourceId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public String nextVoucherNo(String periodCode) {
        VoucherEntity last = voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getPeriodCode, periodCode)
                .orderByDesc(VoucherEntity::getVoucherNo)
                .last("limit 1"));
        int sequence = 1;
        if (last != null && last.getVoucherNo() != null && last.getVoucherNo().length() > 6) {
            String suffix = last.getVoucherNo().substring(last.getVoucherNo().length() - 6);
            try {
                sequence = Integer.parseInt(suffix) + 1;
            } catch (NumberFormatException ignored) {
                sequence = lastVoucherSequence(periodCode);
            }
        }
        return String.format("V%s-%06d", periodCode, sequence);
    }

    @Override
    public void insert(Voucher voucher) {
        VoucherEntity entity = new VoucherEntity();
        fill(entity, voucher);
        voucherMapper.insert(entity);
        voucher.setId(entity.getId());
        for (VoucherLine line : voucher.getLines()) {
            VoucherLineEntity lineEntity = new VoucherLineEntity();
            lineEntity.setVoucherId(entity.getId());
            lineEntity.setLineNo(line.getLineNo());
            lineEntity.setAccountCode(line.getAccountCode());
            lineEntity.setAccountName(line.getAccountName());
            lineEntity.setSummary(line.getSummary());
            lineEntity.setDebitAmount(line.getDebitAmount());
            lineEntity.setCreditAmount(line.getCreditAmount());
            lineEntity.setCurrency(line.getCurrency());
            lineEntity.setExchangeRate(line.getExchangeRate());
            voucherLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(Voucher voucher) {
        VoucherEntity entity = new VoucherEntity();
        entity.setId(voucher.getId());
        entity.setStatus(voucher.getStatus());
        entity.setReversedById(voucher.getReversedById());
        voucherMapper.updateById(entity);
    }

    @Override
    public List<VoucherLine> listPostedLines(String accountCode, String fromPeriod, String toPeriod) {
        List<VoucherEntity> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<VoucherEntity>()
                .in(VoucherEntity::getStatus, FinanceStatus.VOUCHER_POSTED, FinanceStatus.VOUCHER_REVERSED)
                .ge(StringUtils.hasText(fromPeriod), VoucherEntity::getPeriodCode, fromPeriod)
                .le(StringUtils.hasText(toPeriod), VoucherEntity::getPeriodCode, toPeriod));
        if (vouchers.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, VoucherEntity> voucherMap = vouchers.stream()
                .collect(Collectors.toMap(VoucherEntity::getId, Function.identity()));
        List<VoucherLineEntity> lines = voucherLineMapper.selectList(new LambdaQueryWrapper<VoucherLineEntity>()
                .in(VoucherLineEntity::getVoucherId, voucherMap.keySet())
                .eq(StringUtils.hasText(accountCode), VoucherLineEntity::getAccountCode, accountCode)
                .orderByAsc(VoucherLineEntity::getVoucherId)
                .orderByAsc(VoucherLineEntity::getLineNo));
        return lines.stream().map(line -> {
            VoucherLine domain = toDomain(line);
            VoucherEntity voucher = voucherMap.get(line.getVoucherId());
            if (voucher != null) {
                domain.setVoucherNo(voucher.getVoucherNo());
                domain.setVoucherDate(voucher.getVoucherDate());
            }
            return domain;
        }).toList();
    }

    /**
     * 兜底：按期间内条数推算序号。
     *
     * @param periodCode 期间编码
     * @return 下一个序号
     */
    private int lastVoucherSequence(String periodCode) {
        return (int) (voucherMapper.selectCount(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getPeriodCode, periodCode)) + 1);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<VoucherEntity> filter(VoucherQuery query) {
        LambdaQueryWrapper<VoucherEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(VoucherEntity::getVoucherNo, query.getKeyword());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(VoucherEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriodCode())) {
            wrapper.eq(VoucherEntity::getPeriodCode, query.getPeriodCode());
        }
        return wrapper.orderByDesc(VoucherEntity::getVoucherDate)
                .orderByDesc(VoucherEntity::getVoucherNo);
    }

    /**
     * 凭证头实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Voucher toDomain(VoucherEntity entity) {
        Voucher voucher = new Voucher();
        voucher.setId(entity.getId());
        voucher.setVoucherNo(entity.getVoucherNo());
        voucher.setVoucherDate(entity.getVoucherDate());
        voucher.setPeriodCode(entity.getPeriodCode());
        voucher.setVoucherType(entity.getVoucherType());
        voucher.setStatus(entity.getStatus());
        voucher.setCurrency(entity.getCurrency());
        voucher.setExchangeRate(entity.getExchangeRate());
        voucher.setTotalDebit(entity.getTotalDebit());
        voucher.setTotalCredit(entity.getTotalCredit());
        voucher.setSourceType(entity.getSourceType());
        voucher.setSourceId(entity.getSourceId());
        voucher.setReversalOfId(entity.getReversalOfId());
        voucher.setReversedById(entity.getReversedById());
        voucher.setRemark(entity.getRemark());
        voucher.setPostedAt(entity.getPostedAt());
        voucher.setPostedBy(entity.getPostedBy());
        return voucher;
    }

    /**
     * 凭证行实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private VoucherLine toDomain(VoucherLineEntity entity) {
        VoucherLine line = new VoucherLine();
        line.setVoucherId(entity.getVoucherId());
        line.setLineNo(entity.getLineNo());
        line.setAccountCode(entity.getAccountCode());
        line.setAccountName(entity.getAccountName());
        line.setSummary(entity.getSummary());
        line.setDebitAmount(entity.getDebitAmount());
        line.setCreditAmount(entity.getCreditAmount());
        line.setCurrency(entity.getCurrency());
        line.setExchangeRate(entity.getExchangeRate());
        return line;
    }

    /**
     * 领域模型写入凭证头实体。
     *
     * @param entity 实体
     * @param voucher 领域模型
     */
    private void fill(VoucherEntity entity, Voucher voucher) {
        entity.setVoucherNo(voucher.getVoucherNo());
        entity.setVoucherDate(voucher.getVoucherDate());
        entity.setPeriodCode(voucher.getPeriodCode());
        entity.setVoucherType(voucher.getVoucherType());
        entity.setStatus(voucher.getStatus());
        entity.setCurrency(voucher.getCurrency());
        entity.setExchangeRate(voucher.getExchangeRate());
        entity.setTotalDebit(voucher.getTotalDebit());
        entity.setTotalCredit(voucher.getTotalCredit());
        entity.setSourceType(voucher.getSourceType());
        entity.setSourceId(voucher.getSourceId());
        entity.setReversalOfId(voucher.getReversalOfId());
        entity.setReversedById(voucher.getReversedById());
        entity.setRemark(voucher.getRemark());
        entity.setPostedAt(voucher.getPostedAt());
        entity.setPostedBy(voucher.getPostedBy());
    }
}
