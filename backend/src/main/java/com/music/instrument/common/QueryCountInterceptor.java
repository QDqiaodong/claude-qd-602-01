package com.music.instrument.common;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

/**
 * MyBatis 拦截器：每执行一条查询 / 更新就给当前请求的 SQL 计数 +1。
 * mybatis-spring-boot-starter 会自动把 Interceptor 类型的 Bean 注册进 SqlSessionFactory。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class QueryCountInterceptor implements org.apache.ibatis.plugin.Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        SqlQueryCounter.increment();
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
