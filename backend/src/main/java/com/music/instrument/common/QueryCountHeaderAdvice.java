package com.music.instrument.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 把本次请求的 SQL 计数写进响应头 X-Query-Count（含业务异常返回），
 * 供自动化验收脚本断言「查询次数不随历史条数线性增长」。
 */
@RestControllerAdvice
public class QueryCountHeaderAdvice implements ResponseBodyAdvice<Object> {

    public static final String HEADER = "X-Query-Count";

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().add(HEADER, String.valueOf(SqlQueryCounter.count()));
        return body;
    }
}
