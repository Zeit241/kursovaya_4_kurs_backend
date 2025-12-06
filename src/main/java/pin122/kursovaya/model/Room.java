package pin122.kursovaya.model;

import jakarta.persistence.*;
import lombok.Data;

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

    public Room(String code, String name) {
        this.code = code;
        this.name = name;
    }
}


