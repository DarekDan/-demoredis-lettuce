package com.github.darekdan.demoredislettuce;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponse {
    private Item item;
    private long dbFetchCount;
    private long cacheFetchCount;
    private String message;
}
