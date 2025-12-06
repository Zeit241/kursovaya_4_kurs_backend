package pin122.kursovaya.service;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.dto.UserDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.ReviewRepository;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final ReviewRepository reviewRepository;

    public DoctorService(DoctorRepository doctorRepository, ReviewRepository reviewRepository) {
        this.doctorRepository = doctorRepository;
        this.reviewRepository = reviewRepository;
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

    public void deleteDoctor(Long id) {
        doctorRepository.deleteById(id);
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