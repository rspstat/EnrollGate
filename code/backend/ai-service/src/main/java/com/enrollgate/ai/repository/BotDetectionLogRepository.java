package com.enrollgate.ai.repository;

import com.enrollgate.ai.domain.BotDetectionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotDetectionLogRepository extends JpaRepository<BotDetectionLog, Long> {

    List<BotDetectionLog> findTop50ByOrderByCreatedAtDesc();
}
