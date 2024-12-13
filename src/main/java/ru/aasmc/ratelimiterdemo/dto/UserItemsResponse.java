package ru.aasmc.ratelimiterdemo.dto;

import java.util.List;

public record UserItemsResponse(
        List<String> items,
        String user
) {
}
