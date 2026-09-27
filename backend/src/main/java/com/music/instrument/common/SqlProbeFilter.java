package com.music.instrument.common;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 配合 {@link SqlProbeInterceptor}：请求带 ?sqlProbe=1 时开启查询计数，
 * 由拦截器把本次请求的 SELECT 次数实时写入 X-Query-Count 响应头，供自动化检查
 * 「查询次数不随历史条数线性增长」直接断言，生产正常请求完全不走计数。
 */
@Component
@Order(1)
public class SqlProbeFilter implements Filter {

    public static final String HEADER = "X-Query-Count";
    private static final String PARAM = "sqlProbe";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest
                && "1".equals(httpRequest.getParameter(PARAM))
                && response instanceof HttpServletResponse httpResponse) {
            SqlProbeInterceptor.begin(httpResponse);
            try {
                chain.doFilter(request, response);
            } finally {
                SqlProbeInterceptor.end();
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
