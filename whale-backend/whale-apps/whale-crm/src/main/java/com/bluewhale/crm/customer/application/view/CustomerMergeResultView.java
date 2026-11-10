package com.bluewhale.crm.customer.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户合并结果视图（M1-4）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerMergeResultView {

    /** 源客户 ID */
    private Long sourceId;

    /** 目标客户 ID */
    private Long targetId;

    /** 是否幂等命中（源客户此前已合并到该目标） */
    private boolean idempotent;

    /** 目标客户视图 */
    private CustomerView target;

    /** 各上下文引用重写数量，key 为上下文中文名 */
    private Map<String, Long> references = new LinkedHashMap<>();

    /** 迁移联系人数量 */
    private int migratedContacts;

    /** 迁移地址数量 */
    private int migratedAddresses;

    /** 迁移跟进数量 */
    private int migratedFollowUps;

    /** 画像是否迁移 */
    private boolean profileMigrated;

    /** 冲突与提示信息 */
    private List<String> warnings = new ArrayList<>();
}
