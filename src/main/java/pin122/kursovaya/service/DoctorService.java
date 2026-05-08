package pin122.kursovaya.service;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import pin122.kursovaya.dto.CreateDoctorRequest;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.dto.UpdateDoctorRequest;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.DoctorSpecialization;
import pin122.kursovaya.model.QueueEntry;
import pin122.kursovaya.model.Review;
import pin122.kursovaya.model.Schedule;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.model.User;
import jakarta.persistence.EntityManager;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.BookingCatalogRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.QueueEntryRepository;
import pin122.kursovaya.repository.ReviewRepository;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.ScheduleRepository;
import pin122.kursovaya.repository.SpecializationRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.DoctorPhotoUrls;
import pin122.kursovaya.utils.FormatUtils;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Операции с врачами: списки с пагинацией и сортировкой, поиск, CRUD, каскадное удаление связанных сущностей.
 */
@Service
public class DoctorService {

    @Value("${app.directus.public-url:http://localhost:8055}")
    private String directusPublicUrl;

    private final BookingCatalogRepository bookingCatalogRepository;
    private final DoctorRepository doctorRepository;
    private final ReviewRepository reviewRepository;
    private final SpecializationRepository specializationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EntityManager entityManager;
    private final AppointmentRepository appointmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final QueueEntryRepository queueEntryRepository;

