package com.music.instrument.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.music.instrument.common.BizException;
import com.music.instrument.domain.Instrument;
import com.music.instrument.dto.InstrumentCreateRequest;
import com.music.instrument.dto.view.InstrumentHistoryView;
import com.music.instrument.dto.view.RentalView;
import com.music.instrument.dto.view.ServiceView;
import com.music.instrument.mapper.InstrumentMapper;
import com.music.instrument.mapper.RentalOrderMapper;
import com.music.instrument.mapper.ServiceOrderMapper;

/**
 * 乐器台账：入库、分类/状态筛选、状态机流转、行内展开的租赁+维修历史。
 */
@Service
public class InstrumentService {

    private final InstrumentMapper instrumentMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final ServiceOrderMapper serviceOrderMapper;
    private final ServiceOrderService serviceOrderService;
    private final RentalHistoryAssembler rentalHistoryAssembler;

    public InstrumentService(InstrumentMapper instrumentMapper,
                             RentalOrderMapper rentalOrderMapper,
                             ServiceOrderMapper serviceOrderMapper,
                             ServiceOrderService serviceOrderService,
                             RentalHistoryAssembler rentalHistoryAssembler) {
        this.instrumentMapper = instrumentMapper;
        this.rentalOrderMapper = rentalOrderMapper;
        this.serviceOrderMapper = serviceOrderMapper;
        this.serviceOrderService = serviceOrderService;
        this.rentalHistoryAssembler = rentalHistoryAssembler;
    }

    public Map<String, Object> options() {
        return Map.of(
                "categories", MusicDict.categoryOptions(),
                "statuses", MusicDict.instrumentStatusOptions());
    }

    public List<Instrument> list(String category, String status) {
        boolean noFilter = (category == null || category.isEmpty()) && (status == null || status.isEmpty());
        if (noFilter) {
            return instrumentMapper.findAll();
        }
        return instrumentMapper.findByCategoryAndStatus(category, status);
    }

    public Instrument detail(Long id) {
        if (id == null) {
            throw new BizException("请指定要查看的乐器");
        }
        Instrument instrument = instrumentMapper.findById(id);
        if (instrument == null) {
            throw new BizException("乐器不存在，无法查看");
        }
        return instrument;
    }

    /**
     * 行内展开详情：该乐器的全部租赁记录 + 全部服务单。
     * 单行入口也走批量装配，保证单展 / 多展 / 租赁列表三处只有一套查询与计算。
     */
    public InstrumentHistoryView history(Long id) {
        if (id == null) {
            throw new BizException("请指定要展开的乐器");
        }
        return batchHistories(List.of(id)).get(0);
    }

    /**
     * 多台乐器一次展开：租赁记录、服务单、完工轮次全部按 IN (...) 批量取回，
     * 绝不按订单 / 按乐器逐条追加查询（查询次数与历史条数无关）。
     * 排序固定为租期开始日倒序、同日按订单 id 倒序（在 SQL 层统一保证）。
     */
    public List<InstrumentHistoryView> batchHistories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请指定要展开的乐器");
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<Instrument> instruments = instrumentMapper.findByIds(distinctIds);
        if (instruments.isEmpty()) {
            throw new BizException("乐器不存在，无法展开历史");
        }
        LocalDate today = LocalDate.now();

        // 一次查询取齐所有乐器的租赁记录，统一装配后按乐器分组
        List<RentalView> rentals = rentalOrderMapper.listByInstrumentIds(distinctIds);
        rentalHistoryAssembler.assembleAll(rentals, today);
        Map<Long, List<RentalView>> rentalsByInstrument = new LinkedHashMap<>();
        for (RentalView rental : rentals) {
            rentalsByInstrument
                    .computeIfAbsent(rental.getInstrumentId(), k -> new ArrayList<>())
                    .add(rental);
        }

        // 一次查询取齐所有乐器的服务单，再批量补齐轮次（维修页同款装配）
        List<ServiceView> services = serviceOrderMapper.listByInstrumentIds(distinctIds);
        serviceOrderService.attachRounds(services);
        Map<Long, List<ServiceView>> servicesByInstrument = new LinkedHashMap<>();
        for (ServiceView service : services) {
            service.setServiceTypeName(MusicDict.serviceTypeName(service.getServiceType()));
            service.setStatusName(MusicDict.serviceStatusName(service.getStatus()));
            servicesByInstrument
                    .computeIfAbsent(service.getInstrumentId(), k -> new ArrayList<>())
                    .add(service);
        }

        List<InstrumentHistoryView> result = new ArrayList<>(instruments.size());
        for (Instrument instrument : instruments) {
            InstrumentHistoryView view = new InstrumentHistoryView();
            view.setInstrument(instrument);
            view.setRentals(rentalsByInstrument.getOrDefault(instrument.getId(), List.of()));
            view.setServices(servicesByInstrument.getOrDefault(instrument.getId(), List.of()));
            result.add(view);
        }
        return result;
    }

    public Instrument create(InstrumentCreateRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        String instrumentNo = trim(request.getInstrumentNo());
        if (instrumentNo == null) {
            throw new BizException("请填写乐器编号");
        }
        String name = trim(request.getName());
        if (name == null) {
            throw new BizException("请填写乐器名称");
        }
        if (!MusicDict.isValidCategory(request.getCategory())) {
            throw new BizException("乐器分类不合法：" + request.getCategory()
                    + "，只能是 GUITAR / PIANO / WIND / STRING / PERCUSSION");
        }
        BigDecimal dailyRent = request.getDailyRent();
        if (dailyRent == null || dailyRent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("日租金必须大于 0");
        }
        if (instrumentMapper.countByNo(instrumentNo) > 0) {
            throw new BizException("乐器编号【" + instrumentNo + "】已存在，请换一个编号");
        }

        LocalDateTime now = LocalDateTime.now();
        Instrument instrument = new Instrument();
        instrument.setInstrumentNo(instrumentNo);
        instrument.setName(name);
        instrument.setCategory(request.getCategory());
        instrument.setBrand(trim(request.getBrand()));
        instrument.setDailyRent(dailyRent);
        // 默认值只在 service 里落地，实体保持「没传就是 null」
        instrument.setStatus("IN_STOCK");
        instrument.setPurchaseDate(request.getPurchaseDate());
        instrument.setNote(trim(request.getNote()));
        instrument.setCreatedAt(now);
        instrument.setUpdatedAt(now);

        instrumentMapper.insert(instrument);
        return instrument;
    }

    public Instrument changeStatus(Long id, String targetStatus) {
        if (id == null) {
            throw new BizException("请指定要变更状态的乐器");
        }
        Instrument instrument = instrumentMapper.findById(id);
        if (instrument == null) {
            throw new BizException("乐器不存在，无法变更状态");
        }
        String reject = MusicDict.instrumentTransferReject(instrument.getStatus(), targetStatus);
        if (reject != null) {
            throw new BizException("乐器【" + instrument.getInstrumentNo() + " " + instrument.getName() + "】"
                    + reject);
        }
        instrumentMapper.updateStatus(id, targetStatus, LocalDateTime.now());
        instrument.setStatus(targetStatus);
        instrument.setUpdatedAt(LocalDateTime.now());
        return instrument;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
