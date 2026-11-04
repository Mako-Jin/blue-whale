package com.bluewhale.erp.masterdata.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.masterdata.application.command.AccountSaveCommand;
import com.bluewhale.erp.masterdata.application.command.CustomerSaveCommand;
import com.bluewhale.erp.masterdata.application.command.WarehouseSaveCommand;
import com.bluewhale.erp.masterdata.application.view.AccountView;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MasterDataAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class MasterDataAppServiceImplTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private final AccountingPeriodRepository periodRepository = mock(AccountingPeriodRepository.class);

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);

    private final ReferenceDataRepository referenceDataRepository = mock(ReferenceDataRepository.class);

    private final MasterDataAppServiceImpl service = new MasterDataAppServiceImpl(
            accountRepository, periodRepository, customerRepository, warehouseRepository,
            referenceDataRepository);

    @Test
    @DisplayName("科目分页映射视图与总数")
    void shouldPageAccounts() {
        when(accountRepository.page(any())).thenReturn(List.of(account("1001", "库存现金")));
        when(accountRepository.count(any())).thenReturn(1L);

        PageResult<AccountView> page = service.accountPage(new MasterDataQuery());

        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
        assertEquals("库存现金", page.getRecords().get(0).getName());
    }

    @Test
    @DisplayName("新增科目默认启用且末级")
    void shouldCreateAccountWithDefaults() {
        when(accountRepository.findByCode("1001")).thenReturn(Optional.empty());

        AccountView view = service.createAccount(accountCommand("1001", "库存现金", null, null));

        assertEquals(MasterDataStatus.ENABLED, view.getStatus());
        assertTrue(view.getLeaf());
        verify(accountRepository).insert(any(Account.class));
    }

    @Test
    @DisplayName("科目编码重复时拒绝新增与编辑")
    void shouldRejectDuplicateAccountCode() {
        Account duplicate = account("1001", "已有科目");
        duplicate.setId(10L);
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(duplicate));

        BizException createException = assertThrows(BizException.class,
                () -> service.createAccount(accountCommand("1001", "库存现金", null, null)));
        assertEquals(ErpErrorCode.ACCOUNT_CODE_CONFLICT, createException.getCode());

        Account current = account("1002", "银行存款");
        current.setId(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(current));
        BizException updateException = assertThrows(BizException.class,
                () -> service.updateAccount(9L, accountCommand("1001", "改名", null, null)));
        assertEquals(ErpErrorCode.ACCOUNT_CODE_CONFLICT, updateException.getCode());
    }

    @Test
    @DisplayName("编辑科目保留自身编码不视为冲突")
    void shouldUpdateAccountWithSameCode() {
        Account current = account("1001", "旧名称");
        current.setId(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(current));
        when(accountRepository.findByCode("1001")).thenReturn(Optional.of(current));

        AccountView view = service.updateAccount(9L, accountCommand("1001", "新名称", "ASSET", "DEBIT"));

        assertEquals("新名称", view.getName());
        assertEquals("ASSET", view.getAccountType());
        verify(accountRepository).update(current);
    }

    @Test
    @DisplayName("科目不存在时编辑抛出 NOT_FOUND")
    void shouldRejectMissingAccount() {
        when(accountRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.updateAccount(9L, accountCommand("1001", "库存现金", null, null)));

        assertEquals(ErpErrorCode.ACCOUNT_NOT_FOUND, exception.getCode());
        verify(accountRepository, never()).update(any());
    }

    @Test
    @DisplayName("期间分页、关账与反结账")
    void shouldCloseAndReopenPeriod() {
        AccountingPeriod period = period();
        when(periodRepository.page(any())).thenReturn(List.of(period));
        when(periodRepository.count(any())).thenReturn(1L);
        when(periodRepository.findById(11L)).thenReturn(Optional.of(period));

        PageResult<PeriodView> page = service.periodPage(new MasterDataQuery());
        assertEquals("202611", page.getRecords().get(0).getPeriodCode());

        PeriodView closed = service.closePeriod(11L, 7L);
        assertEquals(MasterDataStatus.PERIOD_CLOSED, closed.getStatus());
        assertEquals(7L, closed.getClosedBy());
        assertEquals(LocalDate.of(2026, 11, 30), closed.getEndDate());

        PeriodView reopened = service.reopenPeriod(11L);
        assertEquals(MasterDataStatus.PERIOD_OPEN, reopened.getStatus());
        verify(periodRepository, org.mockito.Mockito.times(2)).update(period);
    }

    @Test
    @DisplayName("期间不存在时关账抛出 NOT_FOUND")
    void shouldRejectMissingPeriod() {
        when(periodRepository.findById(11L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> service.closePeriod(11L, 7L));

        assertEquals(ErpErrorCode.PERIOD_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("新增客户默认额度为零且启用")
    void shouldCreateCustomerWithDefaults() {
        when(customerRepository.findByCode("CUS-001")).thenReturn(Optional.empty());

        CustomerView view = service.createCustomer(customerCommand("CUS-001", null, null));

        assertEquals(BigDecimal.ZERO, view.getCreditLimit());
        assertEquals(MasterDataStatus.ENABLED, view.getStatus());
        verify(customerRepository).insert(any(Customer.class));
    }

    @Test
    @DisplayName("客户编码冲突与不存在场景")
    void shouldRejectCustomerConflicts() {
        Customer existing = customer("CUS-001");
        existing.setId(9L);
        when(customerRepository.findByCode("CUS-001")).thenReturn(Optional.of(existing));

        BizException createException = assertThrows(BizException.class,
                () -> service.createCustomer(customerCommand("CUS-001", null, null)));
        assertEquals(ErpErrorCode.CUSTOMER_CODE_CONFLICT, createException.getCode());

        when(customerRepository.findById(9L)).thenReturn(Optional.empty());
        BizException missingException = assertThrows(BizException.class,
                () -> service.updateCustomer(9L, customerCommand("CUS-002", null, null)));
        assertEquals(ErpErrorCode.CUSTOMER_NOT_FOUND, missingException.getCode());
    }

    @Test
    @DisplayName("编辑客户更新信用额度")
    void shouldUpdateCustomerCreditLimit() {
        Customer current = customer("CUS-001");
        current.setId(9L);
        when(customerRepository.findById(9L)).thenReturn(Optional.of(current));
        when(customerRepository.findByCode("CUS-001")).thenReturn(Optional.of(current));

        CustomerView view = service.updateCustomer(9L, customerCommand("CUS-001", "5000.00", "ENABLED"));

        assertEquals(new BigDecimal("5000.00"), view.getCreditLimit());
        assertEquals("ENABLED", view.getStatus());
        verify(customerRepository).update(current);
    }

    @Test
    @DisplayName("仓库新增、编辑与冲突")
    void shouldSaveWarehouse() {
        when(warehouseRepository.findByCode("WH-001")).thenReturn(Optional.empty());
        WarehouseView created = service.createWarehouse(warehouseCommand("WH-001", null));
        assertEquals(MasterDataStatus.ENABLED, created.getStatus());
        verify(warehouseRepository).insert(any(Warehouse.class));

        Warehouse current = warehouse("WH-001");
        current.setId(9L);
        when(warehouseRepository.findById(9L)).thenReturn(Optional.of(current));
        when(warehouseRepository.findByCode("WH-002"))
                .thenReturn(Optional.of(warehouseWithId(10L, "WH-002")));
        BizException conflict = assertThrows(BizException.class,
                () -> service.updateWarehouse(9L, warehouseCommand("WH-002", null)));
        assertEquals(ErpErrorCode.WAREHOUSE_CODE_CONFLICT, conflict.getCode());

        when(warehouseRepository.findById(99L)).thenReturn(Optional.empty());
        BizException missing = assertThrows(BizException.class,
                () -> service.updateWarehouse(99L, warehouseCommand("WH-001", null)));
        assertEquals(ErpErrorCode.WAREHOUSE_NOT_FOUND, missing.getCode());
    }

    @Test
    @DisplayName("参考数据映射扩展说明")
    void shouldMapReferenceData() {
        Uom uom = new Uom();
        uom.setId(1L);
        uom.setCode("PCS");
        uom.setName("件");
        uom.setPrecisionNo(0);
        Currency currency = new Currency();
        currency.setId(2L);
        currency.setCode("CNY");
        currency.setName("人民币");
        currency.setBaseFlag(Boolean.TRUE);
        currency.setSymbol("¥");
        TaxCode taxCode = new TaxCode();
        taxCode.setId(3L);
        taxCode.setCode("VAT13");
        taxCode.setName("增值税13%");
        taxCode.setTaxRate(new BigDecimal("0.13"));
        when(referenceDataRepository.listUoms()).thenReturn(List.of(uom));
        when(referenceDataRepository.listCurrencies()).thenReturn(List.of(currency));
        when(referenceDataRepository.listTaxCodes()).thenReturn(List.of(taxCode));

        ReferenceDataView view = service.referenceData();

        assertEquals("小数位 0", view.getUoms().get(0).getExtra());
        assertEquals("本位币", view.getCurrencies().get(0).getExtra());
        assertEquals("13%", view.getTaxCodes().get(0).getExtra());
        assertNotNull(view.getTaxCodes().get(0).getId());
    }

    /**
     * 构造科目。
     *
     * @param code 编码
     * @param name 名称
     * @return 科目
     */
    private static Account account(String code, String name) {
        Account account = new Account();
        account.setCode(code);
        account.setName(name);
        account.setAccountType("ASSET");
        account.setDirection("DEBIT");
        account.setLeaf(Boolean.TRUE);
        account.setStatus(MasterDataStatus.ENABLED);
        return account;
    }

    /**
     * 构造科目命令。
     *
     * @param code 编码
     * @param name 名称
     * @param accountType 类型
     * @param direction 方向
     * @return 命令
     */
    private static AccountSaveCommand accountCommand(String code, String name, String accountType,
                                                     String direction) {
        AccountSaveCommand command = new AccountSaveCommand();
        command.setCode(code);
        command.setName(name);
        command.setAccountType(accountType);
        command.setDirection(direction);
        return command;
    }

    /**
     * 构造 2026-11 期间。
     *
     * @return 期间
     */
    private static AccountingPeriod period() {
        AccountingPeriod period = new AccountingPeriod();
        period.setId(11L);
        period.setPeriodCode("202611");
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));
        period.setStatus(MasterDataStatus.PERIOD_OPEN);
        return period;
    }

    /**
     * 构造客户。
     *
     * @param code 编码
     * @return 客户
     */
    private static Customer customer(String code) {
        Customer customer = new Customer();
        customer.setCode(code);
        customer.setName("客户-" + code);
        customer.setCreditLimit(BigDecimal.ZERO);
        customer.setStatus(MasterDataStatus.ENABLED);
        return customer;
    }

    /**
     * 构造客户命令。
     *
     * @param code 编码
     * @param creditLimit 信用额度
     * @param status 状态
     * @return 命令
     */
    private static CustomerSaveCommand customerCommand(String code, String creditLimit, String status) {
        CustomerSaveCommand command = new CustomerSaveCommand();
        command.setCode(code);
        command.setName("客户-" + code);
        command.setCreditLimit(creditLimit == null ? null : new BigDecimal(creditLimit));
        command.setStatus(status);
        return command;
    }

    /**
     * 构造仓库。
     *
     * @param code 编码
     * @return 仓库
     */
    private static Warehouse warehouse(String code) {
        return warehouseWithId(null, code);
    }

    /**
     * 构造带主键的仓库。
     *
     * @param id 主键
     * @param code 编码
     * @return 仓库
     */
    private static Warehouse warehouseWithId(Long id, String code) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode(code);
        warehouse.setName("仓库-" + code);
        warehouse.setStatus(MasterDataStatus.ENABLED);
        return warehouse;
    }

    /**
     * 构造仓库命令。
     *
     * @param code 编码
     * @param status 状态
     * @return 命令
     */
    private static WarehouseSaveCommand warehouseCommand(String code, String status) {
        WarehouseSaveCommand command = new WarehouseSaveCommand();
        command.setCode(code);
        command.setName("仓库-" + code);
        command.setStatus(status);
        return command;
    }
}
