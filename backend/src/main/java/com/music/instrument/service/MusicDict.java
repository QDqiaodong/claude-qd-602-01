package com.music.instrument.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 琴行业务词典：枚举名称、状态机、计费参数集中在此，便于出题时被要求调整或扩展。
 * 注意这里只放「口径」，不放数据；默认值一律在 service 的 create 里落地。
 */
public final class MusicDict {

    private MusicDict() {
    }

    // ---------------- 乐器分类 ----------------
    private static final Map<String, String> CATEGORY = new LinkedHashMap<>();

    static {
        CATEGORY.put("GUITAR", "吉他");
        CATEGORY.put("PIANO", "键盘钢琴");
        CATEGORY.put("WIND", "管乐");
        CATEGORY.put("STRING", "弦乐");
        CATEGORY.put("PERCUSSION", "打击乐");
    }

    // ---------------- 乐器状态机 ----------------
    private static final Map<String, String> INSTRUMENT_STATUS = new LinkedHashMap<>();

    static {
        INSTRUMENT_STATUS.put("IN_STOCK", "在库");
        INSTRUMENT_STATUS.put("RENTED", "租出");
        INSTRUMENT_STATUS.put("REPAIRING", "维修中");
        INSTRUMENT_STATUS.put("SCRAPPED", "报废");
    }

    /** 报废是终态；在库、租出、维修中三者之间可以互相流转。 */
    private static final Map<String, Set<String>> INSTRUMENT_FLOW = Map.of(
            "IN_STOCK", Set.of("RENTED", "REPAIRING", "SCRAPPED"),
            "RENTED", Set.of("IN_STOCK", "REPAIRING", "SCRAPPED"),
            "REPAIRING", Set.of("IN_STOCK", "RENTED", "SCRAPPED"),
            "SCRAPPED", Set.of());

    // ---------------- 调律师 ----------------
    private static final Map<String, String> TECH_LEVEL = new LinkedHashMap<>();

    static {
        TECH_LEVEL.put("JUNIOR", "初级");
        TECH_LEVEL.put("SENIOR", "高级");
        TECH_LEVEL.put("MASTER", "首席");
    }

    private static final Map<String, String> TECH_STATUS = new LinkedHashMap<>();

    static {
        TECH_STATUS.put("ACTIVE", "在岗");
        TECH_STATUS.put("LEAVE", "休假");
        TECH_STATUS.put("OFF", "离职");
    }

    // ---------------- 服务单 ----------------
    private static final Map<String, String> SERVICE_TYPE = new LinkedHashMap<>();

    static {
        SERVICE_TYPE.put("TUNING", "调律");
        SERVICE_TYPE.put("REPAIR", "维修");
        SERVICE_TYPE.put("OVERHAUL", "大修");
    }

    private static final Map<String, String> SERVICE_STATUS = new LinkedHashMap<>();

    static {
        SERVICE_STATUS.put("PLANNED", "已排期");
        SERVICE_STATUS.put("DOING", "进行中");
        SERVICE_STATUS.put("PENDING_ACCEPT", "待验收");
        SERVICE_STATUS.put("REWORKING", "返修中");
        SERVICE_STATUS.put("DONE", "已完成");
        SERVICE_STATUS.put("CANCELED", "已取消");
    }

    /**
     * 通用状态接口只保留「开始施工 / 取消派单」；完工提交、验收通过、退回返修都由专属接口处理。
     * 专属链路：DOING --提交完工--> PENDING_ACCEPT --验收通过--> DONE
     *                              PENDING_ACCEPT --退回返修--> REWORKING --再次提交完工--> PENDING_ACCEPT
     */
    private static final Map<String, Set<String>> SERVICE_FLOW = Map.of(
            "PLANNED", Set.of("DOING", "CANCELED"),
            "DOING", Set.of("CANCELED"),
            "PENDING_ACCEPT", Set.of(),
            "REWORKING", Set.of("CANCELED"),
            "DONE", Set.of(),
            "CANCELED", Set.of());

