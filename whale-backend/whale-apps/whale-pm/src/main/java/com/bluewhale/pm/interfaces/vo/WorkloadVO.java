package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成员负载图（按负责人聚合工作项数量与故事点）
 *
 * @author BlueWhale
 */
@Data
public class WorkloadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成员负载行（按未完成数量降序） */
    private List<WorkloadMemberVO> members;
}
