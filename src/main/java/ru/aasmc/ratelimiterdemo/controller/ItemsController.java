package ru.aasmc.ratelimiterdemo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.aasmc.ratelimiterdemo.dto.ItemCreateRequest;
import ru.aasmc.ratelimiterdemo.dto.UserItemsResponse;
import ru.aasmc.ratelimiterdemo.service.ItemsService;

import static org.springframework.http.HttpStatus.CREATED;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/items")
public class ItemsController {

    private final ItemsService service;

    @PostMapping("/create")
    @ResponseStatus(CREATED)
    public ResponseEntity<String> create(@RequestBody ItemCreateRequest request) {
        log.info("Creating item = {}", request);
        service.createItem(request);
        return ResponseEntity.status(CREATED).body("Created item " + request.itemName());
    }

    @GetMapping("/{user}")
    public UserItemsResponse get(@PathVariable("user") String user) {
        log.info("Retrieving items for userId = {}", user);
        return service.getItemsOfUser(user);
    }

}