    // ---------------- 租赁 ----------------
    private static final Map<String, String> RENTAL_STATUS = new LinkedHashMap<>();

    static {
        RENTAL_STATUS.put("BOOKED", "已预约");
        RENTAL_STATUS.put("RENTING", "租用中");
        RENTAL_STATUS.put("RETURNED", "已归还");
        RENTAL_STATUS.put("OVERDUE", "逾期归还");
        // OVERDUE_ACTIVE 只用于只读装配：库里仍是旧状态码、没有实际归还日且按当前日期已逾期的订单，
        // 两个入口统一展示为「逾期中」。不参与任何写入，也不允许按它建单。
        RENTAL_STATUS.put("OVERDUE_ACTIVE", "逾期中");
    }

    // ---------------- 完工 / 验收轮次 ----------------
    private static final Map<String, String> COMPLETION_PHASE = new LinkedHashMap<>();

    static {
        COMPLETION_PHASE.put("COMPLETION", "完工提交");
        COMPLETION_PHASE.put("ACCEPTED", "验收通过");
        COMPLETION_PHASE.put("REJECTED", "退回返修");
    }

    /** 押金至少是日租金的几倍 */
    public static final int DEPOSIT_RENT_MULTIPLE = 2;

    /** 逾期违约金 = 日租金 × 逾期天数 × 该倍率 */
    public static final BigDecimal OVERDUE_MULTIPLE = new BigDecimal("1.5");

    /** 单次租期上限（天） */
    public static final int MAX_RENT_DAYS = 90;

    // ---------------- 学员与课程 ----------------
    private static final Map<String, String> STUDENT_LEVEL = new LinkedHashMap<>();

    static {
        STUDENT_LEVEL.put("BEGINNER", "入门");
        STUDENT_LEVEL.put("INTERMEDIATE", "进阶");
        STUDENT_LEVEL.put("ADVANCED", "高级");
    }

    private static final Map<String, String> COURSE_STATUS = new LinkedHashMap<>();

    static {
        COURSE_STATUS.put("OPEN", "开放报名");
        COURSE_STATUS.put("CLOSED", "已停开");
    }

    private static final Map<String, String> ENROLLMENT_STATUS = new LinkedHashMap<>();

    static {
        ENROLLMENT_STATUS.put("NORMAL", "正常");
        ENROLLMENT_STATUS.put("CANCELED", "已退课");
    }

    /** 一键多科报名时，每门课默认消耗的课时 */
    public static final int DEFAULT_BATCH_LESSONS = 2;

    /** 一键多科一次最多选几门课 */
    public static final int MAX_BATCH_COURSES = 3;

    // ---------------- 取值方法 ----------------

    public static boolean isValidCategory(String category) {
        return category != null && CATEGORY.containsKey(category);
    }

    public static String categoryName(String category) {
        return category == null ? "" : CATEGORY.getOrDefault(category, category);
    }

    public static boolean isValidInstrumentStatus(String status) {
        return status != null && INSTRUMENT_STATUS.containsKey(status);
    }

    public static String instrumentStatusName(String status) {
        return status == null ? "" : INSTRUMENT_STATUS.getOrDefault(status, status);
    }

    /**
     * 乐器状态能否流转；不允许时返回中文原因，允许时返回 null。
     */
    public static String instrumentTransferReject(String from, String to) {
        if (!isValidInstrumentStatus(to)) {
            return "目标状态不合法：" + to;
        }
        if (from == null || !INSTRUMENT_STATUS.containsKey(from)) {
            return "乐器当前状态不合法：" + from;
        }
        if ("SCRAPPED".equals(from)) {
            return "乐器已报废，报废是终态，状态不能再变更";
        }
        if (from.equals(to)) {
            return "乐器已经是【" + instrumentStatusName(from) + "】状态，无需变更";
        }
        if (!INSTRUMENT_FLOW.getOrDefault(from, Set.of()).contains(to)) {
            return "【" + instrumentStatusName(from) + "】不能直接变更为【" + instrumentStatusName(to) + "】";
        }
        return null;
    }

