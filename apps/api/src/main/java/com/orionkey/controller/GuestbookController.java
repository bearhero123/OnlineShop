package com.orionkey.controller;

import com.orionkey.common.ApiResponse;
import com.orionkey.service.GuestbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/guestbook/messages")
@RequiredArgsConstructor
public class GuestbookController {

    private final GuestbookService guestbookService;

    @GetMapping
    public ApiResponse<?> listMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        return ApiResponse.success(guestbookService.listPublicMessages(page, pageSize));
    }

    @PostMapping
    public ApiResponse<?> createMessage(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(guestbookService.createMessage(request));
    }
}
