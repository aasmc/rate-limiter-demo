package ru.aasmc.ratelimiterdemo.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.aasmc.ratelimiterdemo.dto.ItemCreateRequest;
import ru.aasmc.ratelimiterdemo.dto.UserItemsResponse;
import ru.aasmc.ratelimiterdemo.storage.model.Item;
import ru.aasmc.ratelimiterdemo.storage.repository.ItemsRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemsService {

    private static final String ITEM_METRIC = "item_event";
    private static final String USER_TAG = "user";
    private static final String EVENT_TAG = "event";
    private static final String EVENT_CREATE = "create";
    private static final String EVENT_GET = "get";

    private final ItemsRepository itemsRepository;
    private final UserRateLimiterService userRateLimiterService;
    private final MeterRegistry meterRegistry;

    public void createItem(ItemCreateRequest request) {
        Item item = new Item(null, request.itemName(), request.user());
        itemsRepository.save(item);
        registerMetric(request.user(), EVENT_CREATE);
    }

    public UserItemsResponse getItemsOfUser(String userName) {
        userRateLimiterService.permitRequestOrThrow(userName);
        List<String> itemNames = itemsRepository.findAllByUser(userName)
                .stream()
                .map(Item::getName)
                .toList();
        registerMetric(userName, EVENT_GET);
        return new UserItemsResponse(itemNames, userName);
    }

    private void registerMetric(String user, String event) {
        meterRegistry.counter(ITEM_METRIC, List.of(Tag.of(USER_TAG, user), Tag.of(EVENT_TAG, event))).increment();
    }

}
