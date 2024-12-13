package ru.aasmc.ratelimiterdemo.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("items")
public class Item {
    @Id
    private Long id;
    private String name;
    @Column("user_name")
    private String user;
}
