package pin122.kursovaya.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import pin122.kursovaya.dto.validation.OnCreate;
import pin122.kursovaya.dto.validation.OnUpdate;

import java.time.OffsetDateTime;

@Data
public class DoctorDto {
    @NotNull(groups = OnUpdate.class)
    @Null(groups = OnCreate.class)
    private Long id;
    @NotNull(groups = {OnCreate.class, OnUpdate.class})
    private UserDto user;
    @Size(min = 0, max = 50, message = "Описание должно содержать до 50 символов", groups = {OnCreate.class, OnUpdate.class})
    private String bio;
    @Min(value = 0, message = "Опыт не может быть меньше 0", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 80, message = "Опыт не может быть больше 80", groups = {OnCreate.class, OnUpdate.class})
    private Integer experienceYears;
    private String photoUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public DoctorDto() {
    }

    public DoctorDto(Long id, UserDto user, String bio, Integer experienceYears, String photoUrl, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.bio = bio;
        this.experienceYears = experienceYears;
        this.photoUrl = photoUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}