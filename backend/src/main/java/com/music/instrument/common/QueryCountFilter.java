package com.music.instrument.common;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求边界：进入时复位 SQL 计数器，结束时清理 ThreadLocal，防止线程复用串号。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QueryCountFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        SqlQueryCounter.reset();
        try {
            filterChain.doFilter(request, response);
        } finally {
            SqlQueryCounter.clear();
        }
    }
}
