package com.enrollgate.ai.controller;

import com.enrollgate.ai.repository.BotDetectionLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bot-detection")
@RequiredArgsConstructor
public class AdminBotDetectionController {

    private final BotDetectionLogRepository botDetectionLogRepository;

    @GetMapping("/logs")
    public List<BotDetectionLogResponse> logs() {
        return botDetectionLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(BotDetectionLogResponse::from)
                .toList();
    }
}
