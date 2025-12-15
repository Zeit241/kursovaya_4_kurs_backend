package pin122.kursovaya.service;

import jakarta.validation.Valid;
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
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.QueueEntryRepository;
import pin122.kursovaya.repository.ReviewRepository;
import pin122.kursovaya.repository.RoleRepository;
import pin122.kursovaya.repository.ScheduleRepository;
import pin122.kursovaya.repository.SpecializationRepository;
import pin122.kursovaya.repository.UserRepository;
import pin122.kursovaya.utils.FormatUtils;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final ReviewRepository reviewRepository;
    private final SpecializationRepository specializationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EntityManager entityManager;
    private final AppointmentRepository appointmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final QueueEntryRepository queueEntryRepository;

    public DoctorService(DoctorRepository doctorRepository, ReviewRepository reviewRepository, 
                        SpecializationRepository specializationRepository, UserRepository userRepository,
                        RoleRepository roleRepository, EntityManager entityManager,
                        AppointmentRepository appointmentRepository, ScheduleRepository scheduleRepository,
                        QueueEntryRepository queueEntryRepository) {
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

    @Transactional(readOnly = true)
    public Optional<DoctorDto> getDoctorById(Long id) {
        return doctorRepository.findById(id).stream().map(this::mapToDto).findFirst();
    }
    

    public DoctorDto saveDoctor(@Valid Doctor doctor) {
        return mapToDto(doctorRepository.save(doctor));
    }
    
    /**
     * Создаёт нового врача с возможностью добавления специализаций
     * @param request DTO с данными врача и списком ID специализаций
     * @return созданный врач в виде DTO
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
        roleRepository.findByCode("doctor").ifPresent(role -> user.getRoles().add(role));
        
        // Сохраняем пользователя
        User savedUser = userRepository.save(user);
        
        // Создаём врача
        Doctor doctor = new Doctor();
        doctor.setUser(savedUser);
        doctor.setDisplayName(request.getDisplayName());
        doctor.setBio(request.getBio());
        doctor.setExperienceYears(request.getExperienceYears());
        doctor.setCreatedAt(OffsetDateTime.now());
        doctor.setUpdatedAt(OffsetDateTime.now());
        
        // Обрабатываем фото (Base64 -> byte[])
        if (request.getPhoto() != null && !request.getPhoto().isEmpty()) {
            try {
                String photoData = request.getPhoto();
                // Убираем префикс Data URL если он есть (например: data:image/jpeg;base64,)
                if (photoData.contains(",")) {
                    photoData = photoData.substring(photoData.indexOf(",") + 1);
                }
                byte[] photoBytes = Base64.getDecoder().decode(photoData);
                doctor.setPhoto(photoBytes);
            } catch (IllegalArgumentException e) {
                // Невалидный Base64, игнорируем
            }
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
     * Обновляет данные врача с возможностью изменения специализаций
     * @param id ID врача для обновления
     * @param request DTO с новыми данными врача
     * @return обновлённый врач в виде DTO или empty если врач не найден
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
        
        // Обновляем данные врача
        if (request.getDisplayName() != null) {
            doctor.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            doctor.setBio(request.getBio());
        }
        if (request.getExperienceYears() != null) {
            doctor.setExperienceYears(request.getExperienceYears());
        }
        
        // Обрабатываем фото (Base64 -> byte[])
        if (request.getPhoto() != null) {
            if (request.getPhoto().isEmpty()) {
                doctor.setPhoto(null);
            } else {
                try {
                    String photoData = request.getPhoto();
                    // Убираем префикс Data URL если он есть (например: data:image/jpeg;base64,)
                    if (photoData.contains(",")) {
                        photoData = photoData.substring(photoData.indexOf(",") + 1);
                    }
                    byte[] photoBytes = Base64.getDecoder().decode(photoData);
                    doctor.setPhoto(photoBytes);
                } catch (IllegalArgumentException e) {
                    // Невалидный Base64, игнорируем
                }
            }
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

        // Конвертируем фото в Base64 строку
        String photoBase64 = null;
        if (doctor.getPhoto() != null && doctor.getPhoto().length > 0) {
            photoBase64 = Base64.getEncoder().encodeToString(doctor.getPhoto());
        }

        DoctorDto doctorDto = new DoctorDto(
                doctor.getId(),
                userDto,
                doctor.getDisplayName(),
                doctor.getBio(),
                doctor.getExperienceYears(),
                photoBase64,
                averageRating,
                reviewCount != null ? reviewCount.intValue() : 0,
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
        doctorDto.setSpecializations(specializations);
        
        return doctorDto;
    }
}