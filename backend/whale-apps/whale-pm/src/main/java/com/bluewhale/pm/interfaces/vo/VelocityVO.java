package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 迭代速率图（最近若干已关闭迭代的承诺与完成）
 *
 * @author BlueWhale
 */
@Data
public class VelocityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 迭代速率序列（按结束时间升序） */
    private List<VelocitySprintVO> sprints;
}
