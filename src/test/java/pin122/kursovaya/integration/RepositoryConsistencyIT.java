package pin122.kursovaya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.QueueEntry;
import pin122.kursovaya.model.Role;
import pin122.kursovaya.model.User;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.QueueEntryRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты согласованности JPA-репозиториев с PostgreSQL Testcontainers.
 */
@DisplayName("RepositoryConsistencyIT")
class RepositoryConsistencyIT extends AbstractIntegrationIT {

    private static final String PASSWORD = "RepoPass123!";

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Test
    @DisplayName("UserRepository.findByEmail возвращает сохранённого пользователя с ролью")
    void userRepository_findByEmail_returnsPersistedUser() {
        String email = uniqueEmail("user-repo");
        createPatientUser(email, PASSWORD);

        User found = userRepository.findByEmail(email);

        assertNotNull(found);
        assertEquals(email, found.getEmail());
        assertNotNull(found.getRole());
        assertEquals("patient", found.getRole().getCode());
    }

    @Test
    @DisplayName("PatientRepository.findByUserId находит профиль пациента")
    void patientRepository_findByUserId_linksToUser() {
        String email = uniqueEmail("patient-repo");
        User user = createPatientUser(email, PASSWORD);

        Optional<Patient> patient = patientRepository.findByUserId(user.getId());

        assertTrue(patient.isPresent());
        assertEquals(user.getId(), patient.get().getUser().getId());
    }

    @Test
    @DisplayName("AppointmentRepository сохраняет связь с врачом и читает по doctorId")
    void appointmentRepository_persistsDoctorRelation() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor-repo"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Appointment slot = createSlot(doctor, futureSlotTime(2), "available");

        var byDoctor = appointmentRepository.findByDoctorId(doctor.getId());

        assertEquals(1, byDoctor.size());
        assertEquals(slot.getId(), byDoctor.get(0).getId());
        assertEquals(doctor.getId(), byDoctor.get(0).getDoctor().getId());
    }

    @Test
    @DisplayName("RoleRepository.findByCode возвращает уникальную роль")
    void roleRepository_findByCode_isConsistent() {
        Role patient = saveRoleIfMissing("patient", "Пациент");

        Optional<Role> loaded = roleRepository.findByCode("patient");

        assertTrue(loaded.isPresent());
        assertEquals(patient.getId(), loaded.get().getId());
        assertEquals("patient", loaded.get().getCode());
    }

    @Test
    @DisplayName("QueueEntryRepository сохраняет позицию пациента к врачу")
    void queueEntryRepository_persistsQueuePosition() {
        User doctorUser = createDoctorUser(uniqueEmail("doctor-queue"), PASSWORD);
        User patientUser = createPatientUser(uniqueEmail("patient-queue"), PASSWORD);
        Doctor doctor = requireDoctor(doctorUser);
        Patient patient = patientUser.getPatient();

        QueueEntry entry = new QueueEntry();
        entry.setDoctor(doctor);
        entry.setPatient(patient);
        entry.setPosition(0);
        entry.setLastUpdated(OffsetDateTime.now());
        QueueEntry saved = queueEntryRepository.save(entry);

        var found = queueEntryRepository.findByPatientIdAndDoctorId(patient.getId(), doctor.getId());

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals(0, found.get().getPosition());
    }
}
