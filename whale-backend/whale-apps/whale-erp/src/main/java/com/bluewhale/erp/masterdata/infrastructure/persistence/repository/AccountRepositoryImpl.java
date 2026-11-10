package com.bluewhale.erp.masterdata.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.AccountEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 会计科目仓储实现（MyBatis-Plus）。
 *
 * <p>租户条件由 starter 的租户行插件统一追加，本类不做租户判断。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountMapper accountMapper;

    @Override
    public List<Account> page(MasterDataQuery query) {
        Page<AccountEntity> page = accountMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MasterDataQuery query) {
        return accountMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Account> findById(Long id) {
        return Optional.ofNullable(accountMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByCode(String code) {
        return Optional.ofNullable(accountMapper.selectOne(new LambdaQueryWrapper<AccountEntity>()
                .eq(AccountEntity::getCode, code))).map(this::toDomain);
    }

    @Override
    public List<Account> listEnabled() {
        return accountMapper.selectList(new LambdaQueryWrapper<AccountEntity>()
                        .eq(AccountEntity::getStatus, "ENABLED")
                        .orderByAsc(AccountEntity::getCode))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(Account account) {
        AccountEntity entity = new AccountEntity();
        fill(entity, account);
        accountMapper.insert(entity);
        account.setId(entity.getId());
    }

    @Override
    public void update(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.setId(account.getId());
        fill(entity, account);
        accountMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<AccountEntity> filter(MasterDataQuery query) {
        LambdaQueryWrapper<AccountEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(AccountEntity::getCode, query.getKeyword())
                    .or().like(AccountEntity::getName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AccountEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getAccountType())) {
            wrapper.eq(AccountEntity::getAccountType, query.getAccountType());
        }
        return wrapper.orderByAsc(AccountEntity::getCode);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Account toDomain(AccountEntity entity) {
        Account account = new Account();
        account.setId(entity.getId());
        account.setCode(entity.getCode());
        account.setName(entity.getName());
        account.setAccountType(entity.getAccountType());
        account.setDirection(entity.getDirection());
        account.setParentCode(entity.getParentCode());
        account.setLeaf(entity.getLeaf());
        account.setStatus(entity.getStatus());
        account.setRemark(entity.getRemark());
        return account;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param account 领域模型
     */
    private void fill(AccountEntity entity, Account account) {
        entity.setCode(account.getCode());
        entity.setName(account.getName());
        entity.setAccountType(account.getAccountType());
        entity.setDirection(account.getDirection());
        entity.setParentCode(account.getParentCode());
        entity.setLeaf(account.getLeaf());
        entity.setStatus(account.getStatus());
        entity.setRemark(account.getRemark());
    }
}
