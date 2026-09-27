package com.music.instrument.common;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 查询探针：仅当请求带 ?sqlProbe=1 时（由 {@link SqlProbeFilter} 开启 ThreadLocal）
 * 统计本次请求真正执行的 MyBatis SELECT 次数，并在响应提交前实时回写 X-Query-Count。
 * 响应体会在控制器返回后立即序列化提交，所以头必须在查询发生时同步写，不能等过滤器链返回。
 * 正常请求 ThreadLocal 为空，计数器不创建、零开销。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlProbeInterceptor implements Interceptor {

    private static final ThreadLocal<Probe> CURRENT = new ThreadLocal<>();

    record Probe(AtomicInteger counter, HttpServletResponse response) {
    }

    public static void begin(HttpServletResponse response) {
        CURRENT.set(new Probe(new AtomicInteger(), response));
    }

    public static int current() {
        Probe probe = CURRENT.get();
        return probe == null ? -1 : probe.counter().get();
    }

    public static void end() {
        CURRENT.remove();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Probe probe = CURRENT.get();
        if (probe != null) {
            int count = probe.counter().incrementAndGet();
            // 响应尚未提交：每执行一条查询就把头更新成最新计数
            probe.response().setHeader(SqlProbeFilter.HEADER, String.valueOf(count));
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }
}
