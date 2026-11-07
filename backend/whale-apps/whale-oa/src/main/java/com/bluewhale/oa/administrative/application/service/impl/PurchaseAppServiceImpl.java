package com.bluewhale.oa.administrative.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.gateway.ErpPurchaseGateway;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import com.bluewhale.oa.administrative.domain.model.ErpPurchaseOrderRequest;
import com.bluewhale.oa.administrative.domain.model.PurchaseItem;
import com.bluewhale.oa.administrative.domain.model.PurchasePushStatus;
import com.bluewhale.oa.administrative.domain.model.PurchaseRequest;
import com.bluewhale.oa.administrative.domain.model.PurchaseStatus;
import com.bluewhale.oa.administrative.domain.repository.AdministrativeRepository;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.integration.application.command.OutboxAppendCommand;
import com.bluewhale.oa.integration.application.service.OutboxAppService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购应用服务实现。
 *
 * <p>审批通过后在同一事务内追加 Outbox，ERP 采购订单草稿由 Job 或手工重试推送，
 * 失败不阻断审批通过。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseAppServiceImpl implements PurchaseAppService {

    private static final String BIZ_TYPE_PURCHASE = "PURCHASE";

    private static final String CODE_BIZ_TYPE = "PURCHASE";

    private static final String CODE_PREFIX = "PUR";

    private static final String EVENT_TYPE_PURCHASE_APPROVED = "OA_PURCHASE_APPROVED";

    private static final String AGGREGATE_TYPE_PURCHASE = "PURCHASE";

    private static final String ERP_SOURCE_TYPE = "OA_PURCHASE";

    private static final String DEFAULT_CURRENCY = "CNY";

    private static final int MAX_PUSH_BATCH = 50;

    private final AdministrativeRepository repository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    private final SequenceAppService sequenceAppService;

    private final OutboxAppService outboxAppService;

    private final ErpPurchaseGateway erpPurchaseGateway;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public PageResult<AdministrativeViews.PurchaseView> page(AdministrativeQuery query) {
        List<AdministrativeViews.PurchaseView> records = repository.pagePurchases(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, repository.countPurchases(query), query.getCurrent(), query.getSize());
    }

    @Override
    public AdministrativeViews.PurchaseView detail(Long id) {
        return toView(requirePurchase(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.PurchaseView create(AdministrativeCommands.PurchaseSaveCommand command) {
        PurchaseRequest purchase = new PurchaseRequest();
        apply(purchase, command);
        purchase.setCode(sequenceAppService.nextCode(CODE_BIZ_TYPE, CODE_PREFIX));
        purchase.setItems(buildItems(command.getItems()));
        purchase.calculateTotal();
        purchase.setStatus(PurchaseStatus.PENDING.name());
        purchase.setErpPushStatus(PurchasePushStatus.NONE.name());
        repository.insertPurchase(purchase);
        repository.replacePurchaseItems(purchase.getId(), purchase.getItems());
        registerApproval(purchase);
        log.info("采购申请已创建: id={}, code={}, amount={}", purchase.getId(), purchase.getCode(),
                purchase.getTotalAmount());
        return toView(purchase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.PurchaseView update(Long id, AdministrativeCommands.PurchaseSaveCommand command) {
        PurchaseRequest purchase = requirePurchase(id);
        purchase.assertEditable();
        apply(purchase, command);
        purchase.setItems(buildItems(command.getItems()));
        purchase.calculateTotal();
        repository.updatePurchase(purchase);
        repository.replacePurchaseItems(id, purchase.getItems());
        return toView(purchase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseRequest purchase = requirePurchase(id);
        purchase.assertEditable();
        repository.deletePurchase(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, AdministrativeCommands.PurchaseStatusCommand command) {
        PurchaseRequest purchase = requirePurchase(id);
        PurchaseStatus target = PurchaseStatus.of(command.getStatus());
        if (!purchase.changeStatus(target)) {
            return;
        }
        if (target == PurchaseStatus.APPROVED) {
            purchase.setErpPushStatus(PurchasePushStatus.PENDING.name());
        }
        repository.updatePurchase(purchase);
        if (target == PurchaseStatus.APPROVED) {
            appendApprovedEvent(purchase);
        }
        log.info("采购状态已变更: id={}, status={}", id, purchase.getStatus());
    }

    @Override
    public int pushPending() {
        List<PurchaseRequest> pending = repository.listPushablePurchases(MAX_PUSH_BATCH);
        int success = 0;
        for (PurchaseRequest purchase : pending) {
            try {
                pushOne(purchase);
                success++;
            } catch (RuntimeException exception) {
                log.warn("采购申请 ERP 推送失败: id={}, error={}", purchase.getId(), exception.getMessage());
            }
        }
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.PurchaseView retryPush(Long id) {
        PurchaseRequest purchase = requirePurchase(id);
        if (PurchaseStatus.of(purchase.getStatus()) != PurchaseStatus.APPROVED) {
            throw OaErrorCode.exception(OaErrorCode.PURCHASE_STATUS_CONFLICT, "仅审批通过的采购申请可推送 ERP");
        }
        pushOne(purchase);
        return toView(purchase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long purchaseId, String result) {
        if (PurchaseStatus.of(result) == PurchaseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.PURCHASE_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        AdministrativeCommands.PurchaseStatusCommand command = new AdministrativeCommands.PurchaseStatusCommand();
        command.setStatus(result);
        updateStatus(purchaseId, command);
    }

    private void pushOne(PurchaseRequest purchase) {
        if (PurchasePushStatus.of(purchase.getErpPushStatus()) == PurchasePushStatus.SENT) {
            return;
        }
        if (purchase.getSupplierId() == null || purchase.getWarehouseId() == null) {
            purchase.setErpPushStatus(PurchasePushStatus.FAILED.name());
            repository.updatePurchase(purchase);
            throw OaErrorCode.exception(OaErrorCode.PURCHASE_INVALID, "推送 ERP 前请补充供应商与收货仓库");
        }
        try {
            String poNo = erpPurchaseGateway.createPurchaseOrderDraft(buildRequest(purchase));
            purchase.setErpPoNo(poNo);
            purchase.setErpPushStatus(PurchasePushStatus.SENT.name());
            repository.updatePurchase(purchase);
            log.info("采购申请 ERP 订单草稿已创建: id={}, poNo={}", purchase.getId(), poNo);
        } catch (RuntimeException exception) {
            purchase.setErpPushStatus(PurchasePushStatus.FAILED.name());
            repository.updatePurchase(purchase);
            throw exception;
        }
    }

    private ErpPurchaseOrderRequest buildRequest(PurchaseRequest purchase) {
        ErpPurchaseOrderRequest request = new ErpPurchaseOrderRequest();
        request.setSourceType(ERP_SOURCE_TYPE);
        request.setSourceId(purchase.getId());
        request.setSourceCode(purchase.getCode());
        request.setSupplierId(purchase.getSupplierId());
        request.setWarehouseId(purchase.getWarehouseId());
        request.setDocDate(LocalDate.now());
        request.setExpectedDate(purchase.getExpectedDate());
        request.setCurrency(purchase.getCurrency());
        request.setRemark(purchase.getReason());
        request.setLines(purchase.getItems().stream().map(item -> {
            ErpPurchaseOrderRequest.Line line = new ErpPurchaseOrderRequest.Line();
            line.setItemCode(item.getItemCode());
            line.setItemName(item.getItemName());
            line.setQuantity(item.getQuantity());
            line.setUnitPrice(item.getUnitPrice());
            return line;
        }).toList());
        return request;
    }

    private void apply(PurchaseRequest purchase, AdministrativeCommands.PurchaseSaveCommand command) {
        purchase.setApplicant(command.getApplicant());
        purchase.setApplicantId(command.getApplicantId());
        purchase.setDeptId(command.getDeptId());
        purchase.setReason(command.getReason());
        purchase.setExpectedDate(command.getExpectedDate());
        purchase.setSupplierId(command.getSupplierId());
        purchase.setWarehouseId(command.getWarehouseId());
        purchase.setCurrency(StringUtils.hasText(command.getCurrency()) ? command.getCurrency() : DEFAULT_CURRENCY);
        purchase.setRemark(command.getRemark());
    }

    private List<PurchaseItem> buildItems(List<AdministrativeCommands.PurchaseItemCommand> commands) {
        int lineNo = 1;
        List<PurchaseItem> items = new java.util.ArrayList<>();
        for (AdministrativeCommands.PurchaseItemCommand command : commands) {
            PurchaseItem item = new PurchaseItem();
            item.setLineNo(lineNo++);
            item.setItemCode(command.getItemCode());
            item.setItemName(command.getItemName());
            item.setQuantity(command.getQuantity());
            item.setUnitPrice(command.getUnitPrice());
            item.setSupplierAdvice(command.getSupplierAdvice());
            items.add(item);
        }
        return items;
    }

    private void registerApproval(PurchaseRequest purchase) {
        if (purchase.getApprovalInstanceId() != null) {
            return;
        }
        ApprovalInstanceRegisterCommand command = new ApprovalInstanceRegisterCommand();
        command.setBusinessType(BIZ_TYPE_PURCHASE);
        command.setBusinessId(purchase.getId());
        command.setBusinessCode(purchase.getCode());
        command.setTitle("采购申请-" + purchase.getReason());
        command.setInitiatorId(purchase.getApplicantId());
        command.setInitiatorName(purchase.getApplicant());
        command.setRemark(purchase.getRemark());
        ApprovalInstanceView instance = approvalInstanceAppService.register(command);
        purchase.setApprovalInstanceId(instance.getId());
        repository.updatePurchase(purchase);
    }

    private void appendApprovedEvent(PurchaseRequest purchase) {
        OutboxAppendCommand command = new OutboxAppendCommand();
        command.setEventKey("purchase:" + purchase.getId());
        command.setEventType(EVENT_TYPE_PURCHASE_APPROVED);
        command.setAggregateType(AGGREGATE_TYPE_PURCHASE);
        command.setAggregateId(String.valueOf(purchase.getId()));
        command.setPayload(payload(purchase));
        outboxAppService.append(command);
    }

    private String payload(PurchaseRequest purchase) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purchaseId", purchase.getId());
        data.put("code", purchase.getCode());
        data.put("totalAmount", purchase.getTotalAmount());
        data.put("currency", purchase.getCurrency());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            log.warn("采购 Outbox 载荷序列化失败: id={}", purchase.getId());
            return null;
        }
    }

    private PurchaseRequest requirePurchase(Long id) {
        return repository.findPurchaseById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.PURCHASE_NOT_FOUND));
    }

    private AdministrativeViews.PurchaseView toView(PurchaseRequest purchase) {
        AdministrativeViews.PurchaseView view = new AdministrativeViews.PurchaseView();
        view.setId(purchase.getId());
        view.setCode(purchase.getCode());
        view.setApplicant(purchase.getApplicant());
        view.setApplicantId(purchase.getApplicantId());
        view.setDeptId(purchase.getDeptId());
        view.setReason(purchase.getReason());
        view.setExpectedDate(purchase.getExpectedDate());
        view.setSupplierId(purchase.getSupplierId());
        view.setWarehouseId(purchase.getWarehouseId());
        view.setTotalAmount(purchase.getTotalAmount());
        view.setCurrency(purchase.getCurrency());
        view.setStatus(purchase.getStatus());
        view.setApprovalInstanceId(purchase.getApprovalInstanceId());
        view.setErpPushStatus(purchase.getErpPushStatus());
        view.setErpPoNo(purchase.getErpPoNo());
        view.setRemark(purchase.getRemark());
        view.setItems(purchase.getItems().stream().map(item -> {
            AdministrativeViews.ItemView itemView = new AdministrativeViews.ItemView();
            itemView.setId(item.getId());
            itemView.setLineNo(item.getLineNo());
            itemView.setItemCode(item.getItemCode());
            itemView.setItemName(item.getItemName());
            itemView.setQuantity(item.getQuantity());
            itemView.setUnitPrice(item.getUnitPrice());
            itemView.setAmount(item.getAmount());
            itemView.setSupplierAdvice(item.getSupplierAdvice());
            return itemView;
        }).toList());
        view.setCreateTime(purchase.getCreateTime());
        return view;
    }
}
