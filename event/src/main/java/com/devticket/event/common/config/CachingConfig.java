package com.devticket.event.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CachingConfig {

    public static final String EMBEDDINGS = "embeddings";
    public static final String EVENT_LIST = "event:list";
    public static final String EVENT_DETAIL = "event:detail";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            buildCache(EMBEDDINGS, 10_000, Duration.ofHours(24)),
            buildCache(EVENT_LIST, 1_000, Duration.ofSeconds(10)),
            buildCache(EVENT_DETAIL, 5_000, Duration.ofSeconds(30))
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long maximumSize, Duration ttl) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build()
        );
    }
}
