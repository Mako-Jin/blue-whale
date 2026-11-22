package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 迭代工作项批量请求
 *
 * @author BlueWhale
 */
@Data
public class SprintItemRequest {

    /** 工作项ID列表 */
    @NotEmpty(message = "{validation.pm.sprint-item.item-ids-required}")
    private List<Long> itemIds;
}
