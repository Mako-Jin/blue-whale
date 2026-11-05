package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * PDM 变更任务锁（单实例执行）
 *
 * <p>以 {@code lockKey} 为主键，定时任务通过条件更新抢占；
 * 抢锁成功才执行，锁到期后可被其他实例抢占。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("pdm_chg_job_lock")
public class PdmChgJobLock implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 锁键 */
    @TableId(type = IdType.INPUT)
    private String lockKey;

    /** 持有者 */
    private String owner;

    /** 锁到期时间 */
    private LocalDateTime lockedUntil;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
