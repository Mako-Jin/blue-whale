package com.bluewhale.crm.commerce.application.view;

import lombok.Data;

/**
 * 应收对账同步结果视图。
 *
 * @author BlueWhale
 */
@Data
public class ArSyncResultView {

    /** 成功同步客户数 */
    private int synced;

    /** 跳过客户数（ERP 无应收 / 客户为空） */
    private int skipped;

    /** 失败客户数（ERP 不可用或返回异常） */
    private int failed;

    /** 结果说明 */
    private String message;

    /**
     * 构造同步结果。
     *
     * @param synced 成功数
     * @param skipped 跳过数
     * @param failed 失败数
     * @param message 说明
     * @return 结果视图
     */
    public static ArSyncResultView of(int synced, int skipped, int failed, String message) {
        ArSyncResultView view = new ArSyncResultView();
        view.setSynced(synced);
        view.setSkipped(skipped);
        view.setFailed(failed);
        view.setMessage(message);
        return view;
    }
}
