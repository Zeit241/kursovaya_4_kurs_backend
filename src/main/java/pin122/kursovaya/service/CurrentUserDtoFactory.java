package pin122.kursovaya.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pin122.kursovaya.dto.CurrentUserDto;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;

/**
 * Сборка {@link CurrentUserDto} из сущности {@link User} с дозаполнением {@code doctorId} и {@code patientId}
 * прямыми запросами к БД, если lazy-связи {@code User} не загружены.
 */
@Component
public class CurrentUserDtoFactory {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserDtoFactory.class);

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Value("${app.directus.public-url:http://localhost:8055}")
    private String directusPublicUrl;

    /**
     * @param doctorRepository  поиск профиля врача по {@code userId}
     * @param patientRepository поиск профиля пациента по {@code userId}
     */
    public CurrentUserDtoFactory(DoctorRepository doctorRepository,
                                 PatientRepository patientRepository) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Строит DTO профиля и при необходимости обогащает идентификаторами врача/пациента из таблиц {@code doctors}/{@code patients}.
     *
     * @param user сущность пользователя; может быть {@code null}
     * @return готовый {@link CurrentUserDto} или {@code null}, если {@code user == null}
     */
    public CurrentUserDto build(User user) {
        if (user == null) {
            return null;
        }
        CurrentUserDto dto = new CurrentUserDto(user);
        Long beforeDoctor = dto.getDoctorId();
        Long beforePatient = dto.getPatientId();
        enrichMissingLinks(user, dto);
        log.info(
                "[auth-profile] userId={} email={} roleCode={} doctorId {}->{} patientId {}->{}",
                user.getId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().getCode() : null,
                beforeDoctor,
                dto.getDoctorId(),
                beforePatient,
                dto.getPatientId());
        return dto;
    }

    /**
     * Для ролей {@code doctor} и {@code patient} подставляет в DTO идентификаторы и вложенные info-объекты из репозиториев.
     *
     * @param user исходный пользователь
     * @param dto  изменяемый {@link CurrentUserDto}
     */
    private void enrichMissingLinks(User user, CurrentUserDto dto) {
        if (user.getRole() == null) {
            log.warn("[auth-profile] у пользователя нет роли userId={} email={}", user.getId(), user.getEmail());
            return;
        }
        String code = user.getRole().getCode();
        if (code == null) {
            log.warn("[auth-profile] role.code пустой userId={} email={}", user.getId(), user.getEmail());
            return;
        }
        if (dto.getDoctorId() == null && "doctor".equalsIgnoreCase(code)) {
            var opt = doctorRepository.findByUserId(user.getId());
            if (opt.isPresent()) {
                var d = opt.get();
                dto.setDoctorId(d.getId());
                dto.setDoctor(new CurrentUserDto.DoctorInfo(d, directusPublicUrl));
                log.debug("[auth-profile] doctorId подставлен из БД: userId={} doctorId={}", user.getId(), d.getId());
            } else {
                log.warn(
                        "[auth-profile] no row in doctors for userId={} email={} (role=doctor). Run sql migration/ensure_doctor_profiles.sql or db-seeder.",
                        user.getId(),
                        user.getEmail());
            }
        }
        if (dto.getPatientId() == null && "patient".equalsIgnoreCase(code)) {
            var opt = patientRepository.findByUserId(user.getId());
            if (opt.isPresent()) {
                var p = opt.get();
                dto.setPatientId(p.getId());
                dto.setPatient(new CurrentUserDto.PatientInfo(p));
                log.debug("[auth-profile] patientId подставлен из БД: userId={} patientId={}", user.getId(), p.getId());
            } else {
                log.warn(
                        "[auth-profile] no row in patients for userId={} email={} (role=patient).",
                        user.getId(),
                        user.getEmail());
            }
        }
    }
}
