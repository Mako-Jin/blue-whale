package com.bluewhale.oa.administrative.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.domain.gateway.ContractSealGateway;
import com.bluewhale.oa.administrative.domain.gateway.ErpPurchaseGateway;
import com.bluewhale.oa.administrative.domain.model.BookingStatus;
import com.bluewhale.oa.administrative.domain.model.Driver;
import com.bluewhale.oa.administrative.domain.model.PurchaseItem;
import com.bluewhale.oa.administrative.domain.model.PurchasePushStatus;
import com.bluewhale.oa.administrative.domain.model.PurchaseRequest;
import com.bluewhale.oa.administrative.domain.model.PurchaseStatus;
import com.bluewhale.oa.administrative.domain.model.SealUse;
import com.bluewhale.oa.administrative.domain.model.SealUseStatus;
import com.bluewhale.oa.administrative.domain.model.Vehicle;
import com.bluewhale.oa.administrative.domain.model.VehicleBooking;
import com.bluewhale.oa.administrative.domain.model.VehicleUsage;
import com.bluewhale.oa.administrative.domain.repository.AdministrativeRepository;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.integration.application.service.OutboxAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 行政业务（用车 / 用章 / 采购）单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AdministrativeAppServiceImplTest {

    @Mock
    private AdministrativeRepository repository;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    @Mock
    private SequenceAppService sequenceAppService;

    @Mock
    private ContractSealGateway contractSealGateway;

    @Mock
    private ErpPurchaseGateway erpPurchaseGateway;

    @Mock
    private OutboxAppService outboxAppService;

    private VehicleAppServiceImpl vehicleAppService;

    private SealUseAppServiceImpl sealUseAppService;

    private PurchaseAppServiceImpl purchaseAppService;

    /**
     * 构造被测服务。
     */
    @BeforeEach
    void setUp() {
        vehicleAppService = new VehicleAppServiceImpl(repository, approvalInstanceAppService,
                sequenceAppService);
        sealUseAppService = new SealUseAppServiceImpl(repository, approvalInstanceAppService,
                sequenceAppService, contractSealGateway);
        purchaseAppService = new PurchaseAppServiceImpl(repository, approvalInstanceAppService,
                sequenceAppService, outboxAppService, erpPurchaseGateway);
    }

    @Test
    @DisplayName("dispatch：同车时段重叠时拒绝派车")
    void shouldRejectOverlappingDispatch() {
        when(repository.findBookingById(1L)).thenReturn(Optional.of(approvedBooking()));
        when(repository.findVehicleById(10L)).thenReturn(Optional.of(enabledVehicle()));
        when(repository.findDriverById(20L)).thenReturn(Optional.of(enabledDriver()));
        when(repository.countOverlappingBookings(any(), any(), any(), any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> vehicleAppService.dispatch(1L, dispatchCommand()));

        assertEquals(OaErrorCode.VEHICLE_BOOKING_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("dispatch：未审批通过禁止派车")
    void shouldRejectDispatchWhenNotApproved() {
        VehicleBooking booking = approvedBooking();
        booking.setStatus(BookingStatus.PENDING.name());
        when(repository.findBookingById(1L)).thenReturn(Optional.of(booking));

        BizException exception = assertThrows(BizException.class,
                () -> vehicleAppService.dispatch(1L, dispatchCommand()));

        assertEquals(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("returnVehicle：登记回车并回写车辆里程")
    void shouldRecordReturnAndUpdateMileage() {
        VehicleBooking booking = approvedBooking();
        booking.setVehicleId(10L);
        when(repository.findBookingById(1L)).thenReturn(Optional.of(booking));
        when(repository.findUsageByBookingId(1L)).thenReturn(Optional.empty());
        when(repository.findVehicleById(10L)).thenReturn(Optional.of(enabledVehicle()));

        AdministrativeCommands.ReturnCommand command = new AdministrativeCommands.ReturnCommand();
        command.setStartMileage(new BigDecimal("100"));
        command.setEndMileage(new BigDecimal("180"));
        command.setFuelCost(new BigDecimal("60"));

        vehicleAppService.returnVehicle(1L, command);

        ArgumentCaptor<VehicleUsage> usageCaptor = ArgumentCaptor.forClass(VehicleUsage.class);
        verify(repository).insertUsage(usageCaptor.capture());
        assertEquals(new BigDecimal("180"), usageCaptor.getValue().getEndMileage());
        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(repository).updateVehicle(vehicleCaptor.capture());
        assertEquals(new BigDecimal("180"), vehicleCaptor.getValue().getCurrentMileage());
    }

    @Test
    @DisplayName("create：印章无授权时拒绝申请")
    void shouldRejectSealUseWithoutGrant() {
        when(contractSealGateway.isGranted(30L, 100L)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> sealUseAppService.create(sealCommand()));

        assertEquals(OaErrorCode.SEAL_NOT_AUTHORIZED, exception.getCode());
    }

    @Test
    @DisplayName("register：同一用章单重复登记时拒绝")
    void shouldRejectDuplicateSealRegister() {
        SealUse sealUse = approvedSealUse();
        sealUse.setRegisteredTime(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(repository.findSealUseById(1L)).thenReturn(Optional.of(sealUse));

        BizException exception = assertThrows(BizException.class,
                () -> sealUseAppService.register(1L, 100L));

        assertEquals(OaErrorCode.SEAL_ALREADY_REGISTERED, exception.getCode());
    }

    @Test
    @DisplayName("retryPush：ERP 失败标记 FAILED，重试成功后回写单号")
    void shouldMarkFailedThenRetryPurchasePush() {
        PurchaseRequest purchase = approvedPurchase();
        when(repository.findPurchaseById(1L)).thenReturn(Optional.of(purchase));
        when(erpPurchaseGateway.createPurchaseOrderDraft(any()))
                .thenThrow(new BizException(OaErrorCode.PURCHASE_ERP_UNAVAILABLE, "ERP 不可用"))
                .thenReturn("PO-202611-001");

        assertThrows(BizException.class, () -> purchaseAppService.retryPush(1L));
        assertEquals(PurchasePushStatus.FAILED.name(), purchase.getErpPushStatus());

        purchaseAppService.retryPush(1L);

        assertEquals(PurchasePushStatus.SENT.name(), purchase.getErpPushStatus());
        assertEquals("PO-202611-001", purchase.getErpPoNo());
        verify(repository, org.mockito.Mockito.times(2)).updatePurchase(any());
    }

    @Test
    @DisplayName("pushPending：缺失供应商时标记失败并跳过")
    void shouldSkipPushWhenSupplierMissing() {
        PurchaseRequest purchase = approvedPurchase();
        purchase.setSupplierId(null);
        when(repository.listPushablePurchases(anyInt())).thenReturn(List.of(purchase));

        int success = purchaseAppService.pushPending();

        assertEquals(0, success);
        assertEquals(PurchasePushStatus.FAILED.name(), purchase.getErpPushStatus());
    }

    private VehicleBooking approvedBooking() {
        VehicleBooking booking = new VehicleBooking();
        booking.setId(1L);
        booking.setCode("VEH-202611-001");
        booking.setApplicant("zhangsan");
        booking.setApplicantId(100L);
        booking.setStartTime(LocalDateTime.of(2026, 11, 8, 9, 0));
        booking.setEndTime(LocalDateTime.of(2026, 11, 8, 18, 0));
        booking.setStatus(BookingStatus.APPROVED.name());
        return booking;
    }

    private Vehicle enabledVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(10L);
        vehicle.setPlateNo("沪A12345");
        vehicle.setStatus("ENABLED");
        vehicle.setCurrentMileage(new BigDecimal("100"));
        return vehicle;
    }

    private Driver enabledDriver() {
        Driver driver = new Driver();
        driver.setId(20L);
        driver.setName("李四");
        driver.setStatus("ENABLED");
        return driver;
    }

    private AdministrativeCommands.DispatchCommand dispatchCommand() {
        AdministrativeCommands.DispatchCommand command = new AdministrativeCommands.DispatchCommand();
        command.setVehicleId(10L);
        command.setDriverId(20L);
        return command;
    }

    private AdministrativeCommands.SealUseSaveCommand sealCommand() {
        AdministrativeCommands.SealUseSaveCommand command = new AdministrativeCommands.SealUseSaveCommand();
        command.setApplicant("zhangsan");
        command.setApplicantId(100L);
        command.setSealId(30L);
        command.setSealName("公章");
        command.setReason("合同签署");
        return command;
    }

    private SealUse approvedSealUse() {
        SealUse sealUse = new SealUse();
        sealUse.setId(1L);
        sealUse.setCode("SEAL-202611-001");
        sealUse.setApplicant("zhangsan");
        sealUse.setApplicantId(100L);
        sealUse.setSealId(30L);
        sealUse.setSealName("公章");
        sealUse.setReason("合同签署");
        sealUse.setStatus(SealUseStatus.APPROVED.name());
        return sealUse;
    }

    private PurchaseRequest approvedPurchase() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(1L);
        purchase.setCode("PUR-202611-001");
        purchase.setApplicant("zhangsan");
        purchase.setApplicantId(100L);
        purchase.setReason("办公用品");
        purchase.setSupplierId(50L);
        purchase.setWarehouseId(60L);
        purchase.setCurrency("CNY");
        purchase.setStatus(PurchaseStatus.APPROVED.name());
        purchase.setErpPushStatus(PurchasePushStatus.PENDING.name());
        PurchaseItem item = new PurchaseItem();
        item.setLineNo(1);
        item.setItemCode("A001");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("50"));
        item.setAmount(new BigDecimal("100"));
        purchase.setItems(List.of(item));
        return purchase;
    }
}