    /**
     * @param bookingCatalogRepository каталог записи (врачи по услуге)
     * @param doctorRepository         сущности {@link Doctor}
     * @param reviewRepository         отзывы врача
     * @param specializationRepository справочник и связи специализаций
     * @param userRepository           пользователи врачей
     * @param roleRepository           роль {@code doctor}
     * @param entityManager            принудительный flush при смене специализаций
     * @param appointmentRepository    приёмы врача
     * @param scheduleRepository       расписания врача
     * @param queueEntryRepository     очередь к врачу
     */
    public DoctorService(BookingCatalogRepository bookingCatalogRepository,
                        DoctorRepository doctorRepository, ReviewRepository reviewRepository, 
                        SpecializationRepository specializationRepository, UserRepository userRepository,
                        RoleRepository roleRepository, EntityManager entityManager,
                        AppointmentRepository appointmentRepository, ScheduleRepository scheduleRepository,
                        QueueEntryRepository queueEntryRepository) {
        this.bookingCatalogRepository = bookingCatalogRepository;
        this.doctorRepository = doctorRepository;
        this.reviewRepository = reviewRepository;
        this.specializationRepository = specializationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.entityManager = entityManager;
        this.appointmentRepository = appointmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    /**
     * Список врачей с опциональной пагинацией и сортировкой; рейтинг сортируется в памяти.
     *
     * @param limit     максимум записей или {@code null}
     * @param offset    смещение или {@code null}
     * @param sortBy    поле сортировки (в т.ч. {@code rating})
     * @param sortOrder {@code asc} или {@code desc}
     * @return список {@link DoctorDto}
     */
    @Transactional(readOnly = true)
    public List<DoctorDto> getAllDoctors(Integer limit, Integer offset, String sortBy, String sortOrder) {
        List<Doctor> doctors;
        Sort sort = createSort(sortBy, sortOrder);
        
        // Если сортировка по рейтингу, загружаем всех врачей и сортируем в памяти
        boolean sortByRating = sortBy != null && "rating".equalsIgnoreCase(sortBy);
        
        if (sortByRating) {
            // Загружаем всех врачей для сортировки по рейтингу
            doctors = doctorRepository.findAll();
        } else if (limit != null && offset != null) {
            // Применяем пагинацию с сортировкой
            Pageable pageable = sort != null 
                    ? PageRequest.of(offset / limit, limit, sort)
                    : PageRequest.of(offset / limit, limit);
            doctors = doctorRepository.findAll(pageable).getContent();
        } else if (limit != null) {
            // Только limit с сортировкой
            Pageable pageable = sort != null 
                    ? PageRequest.of(0, limit, sort)
                    : PageRequest.of(0, limit);
            doctors = doctorRepository.findAll(pageable).getContent();
        } else {
            // Без пагинации, но с сортировкой
            if (sort != null) {
                doctors = doctorRepository.findAll(sort);
            } else {
                doctors = doctorRepository.findAll();
            }
        }
        
        // Преобразуем в DTO
        List<DoctorDto> doctorDtos = doctors.stream()
                .map(this::mapToDto)
                .toList();
        
        // Применяем сортировку по рейтингу в памяти, если нужно
        if (sortByRating) {
            Comparator<DoctorDto> ratingComparator = Comparator.comparing(
                    (DoctorDto d) -> d.getRating() != null ? d.getRating() : 0.0,
                    Comparator.nullsLast(Double::compareTo)
            );
            if ("desc".equalsIgnoreCase(sortOrder)) {
                ratingComparator = ratingComparator.reversed();
            }
            doctorDtos = doctorDtos.stream()
                    .sorted(ratingComparator)
                    .toList();
            
            // Применяем пагинацию после сортировки по рейтингу
            if (limit != null && offset != null) {
                int start = Math.min(offset, doctorDtos.size());
                int end = Math.min(start + limit, doctorDtos.size());
                doctorDtos = doctorDtos.subList(start, end);
            } else if (limit != null) {
                int end = Math.min(limit, doctorDtos.size());
                doctorDtos = doctorDtos.subList(0, end);
            }
        }
        
        return doctorDtos;
    }

    /**
     * Врачи, которые могут оказывать указанную услугу (через связи специализаций), с опциональным текстовым фильтром.
     *
     * @param serviceId идентификатор услуги
     * @param query     подстрока поиска по ФИО/специализации или {@code null}
     * @param limit     лимит выборки
     * @param offset    смещение
     * @param sortBy    поле сортировки
     * @param sortOrder направление сортировки
     * @return список {@link DoctorDto}
     */
    @Transactional(readOnly = true)
    public List<DoctorDto> listDoctorsForService(Long serviceId, String query, Integer limit, Integer offset,
                                                   String sortBy, String sortOrder) {
        List<Long> idList = bookingCatalogRepository.findDoctorIdsByServiceId(serviceId);
        if (idList.isEmpty()) {
            return List.of();
        }
        Set<Long> allowed = new HashSet<>(idList);
        List<Doctor> doctors;
        if (query != null && !query.trim().isEmpty()) {
            doctors = doctorRepository.searchByFullNameOrSpecialization(query.trim()).stream()
                    .filter(d -> allowed.contains(d.getId()))
                    .toList();
        } else {
            doctors = doctorRepository.findAllByIdInWithDetails(idList);
        }

        List<DoctorDto> doctorDtos = doctors.stream().map(this::mapToDto).toList();
        boolean sortByRating = sortBy != null && "rating".equalsIgnoreCase(sortBy);
        if (sortByRating) {
            Comparator<DoctorDto> ratingComparator = Comparator.comparing(
                    (DoctorDto d) -> d.getRating() != null ? d.getRating() : 0.0,
                    Comparator.nullsLast(Double::compareTo)
            );
            if ("desc".equalsIgnoreCase(sortOrder)) {
                ratingComparator = ratingComparator.reversed();
            }
            doctorDtos = doctorDtos.stream().sorted(ratingComparator).toList();
            return sliceDoctorDtos(doctorDtos, limit, offset);
        }

        if (sortBy != null && !sortBy.trim().isEmpty()) {
            Comparator<Doctor> comparator = createComparator(sortBy, sortOrder);
            if (comparator != null) {
                java.util.Map<Long, Doctor> doctorMap = doctors.stream()
                        .collect(Collectors.toMap(Doctor::getId, d -> d));
                doctorDtos = doctorDtos.stream()
                        .sorted((d1, d2) -> comparator.compare(doctorMap.get(d1.getId()), doctorMap.get(d2.getId())))
                        .toList();
            }
        }

        return sliceDoctorDtos(doctorDtos, limit, offset);
    }

    /**
     * Применяет limit/offset к уже отсортированному списку DTO.
     *
     * @param doctorDtos полный список
     * @param limit      лимит или {@code null}
     * @param offset     смещение или {@code null}
     * @return подсписок или исходный список
     */
    private static List<DoctorDto> sliceDoctorDtos(List<DoctorDto> doctorDtos, Integer limit, Integer offset) {
        if (limit != null && offset != null) {
            int start = Math.min(offset, doctorDtos.size());
            int end = Math.min(start + limit, doctorDtos.size());
            return doctorDtos.subList(start, end);
        }
        if (limit != null) {
            return doctorDtos.subList(0, Math.min(limit, doctorDtos.size()));
        }
        return doctorDtos;
    }

    /**
     * Полнотекстовый поиск врачей с ручной сортировкой и пагинацией в памяти.
     *
     * @param query     строка поиска
     * @param limit     лимит
     * @param offset    смещение
     * @param sortBy    поле сортировки
     * @param sortOrder направление
     * @return список {@link DoctorDto}
     */
    @Transactional(readOnly = true)
    public List<DoctorDto> searchDoctors(String query, Integer limit, Integer offset, String sortBy, String sortOrder) {
        List<Doctor> doctors = doctorRepository.searchByFullNameOrSpecialization(query);
        
        // Преобразуем в DTO для возможности сортировки по рейтингу
        List<DoctorDto> doctorDtos = doctors.stream()
                .map(this::mapToDto)
                .toList();
        
        // Применяем сортировку вручную для результатов поиска
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            if ("rating".equalsIgnoreCase(sortBy)) {
                // Сортировка по рейтингу
                Comparator<DoctorDto> ratingComparator = Comparator.comparing(
                        (DoctorDto d) -> d.getRating() != null ? d.getRating() : 0.0,
                        Comparator.nullsLast(Double::compareTo)
                );
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    ratingComparator = ratingComparator.reversed();
                }
                doctorDtos = doctorDtos.stream()
                        .sorted(ratingComparator)
                        .toList();
            } else {
                // Сортировка по другим полям (используем Comparator для Doctor)
                Comparator<Doctor> comparator = createComparator(sortBy, sortOrder);
                if (comparator != null) {
                    // Создаем мапу для быстрого поиска Doctor по DTO
                    java.util.Map<Long, Doctor> doctorMap = doctors.stream()
                            .collect(java.util.stream.Collectors.toMap(Doctor::getId, d -> d));
                    
                    doctorDtos = doctorDtos.stream()
                            .sorted((d1, d2) -> {
                                Doctor doc1 = doctorMap.get(d1.getId());
                                Doctor doc2 = doctorMap.get(d2.getId());
                                return comparator.compare(doc1, doc2);
                            })
                            .toList();
                }
            }
        }
        