    public static String techLevelName(String level) {
        return level == null ? "" : TECH_LEVEL.getOrDefault(level, level);
    }

    public static String techStatusName(String status) {
        return status == null ? "" : TECH_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidTechStatus(String status) {
        return status != null && TECH_STATUS.containsKey(status);
    }

    public static boolean isValidServiceType(String type) {
        return type != null && SERVICE_TYPE.containsKey(type);
    }

    public static String serviceTypeName(String type) {
        return type == null ? "" : SERVICE_TYPE.getOrDefault(type, type);
    }

    public static String serviceTypeOptions() {
        return String.join("、", SERVICE_TYPE.keySet());
    }

    public static boolean isValidServiceStatus(String status) {
        return status != null && SERVICE_STATUS.containsKey(status);
    }

    public static String serviceStatusName(String status) {
        return status == null ? "" : SERVICE_STATUS.getOrDefault(status, status);
    }

    /**
     * 服务单状态能否流转；不允许时返回中文原因，允许时返回 null。
     */
    public static String serviceTransferReject(String from, String to) {
        if (!isValidServiceStatus(to)) {
            return "目标状态不合法：" + to;
        }
        if (from == null || !SERVICE_STATUS.containsKey(from)) {
            return "服务单当前状态不合法：" + from;
        }
        if (!SERVICE_FLOW.getOrDefault(from, Set.of()).contains(to)) {
            return "服务单状态【" + serviceStatusName(from) + "】不能变更为【" + serviceStatusName(to) + "】";
        }
        return null;
    }

    public static String rentalStatusName(String status) {
        return status == null ? "" : RENTAL_STATUS.getOrDefault(status, status);
    }

    public static String completionPhaseName(String phase) {
        return phase == null ? "" : COMPLETION_PHASE.getOrDefault(phase, phase);
    }

    /** 维修类（REPAIR 维修 / OVERHAUL 大修）才联动乐器到「维修中 / 恢复在库」；调律不动乐器状态 */
    public static boolean isRepairKind(String serviceType) {
        return "REPAIR".equals(serviceType) || "OVERHAUL".equals(serviceType);
    }

    public static boolean isValidRentalStatus(String status) {
        return status != null && RENTAL_STATUS.containsKey(status);
    }

    public static String studentLevelName(String level) {
        return level == null ? "" : STUDENT_LEVEL.getOrDefault(level, level);
    }

    public static boolean isValidStudentLevel(String level) {
        return level != null && STUDENT_LEVEL.containsKey(level);
    }

    public static String courseStatusName(String status) {
        return status == null ? "" : COURSE_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidCourseStatus(String status) {
        return status != null && COURSE_STATUS.containsKey(status);
    }

    public static String enrollmentStatusName(String status) {
        return status == null ? "" : ENROLLMENT_STATUS.getOrDefault(status, status);
    }

    /** 供前端下拉直接使用的选项列表 */
    public static List<Map<String, String>> options(Map<String, String> source) {
        return source.entrySet().stream()
                .map(e -> Map.of("value", e.getKey(), "label", e.getValue()))
                .toList();
    }

    public static List<Map<String, String>> categoryOptions() {
        return options(CATEGORY);
    }

    public static List<Map<String, String>> instrumentStatusOptions() {
        return options(INSTRUMENT_STATUS);
    }

    public static List<Map<String, String>> serviceTypeOptionsData() {
        return options(SERVICE_TYPE);
    }

    public static List<Map<String, String>> serviceStatusOptions() {
        return options(SERVICE_STATUS);
    }

    public static List<Map<String, String>> rentalStatusOptions() {
        return options(RENTAL_STATUS);
    }

    public static List<Map<String, String>> techStatusOptions() {
        return options(TECH_STATUS);
    }

    public static List<Map<String, String>> studentLevelOptions() {
        return options(STUDENT_LEVEL);
    }

    public static List<Map<String, String>> courseStatusOptions() {
        return options(COURSE_STATUS);
    }
}
