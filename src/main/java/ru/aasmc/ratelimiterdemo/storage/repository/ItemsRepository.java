package ru.aasmc.ratelimiterdemo.storage.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.aasmc.ratelimiterdemo.storage.model.Item;

import java.util.List;

@Repository
public interface ItemsRepository extends CrudRepository<Item, Long> {

    List<Item> findAllByUser(String user);
}
