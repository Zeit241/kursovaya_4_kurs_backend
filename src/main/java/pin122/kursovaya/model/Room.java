package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * JPA-сущность кабинета (помещения): уникальный код и человекочитаемое название.
 */
@Data
@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String name;

    public Room() {
    }

    /**
     * @param code уникальный код кабинета
     * @param name отображаемое название
     */
    public Room(String code, String name) {
        this.code = code;
        this.name = name;
    }
}


