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
import com.music.instrument.dto.RentalCreateRequest;
import com.music.instrument.dto.ReturnRequest;
import com.music.instrument.dto.view.RentalView;
import com.music.instrument.service.RentalService;

/**
 * 租赁订单接口。
 */
@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final RentalService rentalService;

    public RentalController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.success(rentalService.options());
    }

    @GetMapping
    public ApiResponse<List<RentalView>> list(@RequestParam(required = false) List<Long> instrumentIds,
                                             @RequestParam(required = false) String status) {
        return ApiResponse.success(rentalService.list(instrumentIds, status));
    }

    @PostMapping
    public ApiResponse<RentalView> create(@RequestBody RentalCreateRequest request) {
        return ApiResponse.success("开单成功", rentalService.create(request));
    }

    /** 办理归还：逾期归还自动计违约金 */
    @PostMapping("/{id}/return")
    public ApiResponse<RentalView> returnOrder(@PathVariable Long id,
                                               @RequestBody ReturnRequest request) {
        return ApiResponse.success("归还完成", rentalService.returnOrder(id, request));
    }
}
