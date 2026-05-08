package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.AiCatalogResponseDto;
import pin122.kursovaya.dto.AiDoctorRefDto;
import pin122.kursovaya.dto.AiServiceRefDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.DoctorSpecialization;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.ServiceRepository;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Формирует компактный справочник врачей и услуг для сценариев ИИ/подсказок (без скрытых врачей).
 */
@Service
public class AiCatalogService {

    private final DoctorRepository doctorRepository;
    private final ServiceRepository serviceRepository;

    /**
     * @param doctorRepository  репозиторий врачей
     * @param serviceRepository репозиторий услуг ({@link pin122.kursovaya.model.Service})
     */
    public AiCatalogService(DoctorRepository doctorRepository, ServiceRepository serviceRepository) {
        this.doctorRepository = doctorRepository;
        this.serviceRepository = serviceRepository;
    }

    /**
     * Собирает отсортированные списки ссылок на врачей и услуги для передачи во внешний ИИ-контекст.
     *
     * @return {@link AiCatalogResponseDto} с двумя списками
     */
    @Transactional(readOnly = true)
    public AiCatalogResponseDto buildAiReferenceCatalog() {
        var doctors = doctorRepository.findAll().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getHide()))
                .map(this::toDoctorRef)
                .sorted(Comparator.comparing(AiDoctorRefDto::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        var services = serviceRepository.findAll().stream()
                .map(this::toServiceRef)
                .sorted(Comparator.comparing(AiServiceRefDto::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        return new AiCatalogResponseDto(doctors, services);
    }

    /**
     * Преобразует {@link Doctor} в краткую ссылку: имя для отображения, строка специализаций или заглушка «Врач».
     *
     * @param d сущность врача
     * @return DTO для каталога ИИ
     */
    private AiDoctorRefDto toDoctorRef(Doctor d) {
        String name = d.getDisplayName();
        if (name == null || name.isBlank()) {
            name = d.getUser() != null ? d.getUser().getEmail() : "Врач #" + d.getId();
        }
        String spec = "";
        if (d.getSpecializations() != null) {
            spec = d.getSpecializations().stream()
                    .map(DoctorSpecialization::getSpecialization)
                    .filter(s -> s != null && s.getName() != null)
                    .map(pin122.kursovaya.model.Specialization::getName)
                    .collect(Collectors.joining(", "));
        }
        if (spec.isBlank()) {
            spec = "Врач";
        }
        return new AiDoctorRefDto(d.getId(), name.trim(), spec);
    }

    /**
     * Преобразует услугу в пару идентификатор — наименование.
     *
     * @param s сущность {@link pin122.kursovaya.model.Service}
     * @return DTO для каталога ИИ
     */
    private AiServiceRefDto toServiceRef(pin122.kursovaya.model.Service s) {
        return new AiServiceRefDto(s.getId(), s.getName());
    }
}
