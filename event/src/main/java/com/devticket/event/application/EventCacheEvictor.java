package com.devticket.event.application;

import com.devticket.event.common.config.CachingConfig;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventCacheEvictor {

    private final CacheManager cacheManager;

    public void evictEvents(Collection<UUID> eventIds) {
        Cache detailCache = cacheManager.getCache(CachingConfig.EVENT_DETAIL);
        if (detailCache != null) {
            eventIds.forEach(detailCache::evict);
        }
        Cache listCache = cacheManager.getCache(CachingConfig.EVENT_LIST);
        if (listCache != null) {
            listCache.clear();
        }
    }
}
