package com.wangning.auth.audit;

import org.apache.ibatis.annotations.Mapper;

/**
 * 登录审计日志数据访问接口。
 */
@Mapper
public interface LoginLogMapper {

    /**
     * 新增登录审计日志并回填主键。
     *
     * @param loginLog 待保存日志
     * @return 受影响的行数
     */
    int insert(LoginLog loginLog);
}
