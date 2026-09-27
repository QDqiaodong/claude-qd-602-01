package com.music.instrument.common;

/**
 * 请求级 SQL 计数器（演示 / 验收用）：配合 QueryCountInterceptor 统计一次请求内
 * 实际执行的 SQL 条数，由 QueryCountHeaderAdvice 以响应头 X-Query-Count 透出，
 * 用来证明批量装配后查询次数不随乐器台数、历史条数线性增长。
 */
public final class SqlQueryCounter {

    private static final ThreadLocal<Integer> COUNT = new ThreadLocal<>();

    private SqlQueryCounter() {
    }

    public static void reset() {
        COUNT.set(0);
    }

    public static void increment() {
        Integer current = COUNT.get();
        if (current != null) {
            COUNT.set(current + 1);
        }
    }

    public static int count() {
        Integer current = COUNT.get();
        return current == null ? 0 : current;
    }

    public static void clear() {
        COUNT.remove();
    }
}
