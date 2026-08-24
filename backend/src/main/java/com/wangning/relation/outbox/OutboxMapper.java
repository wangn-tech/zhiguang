package com.wangning.relation.outbox;

import org.apache.ibatis.annotations.Mapper;

/**
 * Outbox 事件数据访问接口。
 */
@Mapper
public interface OutboxMapper {

    /**
     * 写入一条业务事件。
     *
     * <p>调用方必须在与业务数据相同的事务中调用本方法。Canal 只会订阅事务提交后的
     * binlog，因此不会消费回滚事务产生的事件。</p>
     *
     * @param record 待保存事件
     * @return 受影响行数
     */
    int insert(OutboxRecord record);
}
