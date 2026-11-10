package com.bluewhale.lms.training.application.view;

import lombok.Data;

/**
 * 必修分配执行结果（批量报名幂等的可观测凭据）。
 *
 * @author BlueWhale
 */
@Data
public class AssignmentResultView {

    /** 本次新建分配数 */
    private int created;

    /** 已存在跳过数（幂等命中） */
    private int skipped;

    /**
     * 构造结果。
     *
     * @param created 新建数
     * @param skipped 跳过数
     * @return 结果视图
     */
    public static AssignmentResultView of(int created, int skipped) {
        AssignmentResultView view = new AssignmentResultView();
        view.setCreated(created);
        view.setSkipped(skipped);
        return view;
    }
}
