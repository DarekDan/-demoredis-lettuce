package com.github.darekdan.demoredislettuce;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping("/{id}")
    public Mono<ItemResponse> getItemById(@PathVariable Long id) {
        return itemService.getItemById(id);
    }

    @PostMapping
    public Mono<Item> createItem(@RequestBody Item item) {
        // Ensure ID is null for a new item to be generated
        item.setId(null);
        return itemService.createItem(item);
    }

    @PutMapping("/{id}")
    public Mono<Item> updateItem(@PathVariable Long id, @RequestBody Item item) {
        return itemService.updateItem(id, item);
    }

    @PostMapping("/reset-counters")
    public Mono<Void> resetCounters() {
        return itemService.resetCounters();
    }
}
