package com.orionkey.controller;

import com.orionkey.annotation.LogOperation;
import com.orionkey.common.ApiResponse;
import com.orionkey.constant.ErrorCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.service.GuestbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/guestbook/messages")
@RequiredArgsConstructor
public class AdminGuestbookController {

    private final GuestbookService guestbookService;

    @GetMapping
    public ApiResponse<?> listMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.success(guestbookService.listAdminMessages(page, pageSize));
    }

    @LogOperation(action = "guestbook.visibility", targetType = "GUESTBOOK", targetId = "#id", detail = "'更新留言显示状态'")
    @PutMapping("/{id}/visibility")
    public ApiResponse<Void> updateVisibility(@PathVariable UUID id,
                                              @RequestBody Map<String, Object> request) {
        guestbookService.updateVisibility(id, parseRequiredBoolean(request.get("is_visible")));
        return ApiResponse.success();
    }

    private boolean parseRequiredBoolean(Object value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "显示状态不能为空");
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String raw = value.toString().trim();
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "显示状态格式不正确");
    }
}
