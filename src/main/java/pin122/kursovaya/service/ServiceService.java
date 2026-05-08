package pin122.kursovaya.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pin122.kursovaya.dto.ServiceDto;
import pin122.kursovaya.model.SpecializationServiceLink;
import pin122.kursovaya.model.SpecializationServiceLinkPk;
import pin122.kursovaya.repository.BookingCatalogRepository;
import pin122.kursovaya.repository.ServiceRepository;
import pin122.kursovaya.repository.SpecializationRepository;
import pin122.kursovaya.repository.SpecializationServiceLinkRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CRUD медицинских услуг и синхронизация связей «специализация — услуга» для каталога записи.
 */
@Service
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final BookingCatalogRepository bookingCatalogRepository;
    private final SpecializationServiceLinkRepository specializationServiceLinkRepository;
    private final SpecializationRepository specializationRepository;

    /**
     * @param serviceRepository                    сущности {@link pin122.kursovaya.model.Service}
     * @param bookingCatalogRepository             запросы каталога (имена специализаций по услугам)
     * @param specializationServiceLinkRepository  таблица связей specialization_services
     * @param specializationRepository             проверка существования специализаций
     */
    public ServiceService(
            ServiceRepository serviceRepository,
            BookingCatalogRepository bookingCatalogRepository,
            SpecializationServiceLinkRepository specializationServiceLinkRepository,
            SpecializationRepository specializationRepository) {
        this.serviceRepository = serviceRepository;
        this.bookingCatalogRepository = bookingCatalogRepository;
        this.specializationServiceLinkRepository = specializationServiceLinkRepository;
        this.specializationRepository = specializationRepository;
    }

    /**
     * Услуги, доступные для записи к врачу через привязки его специализаций к услугам.
     *
     * @param doctorId идентификатор врача
     * @return отсортированный список {@link ServiceDto} с заполненными специализациями
     */
    @Transactional(readOnly = true)
    public List<ServiceDto> getServicesForDoctor(Long doctorId) {
        List<Long> ids = bookingCatalogRepository.findServiceIdsByDoctorId(doctorId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ServiceDto> dtos = serviceRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(pin122.kursovaya.model.Service::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(ServiceDto::new)
                .toList();
        enrichSpecializations(dtos);
        return dtos;
    }

    /**
     * Все услуги справочника с подгруженными списками специализаций.
     *
     * @return список {@link ServiceDto}
     */
    public List<ServiceDto> getAllServices() {
        List<ServiceDto> dtos = serviceRepository.findAll().stream()
                .map(ServiceDto::new)
                .toList();
        enrichSpecializations(dtos);
        return dtos;
    }

    /**
     * Ищет услугу по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link ServiceDto} с полями специализаций
     */
    public Optional<ServiceDto> getServiceById(Long id) {
        return serviceRepository.findById(id)
                .map(ServiceDto::new)
                .map(this::enrichOne);
    }

    /**
     * Ищет услугу по коду.
     *
     * @param code уникальный код услуги
     * @return {@link ServiceDto} с полями специализаций
     */
    public Optional<ServiceDto> getServiceByCode(String code) {
        return serviceRepository.findByCode(code)
                .map(ServiceDto::new)
                .map(this::enrichOne);
    }

    /**
     * Сохраняет услугу и возвращает DTO с актуальными специализациями.
     *
     * @param serviceEntity сущность услуги
     * @return обогащённый {@link ServiceDto}
     */
    public ServiceDto saveService(pin122.kursovaya.model.Service serviceEntity) {
        ServiceDto dto = new ServiceDto(serviceRepository.save(serviceEntity));
        return enrichOne(dto);
    }

    /**
     * Удаляет связи услуги со специализациями и саму услугу.
     *
     * @param id идентификатор услуги
     */
    @Transactional
    public void deleteService(Long id) {
        specializationServiceLinkRepository.deleteByServiceId(id);
        serviceRepository.deleteById(id);
    }

    /**
     * Атомарно заменяет набор специализаций для услуги.
     *
     * @param serviceId          идентификатор услуги
     * @param specializationIds  список id специализаций (допускается {@code null} как «очистить все»)
     * @return обновлённый {@link ServiceDto} или пустой {@link Optional}, если услуга не найдена
     * @throws ResponseStatusException {@link HttpStatus#BAD_REQUEST}, если указан несуществующий id специализации
     */
    @Transactional
    public Optional<ServiceDto> setServiceSpecializations(Long serviceId, List<Long> specializationIds) {
        if (!serviceRepository.existsById(serviceId)) {
            return Optional.empty();
        }
        List<Long> requested = specializationIds == null ? List.of() : specializationIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!requested.isEmpty()) {
            long found = specializationRepository.countByIdIn(requested);
            if (found != requested.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указана несуществующая специализация");
            }
        }
        specializationServiceLinkRepository.deleteByServiceId(serviceId);
        for (Long specId : requested) {
            SpecializationServiceLink link = new SpecializationServiceLink();
            link.setId(new SpecializationServiceLinkPk(specId, serviceId));
            link.setActive(true);
            specializationServiceLinkRepository.save(link);
        }
        return getServiceById(serviceId);
    }

    /**
     * Заполняет в каждом DTO списки id и имён специализаций по активным связям.
     *
     * @param dtos список DTO для обогащения (изменяются на месте)
     */
    private void enrichSpecializations(List<ServiceDto> dtos) {
        if (dtos.isEmpty()) {
            return;
        }
        List<Long> ids = dtos.stream().map(ServiceDto::getId).collect(Collectors.toList());
        Map<Long, List<Long>> idsByService = loadSpecializationIdsByServiceIds(ids);
        Map<Long, List<String>> namesByService = loadSpecializationNamesByServiceIds(ids);
        for (ServiceDto d : dtos) {
            d.setSpecializationIds(idsByService.getOrDefault(d.getId(), List.of()));
            d.setSpecializationNames(namesByService.getOrDefault(d.getId(), List.of()));
        }
    }

    /**
     * Обогащает один DTO специализациями.
     *
     * @param dto услуга
     * @return тот же объект после заполнения полей
     */
    private ServiceDto enrichOne(ServiceDto dto) {
        enrichSpecializations(List.of(dto));
        return dto;
    }

    /**
     * Загружает id специализаций по списку id услуг из {@link SpecializationServiceLink}.
     *
     * @param serviceIds идентификаторы услуг
     * @return карта «id услуги → отсортированные id специализаций»
     */
    private Map<Long, List<Long>> loadSpecializationIdsByServiceIds(List<Long> serviceIds) {
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        List<SpecializationServiceLink> links =
                specializationServiceLinkRepository.findActiveByServiceIds(serviceIds);
        Map<Long, List<Long>> map = new HashMap<>();
        for (SpecializationServiceLink link : links) {
            Long svcId = link.getId().getServiceId();
            Long specId = link.getId().getSpecializationId();
            map.computeIfAbsent(svcId, k -> new ArrayList<>()).add(specId);
        }
        for (List<Long> list : map.values()) {
            list.sort(Comparator.naturalOrder());
        }
        return map;
    }

    /**
     * Загружает отображаемые имена специализаций для услуг через {@link BookingCatalogRepository}.
     *
     * @param serviceIds идентификаторы услуг
     * @return карта «id услуги → имена специализаций»
     */
    private Map<Long, List<String>> loadSpecializationNamesByServiceIds(List<Long> serviceIds) {
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = bookingCatalogRepository.findSpecializationNamesByServiceIds(serviceIds);
        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] row : rows) {
            Long sid = ((Number) row[0]).longValue();
            String name = (String) row[1];
            map.computeIfAbsent(sid, k -> new ArrayList<>()).add(name);
        }
        return map;
    }
}
