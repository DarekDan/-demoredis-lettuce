package com.github.darekdan.demoredislettuce;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;
    private final CacheManager cacheManager;

    // Use AtomicLong for thread-safe counting in a concurrent environment
    private final AtomicLong dbFetchCount = new AtomicLong(0);
    private final AtomicLong cacheFetchCount = new AtomicLong(0);

    /**
     * This method demonstrates programmatic cache access to include fetch counters.
     * The @Cacheable annotation is simpler but doesn't allow this level of custom response.
     */
    public Mono<ItemResponse> getItemById(Long id) {
        Cache itemCache = cacheManager.getCache(RedisConfig.ITEM_CACHE);
        if (itemCache == null) {
            return Mono.error(new RuntimeException("Cache not found: " + RedisConfig.ITEM_CACHE));
        }

        // 1. Try to get from cache
        Cache.ValueWrapper valueWrapper = itemCache.get(id);
        if (valueWrapper != null) {
            Item cachedItem = (Item) valueWrapper.get();
            log.info("Cache HIT for item {}", id);
            return Mono.just(new ItemResponse(
                    cachedItem,
                    dbFetchCount.get(),
                    cacheFetchCount.incrementAndGet(),
                    "Retrieved from cache"
            ));
        }

        // 2. Cache MISS: Get from DB, update counters, put in cache, and return
        log.info("Cache MISS for item {}. Fetching from database.", id);
        return itemRepository.findById(id)
                .flatMap(item -> {
                    log.info("DB fetch complete for item {}", id);
                    itemCache.put(id, item); // Put in cache
                    return Mono.just(new ItemResponse(
                            item,
                            dbFetchCount.incrementAndGet(),
                            cacheFetchCount.get(),
                            "Retrieved from database"
                    ));
                })
                .switchIfEmpty(Mono.just(new ItemResponse(null, dbFetchCount.get(), cacheFetchCount.get(), "Item not found")));
    }

    /**
     * This demonstrates using @Cacheable for a simpler get.
     * Note: This won't update our custom counters.
     */
    @Cacheable(cacheNames = RedisConfig.ITEM_CACHE, key = "#id")
    public Mono<Item> getSimpleItemById(Long id) {
        log.info("Cacheable method: DB fetch for {}", id);
        return itemRepository.findById(id);
    }


    public Mono<Item> createItem(Item item) {
        // We just save. The first 'get' will populate the cache.
        return itemRepository.save(item);
    }

    /**
     * On update, we save to the DB and then evict the entry from the cache.
     * The next GET request will fetch the updated data from the DB and re-populate the cache.
     */
    @CacheEvict(cacheNames = RedisConfig.ITEM_CACHE, key = "#id")
    public Mono<Item> updateItem(Long id, Item item) {
        log.info("Updating item {} and evicting from cache", id);
        item.setId(id); // Ensure ID is set for the save
        return itemRepository.save(item);
    }

    public Mono<Void> resetCounters() {
        dbFetchCount.set(0);
        cacheFetchCount.set(0);
        return Mono.empty();
    }
}
