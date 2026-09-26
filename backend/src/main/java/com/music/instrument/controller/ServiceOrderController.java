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
import com.music.instrument.domain.Technician;
import com.music.instrument.dto.AcceptanceRequest;
import com.music.instrument.dto.CompletionSubmitRequest;
import com.music.instrument.dto.ServiceCreateRequest;
import com.music.instrument.dto.StatusChangeRequest;
import com.music.instrument.dto.view.ServiceView;
import com.music.instrument.service.ServiceOrderService;

/**
 * 调律与维修接口。
 */
@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;

    public ServiceOrderController(ServiceOrderService serviceOrderService) {
        this.serviceOrderService = serviceOrderService;
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.success(serviceOrderService.options());
    }

    @GetMapping("/technicians")
    public ApiResponse<List<Technician>> technicians() {
        return ApiResponse.success(serviceOrderService.listTechnicians());
    }

    @GetMapping
    public ApiResponse<List<ServiceView>> list(@RequestParam(required = false) List<Long> instrumentIds,
                                               @RequestParam(required = false) List<Long> technicianIds,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.success(serviceOrderService.list(instrumentIds, technicianIds, status));
    }

    @PostMapping
    public ApiResponse<ServiceView> create(@RequestBody ServiceCreateRequest request) {
        return ApiResponse.success("派单成功", serviceOrderService.create(request));
    }

    /** 通用状态推进：开始施工 / 取消派单（完工、验收走专属接口） */
    @PostMapping("/{id}/status")
    public ApiResponse<ServiceView> changeStatus(@PathVariable Long id,
                                                 @RequestBody StatusChangeRequest request) {
        return ApiResponse.success("状态已推进", serviceOrderService.changeStatus(id, request));
    }

    /** 调律师提交完工：写清处理内容、检测结果、交付说明，服务单进入待验收 */
    @PostMapping("/{id}/completions")
    public ApiResponse<ServiceView> submitCompletion(@PathVariable Long id,
                                                     @RequestBody CompletionSubmitRequest request) {
        return ApiResponse.success("完工已提交，等待店员验收",
                serviceOrderService.submitCompletion(id, request));
    }

    /** 店员验收：ACCEPTED 通过（服务单完成、乐器恢复在库）或 REJECTED 退回返修 */
    @PostMapping("/{id}/acceptance")
    public ApiResponse<ServiceView> accept(@PathVariable Long id,
                                           @RequestBody AcceptanceRequest request) {
        boolean rejected = request != null && "REJECTED".equals(request.getDecision());
        String message = rejected ? "已退回返修" : "验收通过，服务单完成";
        return ApiResponse.success(message, serviceOrderService.accept(id, request));
    }
}
