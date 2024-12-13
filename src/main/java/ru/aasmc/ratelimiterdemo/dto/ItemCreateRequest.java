package ru.aasmc.ratelimiterdemo.dto;

public record ItemCreateRequest(
        String user,
        String itemName
) {
}
