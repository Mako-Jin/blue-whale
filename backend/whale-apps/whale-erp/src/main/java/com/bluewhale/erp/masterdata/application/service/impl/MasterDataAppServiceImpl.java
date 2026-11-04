package com.bluewhale.erp.masterdata.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.application.command.AccountSaveCommand;
import com.bluewhale.erp.masterdata.application.command.CustomerSaveCommand;
import com.bluewhale.erp.masterdata.application.command.WarehouseSaveCommand;
import com.bluewhale.erp.masterdata.application.service.MasterDataAppService;
import com.bluewhale.erp.masterdata.application.view.AccountView;
import com.bluewhale.erp.masterdata.application.view.CodeNameView;
import com.bluewhale.erp.masterdata.application.view.CustomerView;
import com.bluewhale.erp.masterdata.application.view.PeriodView;
import com.bluewhale.erp.masterdata.application.view.ReferenceDataView;
import com.bluewhale.erp.masterdata.application.view.WarehouseView;
import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.model.Currency;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import com.bluewhale.erp.masterdata.domain.model.MasterDataStatus;
import com.bluewhale.erp.masterdata.domain.model.TaxCode;
import com.bluewhale.erp.masterdata.domain.model.Uom;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.AccountRepository;
import com.bluewhale.erp.masterdata.domain.repository.AccountingPeriodRepository;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.masterdata.domain.repository.ReferenceDataRepository;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 主数据应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MasterDataAppServiceImpl implements MasterDataAppService {

    /** 百分比换算基数 */
    private static final BigDecimal PERCENT_BASE = BigDecimal.valueOf(100);

    private final AccountRepository accountRepository;

    private final AccountingPeriodRepository periodRepository;

    private final CustomerRepository customerRepository;

    private final WarehouseRepository warehouseRepository;

    private final ReferenceDataRepository referenceDataRepository;

    @Override
    public PageResult<AccountView> accountPage(MasterDataQuery query) {
        List<AccountView> records = accountRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, accountRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountView createAccount(AccountSaveCommand command) {
        accountRepository.findByCode(command.getCode()).ifPresent(existing -> {
            throw ErpErrorCode.exception(ErpErrorCode.ACCOUNT_CODE_CONFLICT);
        });
        Account account = new Account();
        fill(account, command);
        accountRepository.insert(account);
        return toView(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountView updateAccount(Long id, AccountSaveCommand command) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ACCOUNT_NOT_FOUND));
        Optional<Account> sameCode = accountRepository.findByCode(command.getCode());
        if (sameCode.isPresent() && !sameCode.get().getId().equals(id)) {
            throw ErpErrorCode.exception(ErpErrorCode.ACCOUNT_CODE_CONFLICT);
        }
        fill(account, command);
        accountRepository.update(account);
        return toView(account);
    }

    @Override
    public PageResult<PeriodView> periodPage(MasterDataQuery query) {
        List<PeriodView> records = periodRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, periodRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PeriodView closePeriod(Long id, Long actorId) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PERIOD_NOT_FOUND));
        period.close(actorId, LocalDateTime.now());
        periodRepository.update(period);
        return toView(period);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PeriodView reopenPeriod(Long id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.PERIOD_NOT_FOUND));
        period.reopen();
        periodRepository.update(period);
        return toView(period);
    }

    @Override
    public PageResult<CustomerView> customerPage(MasterDataQuery query) {
        List<CustomerView> records = customerRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, customerRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView createCustomer(CustomerSaveCommand command) {
        customerRepository.findByCode(command.getCode()).ifPresent(existing -> {
            throw ErpErrorCode.exception(ErpErrorCode.CUSTOMER_CODE_CONFLICT);
        });
        Customer customer = new Customer();
        fill(customer, command);
        customerRepository.insert(customer);
        return toView(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView updateCustomer(Long id, CustomerSaveCommand command) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.CUSTOMER_NOT_FOUND));
        Optional<Customer> sameCode = customerRepository.findByCode(command.getCode());
        if (sameCode.isPresent() && !sameCode.get().getId().equals(id)) {
            throw ErpErrorCode.exception(ErpErrorCode.CUSTOMER_CODE_CONFLICT);
        }
        fill(customer, command);
        customerRepository.update(customer);
        return toView(customer);
    }

    @Override
    public PageResult<WarehouseView> warehousePage(MasterDataQuery query) {
        List<WarehouseView> records = warehouseRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, warehouseRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseView createWarehouse(WarehouseSaveCommand command) {
        warehouseRepository.findByCode(command.getCode()).ifPresent(existing -> {
            throw ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_CODE_CONFLICT);
        });
        Warehouse warehouse = new Warehouse();
        fill(warehouse, command);
        warehouseRepository.insert(warehouse);
        return toView(warehouse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseView updateWarehouse(Long id, WarehouseSaveCommand command) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_NOT_FOUND));
        Optional<Warehouse> sameCode = warehouseRepository.findByCode(command.getCode());
        if (sameCode.isPresent() && !sameCode.get().getId().equals(id)) {
            throw ErpErrorCode.exception(ErpErrorCode.WAREHOUSE_CODE_CONFLICT);
        }
        fill(warehouse, command);
        warehouseRepository.update(warehouse);
        return toView(warehouse);
    }

    @Override
    public ReferenceDataView referenceData() {
        ReferenceDataView view = new ReferenceDataView();
        view.setUoms(referenceDataRepository.listUoms().stream().map(this::toView).toList());
        view.setCurrencies(referenceDataRepository.listCurrencies().stream().map(this::toView).toList());
        view.setTaxCodes(referenceDataRepository.listTaxCodes().stream().map(this::toView).toList());
        return view;
    }

    /**
     * 科目命令写入领域模型。
     *
     * @param account 领域模型
     * @param command 命令
     */
    private void fill(Account account, AccountSaveCommand command) {
        account.setCode(command.getCode());
        account.setName(command.getName());
        account.setAccountType(command.getAccountType());
        account.setDirection(command.getDirection());
        account.setParentCode(command.getParentCode());
        account.setLeaf(command.getLeaf() == null ? Boolean.TRUE : command.getLeaf());
        account.setStatus(StringUtils.hasText(command.getStatus())
                ? command.getStatus() : MasterDataStatus.ENABLED);
        account.setRemark(command.getRemark());
    }

    /**
     * 客户命令写入领域模型。
     *
     * @param customer 领域模型
     * @param command 命令
     */
    private void fill(Customer customer, CustomerSaveCommand command) {
        customer.setCode(command.getCode());
        customer.setName(command.getName());
        customer.setShortName(command.getShortName());
        customer.setTaxNo(command.getTaxNo());
        customer.setContactName(command.getContactName());
        customer.setContactPhone(command.getContactPhone());
        customer.changeCreditLimit(command.getCreditLimit() == null ? BigDecimal.ZERO : command.getCreditLimit());
        customer.setStatus(StringUtils.hasText(command.getStatus())
                ? command.getStatus() : MasterDataStatus.ENABLED);
        customer.setRemark(command.getRemark());
    }

    /**
     * 仓库命令写入领域模型。
     *
     * @param warehouse 领域模型
     * @param command 命令
     */
    private void fill(Warehouse warehouse, WarehouseSaveCommand command) {
        warehouse.setCode(command.getCode());
        warehouse.setName(command.getName());
        warehouse.setManager(command.getManager());
        warehouse.setAddress(command.getAddress());
        warehouse.setStatus(StringUtils.hasText(command.getStatus())
                ? command.getStatus() : MasterDataStatus.ENABLED);
        warehouse.setRemark(command.getRemark());
    }

    /**
     * 科目转视图。
     *
     * @param account 领域模型
     * @return 视图
     */
    private AccountView toView(Account account) {
        AccountView view = new AccountView();
        view.setId(account.getId());
        view.setCode(account.getCode());
        view.setName(account.getName());
        view.setAccountType(account.getAccountType());
        view.setDirection(account.getDirection());
        view.setParentCode(account.getParentCode());
        view.setLeaf(account.getLeaf());
        view.setStatus(account.getStatus());
        view.setRemark(account.getRemark());
        return view;
    }

    /**
     * 期间转视图。
     *
     * @param period 领域模型
     * @return 视图
     */
    private PeriodView toView(AccountingPeriod period) {
        PeriodView view = new PeriodView();
        view.setId(period.getId());
        view.setPeriodCode(period.getPeriodCode());
        view.setStartDate(period.getStartDate());
        view.setEndDate(period.getEndDate());
        view.setStatus(period.getStatus());
        view.setClosedAt(period.getClosedAt());
        view.setClosedBy(period.getClosedBy());
        view.setRemark(period.getRemark());
        return view;
    }

    /**
     * 客户转视图。
     *
     * @param customer 领域模型
     * @return 视图
     */
    private CustomerView toView(Customer customer) {
        CustomerView view = new CustomerView();
        view.setId(customer.getId());
        view.setCode(customer.getCode());
        view.setName(customer.getName());
        view.setShortName(customer.getShortName());
        view.setTaxNo(customer.getTaxNo());
        view.setContactName(customer.getContactName());
        view.setContactPhone(customer.getContactPhone());
        view.setCreditLimit(customer.getCreditLimit());
        view.setStatus(customer.getStatus());
        view.setRemark(customer.getRemark());
        return view;
    }

    /**
     * 仓库转视图。
     *
     * @param warehouse 领域模型
     * @return 视图
     */
    private WarehouseView toView(Warehouse warehouse) {
        WarehouseView view = new WarehouseView();
        view.setId(warehouse.getId());
        view.setCode(warehouse.getCode());
        view.setName(warehouse.getName());
        view.setManager(warehouse.getManager());
        view.setAddress(warehouse.getAddress());
        view.setStatus(warehouse.getStatus());
        view.setRemark(warehouse.getRemark());
        return view;
    }

    /**
     * 计量单位转视图。
     *
     * @param uom 领域模型
     * @return 视图
     */
    private CodeNameView toView(Uom uom) {
        CodeNameView view = new CodeNameView();
        view.setId(uom.getId());
        view.setCode(uom.getCode());
        view.setName(uom.getName());
        view.setExtra("小数位 " + uom.getPrecisionNo());
        return view;
    }

    /**
     * 币种转视图。
     *
     * @param currency 领域模型
     * @return 视图
     */
    private CodeNameView toView(Currency currency) {
        CodeNameView view = new CodeNameView();
        view.setId(currency.getId());
        view.setCode(currency.getCode());
        view.setName(currency.getName());
        view.setExtra(Boolean.TRUE.equals(currency.getBaseFlag()) ? "本位币" : currency.getSymbol());
        return view;
    }

    /**
     * 税码转视图。
     *
     * @param taxCode 领域模型
     * @return 视图
     */
    private CodeNameView toView(TaxCode taxCode) {
        CodeNameView view = new CodeNameView();
        view.setId(taxCode.getId());
        view.setCode(taxCode.getCode());
        view.setName(taxCode.getName());
        view.setExtra(taxCode.getTaxRate().multiply(PERCENT_BASE).stripTrailingZeros().toPlainString() + "%");
        return view;
    }
}