        // Применяем пагинацию вручную для результатов поиска
        if (limit != null && offset != null) {
            int start = Math.min(offset, doctorDtos.size());
            int end = Math.min(start + limit, doctorDtos.size());
            doctorDtos = doctorDtos.subList(start, end);
        } else if (limit != null) {
            int end = Math.min(limit, doctorDtos.size());
            doctorDtos = doctorDtos.subList(0, end);
        }
        
        return doctorDtos;
    }

    /**
     * Врач по идентификатору с данными для карточки (рейтинг, специализации, фото — публичный URL).
     *
     * @param id первичный ключ
     * @return {@link DoctorDto}, если найден
     */
    @Transactional(readOnly = true)
    public Optional<DoctorDto> getDoctorById(Long id) {
        return doctorRepository.findById(id).stream().map(this::mapToDto).findFirst();
    }
    

    /**
     * Сохраняет сущность врача и возвращает DTO.
     *
     * @param doctor валидируемая сущность {@link Doctor}
     * @return {@link DoctorDto}
     */
    public DoctorDto saveDoctor(@Valid Doctor doctor) {
        return mapToDto(doctorRepository.save(doctor));
    }
    
    /**
     * Создаёт пользователя с ролью {@code doctor}, профиль врача и опционально связи со специализациями; фото — UUID файла Directus или полный URL.
     *
     * @param request данные создания
     * @return {@link DoctorDto} сохранённого врача
     */
    @Transactional
    public DoctorDto createDoctor(@Valid CreateDoctorRequest request) {
        // Создаём пользователя
        User user = new User();
        user.setEmail(request.getUser().getEmail());
        user.setPhone(FormatUtils.normalizePhone(request.getUser().getPhone()));
        user.setFirstName(request.getUser().getFirstName());
        user.setLastName(request.getUser().getLastName());
        user.setMiddleName(request.getUser().getMiddleName());
        user.setActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        
        // Назначаем роль "doctor"
        roleRepository.findByCode("doctor").ifPresent(user::setRole);
        
        // Сохраняем пользователя
        User savedUser = userRepository.save(user);
        
        // Создаём врача
        Doctor doctor = new Doctor();
        doctor.setUser(savedUser);
        doctor.setBio(request.getBio());
        doctor.setExperienceYears(request.getExperienceYears());
        doctor.setCreatedAt(OffsetDateTime.now());
        doctor.setUpdatedAt(OffsetDateTime.now());
        
        if (request.getPhoto() != null) {
            applyPhotoFromRequest(doctor, request.getPhoto());
        }

        // Сохраняем врача
        Doctor savedDoctor = doctorRepository.save(doctor);
        
        // Добавляем специализации, если они указаны
        if (request.getSpecializationIds() != null && !request.getSpecializationIds().isEmpty()) {
            for (Long specId : request.getSpecializationIds()) {
                specializationRepository.findById(specId).ifPresent(specialization -> {
                    DoctorSpecialization ds = new DoctorSpecialization();
                    ds.setDoctor(savedDoctor);
                    ds.setSpecialization(specialization);
                    savedDoctor.getSpecializations().add(ds);
                });
            }
            // Сохраняем врача с специализациями
            doctorRepository.save(savedDoctor);
        }
        
        return mapToDto(savedDoctor);
    }

    /**
     * Удаляет врача вместе с приёмами, расписаниями, очередью и отзывами; пользователь удаляется каскадом.
     *
     * @param id идентификатор врача; при отсутствии записи метод завершается без ошибки
     */
    @Transactional
    public void deleteDoctor(Long id) {
        Optional<Doctor> doctorOpt = doctorRepository.findById(id);
        if (doctorOpt.isEmpty()) {
            return; // Врач не найден
        }
        
        Doctor doctor = doctorOpt.get();
        Long doctorId = doctor.getId();
        
        // 1. Удаляем все записи на приём (appointments)
        List<Appointment> appointments = appointmentRepository.findByDoctorId(doctorId);
        if (!appointments.isEmpty()) {
            appointmentRepository.deleteAll(appointments);
        }
        
        // 2. Удаляем все расписания (schedules)
        List<Schedule> schedules = scheduleRepository.findByDoctorId(doctorId);
        if (!schedules.isEmpty()) {
            scheduleRepository.deleteAll(schedules);
        }
        
        // 3. Удаляем все записи в очереди (queue entries)
        List<QueueEntry> queueEntries = queueEntryRepository.findByDoctorIdOrderByPositionAsc(doctorId);
        if (!queueEntries.isEmpty()) {
            queueEntryRepository.deleteAll(queueEntries);
        }
        
        // 4. Удаляем все отзывы (reviews)
        List<Review> reviews = reviewRepository.findByDoctorId(doctorId);
        if (!reviews.isEmpty()) {
            reviewRepository.deleteAll(reviews);
        }
        
        // 5. Специализации удалятся автоматически через orphanRemoval = true
        
        // 6. Удаляем врача (это также удалит связанного User через каскад)
        doctorRepository.delete(doctor);
    }
    
    /**
     * Частично обновляет пользователя и профиль врача; список специализаций при передаче полностью заменяется.
     *
     * @param id      идентификатор врача
     * @param request новые данные
     * @return {@link DoctorDto} или пустой {@link Optional}, если врач не найден
     */
    @Transactional
    public Optional<DoctorDto> updateDoctor(Long id, @Valid UpdateDoctorRequest request) {
        Optional<Doctor> existingDoctorOpt = doctorRepository.findById(id);
        
        if (existingDoctorOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Doctor doctor = existingDoctorOpt.get();
        User user = doctor.getUser();
        
        // Обновляем данные пользователя, если они переданы
        if (request.getUser() != null) {
            if (request.getUser().getEmail() != null) {
                user.setEmail(request.getUser().getEmail());
            }
            if (request.getUser().getPhone() != null) {
                user.setPhone(FormatUtils.normalizePhone(request.getUser().getPhone()));
            }
            if (request.getUser().getFirstName() != null) {
                user.setFirstName(request.getUser().getFirstName());
            }
            if (request.getUser().getLastName() != null) {
                user.setLastName(request.getUser().getLastName());
            }
            if (request.getUser().getMiddleName() != null) {
                user.setMiddleName(request.getUser().getMiddleName());
            }
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
        }
        
        // Обновляем данные врача (отображаемое имя берётся из ФИО пользователя)
        if (request.getBio() != null) {
            doctor.setBio(request.getBio());
        }
        if (request.getExperienceYears() != null) {
            doctor.setExperienceYears(request.getExperienceYears());
        }
        
        if (request.getPhoto() != null) {
            applyPhotoFromRequest(doctor, request.getPhoto());
        }
        
        doctor.setUpdatedAt(OffsetDateTime.now());
        
        // Обновляем специализации, если они указаны
        if (request.getSpecializationIds() != null) {
            // Очищаем текущие специализации
            doctor.getSpecializations().clear();
            
            // Сохраняем и делаем flush, чтобы удаления произошли до вставок
            doctorRepository.save(doctor);
            entityManager.flush();
            
            // Добавляем новые специализации
            for (Long specId : request.getSpecializationIds()) {
                specializationRepository.findById(specId).ifPresent(specialization -> {
                    DoctorSpecialization ds = new DoctorSpecialization();
                    ds.setDoctor(doctor);
                    ds.setSpecialization(specialization);
                    doctor.getSpecializations().add(ds);
                });
            }
        }
        
        Doctor savedDoctor = doctorRepository.save(doctor);
        return Optional.of(mapToDto(savedDoctor));
    }

    /**
     * Строит {@link Sort} для Spring Data по строковым параметрам API (рейтинг возвращает {@code null} — сортировка в памяти).
     *
     * @param sortBy    имя поля
     * @param sortOrder направление
     * @return объект сортировки или {@code null}
     */
    private Sort createSort(String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return null;
        }
        
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;
        
        // Маппинг полей для сортировки
        // Примечание: сортировка по rating не поддерживается на уровне БД,
        // так как это вычисляемое поле. Используется сортировка в памяти.
        String sortField = switch (sortBy.toLowerCase()) {
            case "firstname", "first_name" -> "user.firstName";
            case "lastname", "last_name" -> "user.lastName";
            case "experience", "experience_years", "experienceyears" -> "experienceYears";
            case "created", "created_at", "createdat" -> "createdAt";
            case "updated", "updated_at", "updatedat" -> "updatedAt";
            case "rating" -> null; // Сортировка по рейтингу выполняется в памяти
            default -> null;
        };
        
        if (sortField == null) {
            return null;
        }
        
        return Sort.by(direction, sortField);
    }
    
    /**
     * Компаратор сущностей {@link Doctor} для сортировки результатов поиска в памяти.
     *
     * @param sortBy    поле
     * @param sortOrder направление
     * @return компаратор или {@code null} для рейтинга/неизвестного поля
     */
    private Comparator<Doctor> createComparator(String sortBy, String sortOrder) {
        boolean ascending = !"desc".equalsIgnoreCase(sortOrder);
        
        Comparator<Doctor> comparator = switch (sortBy.toLowerCase()) {
            case "firstname", "first_name" -> Comparator.comparing(d -> 
                    d.getUser().getFirstName() != null ? d.getUser().getFirstName() : "", 
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "lastname", "last_name" -> Comparator.comparing(d -> 
                    d.getUser().getLastName() != null ? d.getUser().getLastName() : "", 
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "experience", "experience_years", "experienceyears" -> Comparator.comparing(
                    Doctor::getExperienceYears, 
                    Comparator.nullsLast(Integer::compareTo));
            case "created", "created_at", "createdat" -> Comparator.comparing(
                    Doctor::getCreatedAt, 
                    Comparator.nullsLast(java.time.OffsetDateTime::compareTo));
            case "updated", "updated_at", "updatedat" -> Comparator.comparing(
                    Doctor::getUpdatedAt, 
                    Comparator.nullsLast(java.time.OffsetDateTime::compareTo));
            case "rating" -> {
                // Сортировка по рейтингу выполняется после преобразования в DTO
                yield null;
            }
            default -> null;
        };
        
        if (comparator == null) {
            return null;
        }
        
        return ascending ? comparator : comparator.reversed();
    }

    private void applyPhotoFromRequest(Doctor doctor, String raw) {
        String normalized = DoctorPhotoUrls.normalizeForStorage(raw);
        doctor.setPhoto(normalized);
    }

    /**
     * Собирает {@link DoctorDto} с пользователем, рейтингом, специализациями и фото (публичный URL ассета Directus).
     *
     * @param doctor сущность (ожидается связанный {@link User})
     * @return DTO для API
     */
    @Transactional(readOnly = true)
    private DoctorDto mapToDto(Doctor doctor) {
        User user = doctor.getUser();
        UserDto userDto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleName(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.isActive()
        );

        // Вычисляем средний рейтинг и количество отзывов
        Double averageRating = reviewRepository.findAverageRatingByDoctorId(doctor.getId())
                .orElse(null);
        Long reviewCount = reviewRepository.countByDoctorId(doctor.getId());

        // Округляем рейтинг до 1 знака после запятой
        if (averageRating != null) {
            averageRating = Math.round(averageRating * 10.0) / 10.0;
        }

        // Загружаем специализации врача
        // Инициализируем коллекцию, если она еще не загружена
        if (doctor.getSpecializations() != null) {
            doctor.getSpecializations().size(); // Это загрузит коллекцию, если она lazy
        }
        List<SpecializationDto> specializations = doctor.getSpecializations() != null 
                ? doctor.getSpecializations().stream()
                        .map(ds -> new SpecializationDto(ds.getSpecialization()))
                        .toList()
                : List.of();

        String photoUrl = DoctorPhotoUrls.toPublicImageUrl(doctor.getPhoto(), directusPublicUrl);

        DoctorDto doctorDto = new DoctorDto(
                doctor.getId(),
                userDto,
                doctor.getDisplayName(),
                doctor.getBio(),
                doctor.getExperienceYears(),
                photoUrl,
                averageRating,
                reviewCount != null ? reviewCount.intValue() : 0,
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
        doctorDto.setSpecializations(specializations);
        
        return doctorDto;
    }
}