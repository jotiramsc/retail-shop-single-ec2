package com.retailshop.service.bot;

import com.retailshop.dto.bot.BotMemoryRecord;

import java.util.List;
import java.util.Optional;

public interface QdrantMemoryService {
    void ensureCollection();

    List<BotMemoryRecord> searchByMobile(String mobile, List<Double> vector, int limit);

    Optional<String> upsert(String pointId, List<Double> vector, BotMemoryRecord memory);
}
