package com.music.instrument.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.music.instrument.common.ApiResponse;
import com.music.instrument.domain.Instrument;
import com.music.instrument.dto.InstrumentCreateRequest;
import com.music.instrument.dto.StatusChangeRequest;
import com.music.instrument.dto.view.InstrumentHistoryView;
import com.music.instrument.service.InstrumentService;

/**
 * 乐器台账接口。
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.success(instrumentService.options());
    }

    @GetMapping
    public ApiResponse<List<Instrument>> list(@RequestParam(required = false) String category,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.success(instrumentService.list(category, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Instrument> detail(@PathVariable Long id) {
        return ApiResponse.success(instrumentService.detail(id));
    }

    /** 主表格行内展开：这台乐器的租赁历史 + 维修历史 */
    @GetMapping("/{id}/history")
    public ApiResponse<InstrumentHistoryView> history(@PathVariable Long id) {
        return ApiResponse.success(instrumentService.history(id));
    }

    /**
     * 批量行内展开：一次展开多台乐器时一个请求取回全部历史，
     * 后端按 id 集合批量装配，查询次数不随乐器台数或历史条数增长。
     */
    @GetMapping("/history")
    public ApiResponse<List<InstrumentHistoryView>> histories(@RequestParam List<Long> ids) {
        return ApiResponse.success(instrumentService.histories(ids));
    }

    @PostMapping
    public ApiResponse<Instrument> create(@RequestBody InstrumentCreateRequest request) {
        return ApiResponse.success("入库成功", instrumentService.create(request));
    }

    /** 状态机流转：在库 / 租出 / 维修中 / 报废（报废为终态） */
    @PostMapping("/{id}/status")
    public ApiResponse<Instrument> changeStatus(@PathVariable Long id,
                                                @RequestBody StatusChangeRequest request) {
        String status = request == null ? null : request.getStatus();
        return ApiResponse.success("状态已更新", instrumentService.changeStatus(id, status));
    }
}
