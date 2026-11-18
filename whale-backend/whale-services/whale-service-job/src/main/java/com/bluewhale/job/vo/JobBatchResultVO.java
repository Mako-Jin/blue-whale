package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量启停/批量删除结果：成功条数 + 逐条失败明细。
 *
 * <p>接口整体返回 200，部分失败通过 {@code failures} 表达，便于前端一次性提示全部问题。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobBatchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功条数 */
    private int successCount;

    /** 失败明细 */
    private List<JobBatchFailureVO> failures = new ArrayList<>();
}
