-- ============================================================================
-- 知音琴行 · 乐器台账与租赁管理系统 —— 建表 + 种子数据
-- 库名 music_instrument，字符集 utf8mb4。
--
-- 本文件同时被 mysql 镜像在构建期导入（mysql/Dockerfile 会 COPY schema.sql），
-- 所以 backend/src/main/resources/schema.sql 与 mysql/schema.sql 必须保持一致。
--
-- 种子数据口径：下面列出的每一条业务规则，都能用现成数据直接触发，不存在永远排不到的规则。
--   乐器台账  编号唯一 / 分类受控 / 状态机（在库·租出·维修·报废，报废为终态）
--   租赁订单  租期与押金合法 / 同一乐器同时间段不能重复租 / 维修中禁租 / 报废禁租 / 逾期归还计费
--   调律维修  同调律师同时段不重叠 / 休假调律师禁派单 / 已报废乐器禁派单 / 服务单状态单向推进
--             完工提交→待验收→验收通过(完成) / 退回返修(返修中)→再次提交，按轮次留痕
--   学员课程  课时余额不足禁约 / 满员拦 / 未购课（余额为 0）禁约 / 已停开课程禁约 / 同课不重复约
-- ============================================================================

CREATE DATABASE IF NOT EXISTS music_instrument
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE music_instrument;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS lesson_course;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS service_completion;
DROP TABLE IF EXISTS service_order;
DROP TABLE IF EXISTS technician;
DROP TABLE IF EXISTS rental_order;
DROP TABLE IF EXISTS instrument;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 乐器台账主表
-- ---------------------------------------------------------------------------
CREATE TABLE instrument (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    instrument_no  VARCHAR(32)   NOT NULL COMMENT '乐器编号，全店唯一',
    name           VARCHAR(64)   NOT NULL COMMENT '乐器名称',
    category       VARCHAR(16)   NOT NULL COMMENT '分类：GUITAR 吉他 / PIANO 键盘钢琴 / WIND 管乐 / STRING 弦乐 / PERCUSSION 打击乐',
    brand          VARCHAR(32)            COMMENT '品牌',
    daily_rent     DECIMAL(10, 2) NOT NULL COMMENT '日租金（元）',
    status         VARCHAR(16)   NOT NULL COMMENT '状态机：IN_STOCK 在库 / RENTED 租出 / REPAIRING 维修中 / SCRAPPED 报废（终态）',
    purchase_date  DATE                   COMMENT '购入日期',
    note           VARCHAR(255)           COMMENT '备注',
    created_at     DATETIME               COMMENT '创建时间',
    updated_at     DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_instrument_no (instrument_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '乐器台账';

-- ---------------------------------------------------------------------------
-- 调律师/维修师
-- ---------------------------------------------------------------------------
CREATE TABLE technician (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    tech_no    VARCHAR(32) NOT NULL COMMENT '调律师工号，唯一',
    name       VARCHAR(32) NOT NULL COMMENT '姓名',
    level      VARCHAR(16) NOT NULL COMMENT '等级：JUNIOR 初级 / SENIOR 高级 / MASTER 首席',
    phone      VARCHAR(20)          COMMENT '手机号',
    status     VARCHAR(16) NOT NULL COMMENT '在岗状态：ACTIVE 在岗 / LEAVE 休假 / OFF 离职',
    created_at DATETIME             COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_technician_no (tech_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '调律师';

-- ---------------------------------------------------------------------------
-- 学员
-- ---------------------------------------------------------------------------
CREATE TABLE student (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    student_no     VARCHAR(32) NOT NULL COMMENT '学号，唯一',
    name           VARCHAR(32) NOT NULL COMMENT '姓名',
    phone          VARCHAR(20) NOT NULL COMMENT '手机号，唯一',
    level          VARCHAR(16) NOT NULL COMMENT '程度：BEGINNER 入门 / INTERMEDIATE 进阶 / ADVANCED 高级',
    lesson_balance INT         NOT NULL COMMENT '课时余额（节课）；为 0 视为未购课',
    created_at     DATETIME             COMMENT '创建时间',
    updated_at     DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no),
    UNIQUE KEY uk_student_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '学员';

-- ---------------------------------------------------------------------------
-- 租赁订单
-- ---------------------------------------------------------------------------
CREATE TABLE rental_order (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    order_no      VARCHAR(32)   NOT NULL COMMENT '订单号，唯一',
    instrument_id BIGINT        NOT NULL COMMENT '外键：乐器',
    student_id    BIGINT        NOT NULL COMMENT '外键：承租学员',
    start_date    DATE          NOT NULL COMMENT '租期开始日',
    end_date      DATE          NOT NULL COMMENT '租期结束日',
    deposit       DECIMAL(10, 2) NOT NULL COMMENT '押金（元）',
    daily_rent    DECIMAL(10, 2) NOT NULL COMMENT '下单时的日租金快照（逾期计费用）',
    rent_amount   DECIMAL(10, 2) NOT NULL COMMENT '租金合计',
    status        VARCHAR(16)   NOT NULL COMMENT 'BOOKED 已预约 / RENTING 租用中 / RETURNED 已归还 / OVERDUE 逾期归还',
    return_date   DATE                   COMMENT '实际归还日，未归还为 NULL',
    overdue_fee   DECIMAL(10, 2)         COMMENT '逾期违约金',
    created_at    DATETIME               COMMENT '创建时间',
    updated_at    DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rental_order_no (order_no),
    KEY idx_rental_instrument (instrument_id, start_date, end_date),
    CONSTRAINT fk_rental_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT fk_rental_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '租赁订单';

-- ---------------------------------------------------------------------------
-- 调律/维修服务单
-- ---------------------------------------------------------------------------
CREATE TABLE service_order (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    service_no    VARCHAR(32)   NOT NULL COMMENT '服务单号，唯一',
    instrument_id BIGINT        NOT NULL COMMENT '外键：乐器',
    technician_id BIGINT        NOT NULL COMMENT '外键：调律师',
    service_type  VARCHAR(16)   NOT NULL COMMENT 'TUNING 调律 / REPAIR 维修 / OVERHAUL 大修',
    plan_start    DATETIME      NOT NULL COMMENT '计划开始时间',
    plan_end      DATETIME      NOT NULL COMMENT '计划结束时间',
    fee           DECIMAL(10, 2) NOT NULL COMMENT '服务费（元）',
    status        VARCHAR(16)   NOT NULL COMMENT 'PLANNED 已排期 / DOING 进行中 / PENDING_ACCEPT 待验收 / REWORKING 返修中 / DONE 已完成 / CANCELED 已取消',
    current_round INT           NOT NULL DEFAULT 0 COMMENT '当前验收轮次：每提交一次完工 +1，验收通过/返修都围绕该轮次，只增不改',
    instrument_snapshot VARCHAR(16)       COMMENT '维修类开始施工时的乐器原状态（如 IN_STOCK），最终验收通过后按此恢复；调律单为 NULL',
    result_note   VARCHAR(255)           COMMENT '结果备注（仅兼容历史单，新流程的过程记录全部落在 service_completion）',
    created_at    DATETIME               COMMENT '创建时间',
    updated_at    DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_no (service_no),
    KEY idx_service_technician (technician_id, plan_start, plan_end),
    CONSTRAINT fk_service_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT fk_service_technician FOREIGN KEY (technician_id) REFERENCES technician (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '调律维修服务单';

-- ---------------------------------------------------------------------------
-- 完工 / 验收轮次记录（一服务单可有多轮，永远只追加、不覆盖）
-- 每一轮：调律师提交一条 COMPLETION（submitted），店员随后补一条 ACCEPTED / REJECTED（decided）。
-- uk_order_round_phase 保证同一轮同一阶段只有一条：连点提交 / 并发相反结论只会有一个落库。
-- ---------------------------------------------------------------------------
CREATE TABLE service_completion (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    service_order_id  BIGINT       NOT NULL COMMENT '外键：服务单',
    round_no          INT          NOT NULL COMMENT '第几轮验收，从 1 开始',
    phase             VARCHAR(16)  NOT NULL COMMENT 'COMPLETION 调律师完工提交 / ACCEPTED 验收通过 / REJECTED 退回返修',
    process_note      VARCHAR(500)          COMMENT '本轮处理内容（完工时填写）',
    check_result      VARCHAR(500)          COMMENT '检测结果（完工时填写）',
    delivery_note     VARCHAR(500)          COMMENT '交付说明（完工时填写）',
    remaining_fault   VARCHAR(500)          COMMENT '仍存在的故障（退回返修时必填）',
    rework_request    VARCHAR(500)          COMMENT '返修要求（退回返修时必填）',
    operator_name     VARCHAR(32)           COMMENT '操作人（提交调律师 / 验收店员，按当时外键名称留痕）',
    created_at        DATETIME     NOT NULL COMMENT '记录时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_round_phase (service_order_id, round_no, phase),
    KEY idx_completion_round (service_order_id, round_no, id),
    CONSTRAINT fk_completion_order FOREIGN KEY (service_order_id) REFERENCES service_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '完工验收轮次记录';

-- ---------------------------------------------------------------------------
-- 课程
-- ---------------------------------------------------------------------------
CREATE TABLE lesson_course (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    course_no      VARCHAR(32)   NOT NULL COMMENT '课程编号，唯一',
    title          VARCHAR(64)   NOT NULL COMMENT '课程名称',
    teacher_name   VARCHAR(32)   NOT NULL COMMENT '授课教师',
    category       VARCHAR(16)   NOT NULL COMMENT '对应乐器分类',
    capacity       INT           NOT NULL COMMENT '容量上限',
    enrolled_count INT           NOT NULL COMMENT '已报名人数',
    price          DECIMAL(10, 2) NOT NULL COMMENT '课时包价格（元）',
    status         VARCHAR(16)   NOT NULL COMMENT 'OPEN 开放报名 / CLOSED 已停开',
    created_at     DATETIME               COMMENT '创建时间',
    updated_at     DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_no (course_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '课程';

-- ---------------------------------------------------------------------------
-- 约课记录
-- ---------------------------------------------------------------------------
CREATE TABLE enrollment (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    student_id  BIGINT   NOT NULL COMMENT '外键：学员',
    course_id   BIGINT   NOT NULL COMMENT '外键：课程',
    enroll_date DATE     NOT NULL COMMENT '约课日期',
    lessons     INT      NOT NULL COMMENT '本次消耗课时数',
    status      VARCHAR(16) NOT NULL COMMENT 'NORMAL 正常 / CANCELED 已退课',
    created_at  DATETIME          COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_enrollment_student_course (student_id, course_id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES lesson_course (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '约课记录';

-- ============================================================================
-- 种子数据
-- ============================================================================

INSERT INTO instrument
    (id, instrument_no, name, category, brand, daily_rent, status, purchase_date, note, created_at, updated_at)
VALUES
    (1, 'G-1001', '民谣吉他 D-28', 'GUITAR', '森韵', 30.00, 'IN_STOCK', '2023-03-12', '面板有轻微使用痕迹', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (2, 'G-1002', '古典吉他 CG-180', 'GUITAR', '森韵', 45.00, 'IN_STOCK', '2023-06-20', '适合入门学员', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (3, 'P-2001', '立式钢琴 UP-121', 'PIANO', '海韵', 200.00, 'IN_STOCK', '2022-09-01', '每年需调律两次', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (4, 'P-2002', '三角钢琴 GP-186', 'PIANO', '海韵', 500.00, 'IN_STOCK', '2021-11-15', '演出厅专用', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (5, 'W-3001', '长笛 FL-211', 'WIND', '云笛', 60.00, 'RENTED', '2024-02-08', '租出中，承租学员苏念安', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (6, 'W-3002', '中音萨克斯 AS-380', 'WIND', '云笛', 120.00, 'REPAIRING', '2023-08-30', '按键漏气，维修中', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (7, 'S-4001', '小提琴 VN-4/4', 'STRING', '木言', 80.00, 'RENTED', '2022-05-18', '租出中', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (8, 'S-4002', '大提琴 VC-3/4', 'STRING', '木言', 150.00, 'REPAIRING', '2022-05-18', '琴马开胶返修中，第二轮处理', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (9, 'D-5001', '架子鼓 TD-5 五鼓', 'PERCUSSION', '节律', 180.00, 'SCRAPPED', '2018-04-01', '鼓腔开裂，已报废', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (10, 'D-5002', '电子琴 EK-61', 'PERCUSSION', '节律', 40.00, 'REPAIRING', '2024-07-22', '琴键接触不良，已完工待验收', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
    (11, 'U-6001', '尤克里里 UK-S', 'GUITAR', '木言', 25.00, 'IN_STOCK', '2026-05-06', '从未出租：展开历史应为空', '2026-05-06 09:00:00', '2026-05-06 09:00:00');

INSERT INTO technician (id, tech_no, name, level, phone, status, created_at)
VALUES
    (1, 'T-01', '卫子期', 'MASTER', '13800000001', 'ACTIVE', '2026-01-05 09:00:00'),
    (2, 'T-02', '周慕青', 'SENIOR', '13800000002', 'ACTIVE', '2026-01-05 09:00:00'),
    (3, 'T-03', '陈砚舟', 'JUNIOR', '13800000003', 'LEAVE', '2026-01-05 09:00:00'),
    (4, 'T-04', '林知远', 'SENIOR', '13800000004', 'ACTIVE', '2026-01-05 09:00:00');

INSERT INTO student
    (id, student_no, name, phone, level, lesson_balance, created_at, updated_at)
VALUES
    (1, 'ST-8001', '韩青梧', '13900000001', 'INTERMEDIATE', 12, '2026-01-06 09:00:00', '2026-01-06 09:00:00'),
    (2, 'ST-8002', '沈砚辞', '13900000002', 'BEGINNER', 3, '2026-01-06 09:00:00', '2026-01-06 09:00:00'),
    (3, 'ST-8003', '温叙白', '13900000003', 'ADVANCED', 24, '2026-01-06 09:00:00', '2026-01-06 09:00:00'),
    (4, 'ST-8004', '梁听山', '13900000004', 'BEGINNER', 0, '2026-01-06 09:00:00', '2026-01-06 09:00:00'),
    (5, 'ST-8005', '苏念安', '13900000005', 'INTERMEDIATE', 6, '2026-01-06 09:00:00', '2026-01-06 09:00:00');

-- 租赁订单：G-1001 在 09-01~09-30 已被租出 → 同乐器时段重叠可直接触发
--           W-3001 09-15~09-20 逾期未还 → 归还计费可直接触发
--           P-2002 08-01~08-20 已逾期归还 6 天 → 逾期违约金样例
-- #7~#10 为「租赁列表 ↔ 乐器历史同口径」演示数据（当前日期 2026-09-26）：
--   #7  跨年按期归还：旧 RETURNED，实际归还日=结束日，31 天、逾期 0（结束日当天不算逾期）
--   #8  跨月逾期归还：旧 OVERDUE 且带实际归还日，按已归还历史解释，30 天、逾期 6 天、保留原费用 360
--   #9  未来开始的 BOOKED：起租日在未来，不能提前出现逾期，租期 9 天
--   #10 逾期未归还：旧 RENTING 且无实际归还日（历史脏状态），按「逾期中」解释，20 天、试算 1440
--        （日租快照 160 与现日租 60 不同，证明逾期费用按订单快照而非当前日租计算）
INSERT INTO rental_order
    (id, order_no, instrument_id, student_id, start_date, end_date, deposit, daily_rent, rent_amount, status, return_date, overdue_fee, created_at, updated_at)
VALUES
    (1, 'R-20260901-001', 1, 1, '2026-09-01', '2026-09-30', 200.00, 30.00, 900.00, 'RENTING', NULL, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
    (2, 'R-20260801-001', 4, 2, '2026-08-01', '2026-08-20', 1500.00, 500.00, 10000.00, 'OVERDUE', '2026-08-26', 4500.00, '2026-08-01 10:00:00', '2026-08-26 10:00:00'),
    (3, 'R-20260925-001', 2, 3, '2026-09-25', '2026-10-08', 300.00, 45.00, 630.00, 'BOOKED', NULL, NULL, '2026-09-20 10:00:00', '2026-09-20 10:00:00'),
    (4, 'R-20260910-001', 7, 1, '2026-09-10', '2026-09-28', 400.00, 80.00, 1520.00, 'RENTING', NULL, NULL, '2026-09-10 10:00:00', '2026-09-10 10:00:00'),
    (5, 'R-20260915-001', 5, 5, '2026-09-15', '2026-09-20', 300.00, 60.00, 360.00, 'RENTING', NULL, NULL, '2026-09-15 10:00:00', '2026-09-15 10:00:00'),
    (6, 'R-20260501-001', 1, 2, '2026-05-01', '2026-05-10', 200.00, 30.00, 300.00, 'RETURNED', '2026-05-10', 0.00, '2026-05-01 10:00:00', '2026-05-10 10:00:00'),
    (7, 'R-20251220-001', 3, 1, '2025-12-20', '2026-01-19', 800.00, 200.00, 6200.00, 'RETURNED', '2026-01-19', 0.00, '2025-12-20 10:00:00', '2026-01-19 10:00:00'),
    (8, 'R-20260601-001', 4, 3, '2026-06-01', '2026-06-30', 600.00, 40.00, 1200.00, 'OVERDUE', '2026-07-06', 360.00, '2026-06-01 10:00:00', '2026-07-06 10:00:00'),
    (9, 'R-20261001-001', 2, 4, '2026-10-01', '2026-10-09', 200.00, 45.00, 405.00, 'BOOKED', NULL, NULL, '2026-09-20 10:00:00', '2026-09-20 10:00:00'),
    (10, 'R-20260901-002', 5, 2, '2026-09-01', '2026-09-20', 200.00, 160.00, 3200.00, 'RENTING', NULL, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

-- 服务单：T-01 在 09-22 09:00~12:00 已排单 → 同时段重叠可直接触发
--         D-5001 已报废仍有历史单 → 报废乐器禁派单可直接触发
-- 新验收链路现成数据：
--   #6  D-5002 PENDING_ACCEPT 已完工待验收 → 店员可直接「验收通过」或「退回返修」
--   #5  S-4002 REWORKING      第 1 轮被退回 → 调律师可直接「再次提交完工」，两轮记录均在
INSERT INTO service_order
    (id, service_no, instrument_id, technician_id, service_type, plan_start, plan_end, fee,
     status, current_round, instrument_snapshot, result_note, created_at, updated_at)
VALUES
    (1, 'SV-20260922-001', 6, 1, 'REPAIR', '2026-09-22 09:00:00', '2026-09-22 12:00:00', 380.00, 'DOING', 0, 'IN_STOCK', '更换软木垫并调整按键', '2026-09-21 09:00:00', '2026-09-22 09:10:00'),
    (2, 'SV-20260923-001', 3, 2, 'TUNING', '2026-09-23 14:00:00', '2026-09-23 15:30:00', 260.00, 'PLANNED', 0, NULL, NULL, '2026-09-21 09:00:00', '2026-09-21 09:00:00'),
    (3, 'SV-20260801-001', 9, 4, 'OVERHAUL', '2026-08-01 09:00:00', '2026-08-05 18:00:00', 1200.00, 'DONE', 0, NULL, '鼓腔开裂无法修复，判定报废', '2026-07-30 09:00:00', '2026-08-05 18:00:00'),
    (4, 'SV-20260910-001', 1, 1, 'TUNING', '2026-09-10 10:00:00', '2026-09-10 11:00:00', 180.00, 'DONE', 0, NULL, '音准已校准', '2026-09-09 09:00:00', '2026-09-10 11:00:00'),
    (5, 'SV-20260925-001', 8, 2, 'REPAIR', '2026-09-25 09:00:00', '2026-09-25 17:00:00', 640.00, 'REWORKING', 1, 'IN_STOCK', '琴马开胶返修', '2026-09-21 09:00:00', '2026-09-25 16:40:00'),
    (6, 'SV-20260926-001', 10, 4, 'REPAIR', '2026-09-26 09:00:00', '2026-09-26 12:00:00', 220.00, 'PENDING_ACCEPT', 1, 'IN_STOCK', NULL, '2026-09-24 09:00:00', '2026-09-26 11:30:00');

-- 完工 / 验收轮次记录（按服务单、轮次、时间排序，可完整还原过程）
INSERT INTO service_completion
    (id, service_order_id, round_no, phase, process_note, check_result, delivery_note,
     remaining_fault, rework_request, operator_name, created_at)
VALUES
    -- #5 第 1 轮：完工提交后被店员退回返修（现成的返修数据，原完工记录与退回结论都保留）
    (1, 5, 1, 'COMPLETION', '拆下琴马重新胶合，清理指板并更换一套琴弦',
     '琴马已复位，空弦音准正常', '可装盒交付，建议一周内复查一次',
     NULL, NULL, '周慕青', '2026-09-25 15:20:00'),
    (2, 5, 1, 'REJECTED', NULL, NULL, NULL,
     'C 弦高把位仍有轻微杂音，琴马右脚与面板贴合不密实',
     '重新贴合琴马右脚并校平，复检 C 弦全部把位后再次提交',
     '店员·许棠', '2026-09-25 16:40:00'),
    -- #6 第 1 轮：已提交完工，等待店员验收（验收待办）
    (3, 6, 1, 'COMPLETION', '拆开琴体清洁导电橡胶，更换 3 个失灵按键的触点并重新装配',
     '61 键逐一试弹全部发声，力度感应正常，连续弹奏 30 分钟无复现故障',
     '已装回原包装，附 30 天同类故障保修说明',
     NULL, NULL, '林知远', '2026-09-26 11:30:00');

-- 课程：C-6002 已满员（6/6）→ 满员拦可直接触发
--       C-6004 已停开 → 停开禁约可直接触发
INSERT INTO lesson_course
    (id, course_no, title, teacher_name, category, capacity, enrolled_count, price, status, created_at, updated_at)
VALUES
    (1, 'C-6001', '钢琴入门小组课', '沈翊', 'PIANO', 4, 3, 800.00, 'OPEN', '2026-01-06 09:00:00', '2026-09-05 09:00:00'),
    (2, 'C-6002', '吉他弹唱进阶班', '苏晚', 'GUITAR', 6, 6, 960.00, 'OPEN', '2026-01-06 09:00:00', '2026-09-08 09:00:00'),
    (3, 'C-6003', '长笛独奏精修', '叶知秋', 'WIND', 2, 1, 1200.00, 'OPEN', '2026-01-06 09:00:00', '2026-09-05 09:00:00'),
    (4, 'C-6004', '小提琴合奏团', '顾南野', 'STRING', 8, 2, 1080.00, 'CLOSED', '2026-01-06 09:00:00', '2026-08-20 09:00:00'),
    (5, 'C-6005', '架子鼓节奏营', '白鹭', 'PERCUSSION', 5, 0, 720.00, 'OPEN', '2026-01-06 09:00:00', '2026-01-06 09:00:00');

INSERT INTO enrollment (id, student_id, course_id, enroll_date, lessons, status, created_at)
VALUES
    (1, 1, 1, '2026-09-01', 4, 'NORMAL', '2026-09-01 09:30:00'),
    (2, 3, 3, '2026-09-05', 6, 'NORMAL', '2026-09-05 09:30:00'),
    (3, 2, 5, '2026-09-08', 2, 'NORMAL', '2026-09-08 09:30:00');
