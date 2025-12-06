package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.service.AppointmentService;
import pin122.kursovaya.service.RedisQueueService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Тестовый контроллер для создания очередей
 * Доступен без авторизации для тестирования
 */
@RestController
@RequestMapping("/api/test/queue")
public class QueueTestController {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final RedisQueueService redisQueueService;
    private final AppointmentService appointmentService;

    public QueueTestController(PatientRepository patientRepository,
                               DoctorRepository doctorRepository,
                               AppointmentRepository appointmentRepository,
                               RedisQueueService redisQueueService,
                               AppointmentService appointmentService) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.redisQueueService = redisQueueService;
        this.appointmentService = appointmentService;
    }

    /**
     * Создает несколько записей для пациента и строит очереди
     * Время приема: 5 минут
     * 
     * @param patientId ID пациента
     * @return Информация о созданных записях и очередях
     */
    @PostMapping("/create-for-patient/{patientId}")
    public ResponseEntity<Map<String, Object>> createQueuesForPatient(@PathVariable Long patientId) {
        // Проверяем, существует ли пациент
        Patient patient = patientRepository.findById(patientId)
                .orElse(null);
        
        if (patient == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент с ID " + patientId + " не найден"));
        }

        // Получаем всех врачей (или первых 3-5 для теста)
        List<Doctor> doctors = doctorRepository.findAll();
        
        if (doctors.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "В системе нет врачей"));
        }

        // Ограничиваем количество врачей для теста (берем первых 3)
        int doctorsToUse = Math.min(3, doctors.size());
        List<Doctor> selectedDoctors = doctors.subList(0, doctorsToUse);

        // Получаем всех пациентов для создания случайных записей
        List<Patient> allPatients = patientRepository.findAll();
        
        // Фильтруем, исключая переданного пациента
        List<Patient> otherPatients = allPatients.stream()
                .filter(p -> !p.getId().equals(patientId))
                .toList();
        
        if (otherPatients.size() < 4) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "В системе недостаточно пациентов для создания очереди. Нужно минимум 5 пациентов (включая переданного)"));
        }

        // Создаем appointments для каждого врача
        OffsetDateTime now = OffsetDateTime.now();
        List<Appointment> createdAppointments = new ArrayList<>();
        
        // Для каждого врача создаем очередь: 4 случайных пациента + 1 переданный
        for (int i = 0; i < selectedDoctors.size(); i++) {
            Doctor doctor = selectedDoctors.get(i);
            
            // Базовое время начала для этого врача
            OffsetDateTime baseStart = now.plusMinutes(10 + (i * 60));
            
            // Выбираем 4 случайных пациента
            List<Patient> randomPatients = new ArrayList<>(otherPatients);
            java.util.Collections.shuffle(randomPatients);
            List<Patient> selectedRandomPatients = randomPatients.subList(0, 4);
            
            // Создаем 4 записи для случайных пациентов (они будут первыми в очереди)
            for (int j = 0; j < selectedRandomPatients.size(); j++) {
                Patient randomPatient = selectedRandomPatients.get(j);
                OffsetDateTime appointmentStart = baseStart.plusMinutes(j * 10); // Интервал 10 минут между записями
                OffsetDateTime appointmentEnd = appointmentStart.plusMinutes(5);
                
                Appointment appointment = new Appointment();
                appointment.setDoctor(doctor);
                appointment.setPatient(randomPatient);
                appointment.setStartTime(appointmentStart);
                appointment.setEndTime(appointmentEnd);
                appointment.setStatus("scheduled");
                appointment.setSource("admin");
                appointment.setCreatedAt(OffsetDateTime.now());
                appointment.setUpdatedAt(OffsetDateTime.now());
                
                createdAppointments.add(appointment);
            }
            
            // Создаем 1 запись для переданного пациента (он будет 5-м в очереди)
            OffsetDateTime patientAppointmentStart = baseStart.plusMinutes(4 * 10); // После 4 случайных пациентов
            OffsetDateTime patientAppointmentEnd = patientAppointmentStart.plusMinutes(5);
            
            Appointment patientAppointment = new Appointment();
            patientAppointment.setDoctor(doctor);
            patientAppointment.setPatient(patient);
            patientAppointment.setStartTime(patientAppointmentStart);
            patientAppointment.setEndTime(patientAppointmentEnd);
            patientAppointment.setStatus("scheduled");
            patientAppointment.setSource("admin");
            patientAppointment.setCreatedAt(OffsetDateTime.now());
            patientAppointment.setUpdatedAt(OffsetDateTime.now());
            
            createdAppointments.add(patientAppointment);
        }

        // Сохраняем все appointments
        List<Appointment> savedAppointments = appointmentRepository.saveAll(createdAppointments);

        // Собираем все уникальные patientId для пересборки очередей
        java.util.Set<Long> allPatientIds = new java.util.HashSet<>();
        allPatientIds.add(patientId);
        for (Appointment apt : savedAppointments) {
            if (apt.getPatient() != null) {
                allPatientIds.add(apt.getPatient().getId());
            }
        }

        // Пересобираем очереди для всех задействованных пациентов
        for (Long pid : allPatientIds) {
            redisQueueService.buildQueueFromAppointments(pid);
        }

        // Получаем все очереди переданного пациента
        List<QueueEntryDto> queues = redisQueueService.getQueuesByPatient(patientId);

        // Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Создано " + savedAppointments.size() + " записей. Для каждого врача: 4 случайных пациента + 1 переданный пациент (ID: " + patientId + ")");
        response.put("patientId", patientId);
        response.put("appointmentsCreated", savedAppointments.size());
        response.put("doctorsUsed", selectedDoctors.size());
        response.put("patientsInQueues", allPatientIds.size());
        
        // Информация о созданных appointments
        List<Map<String, Object>> appointmentsInfo = new ArrayList<>();
        for (Appointment apt : savedAppointments) {
            Map<String, Object> aptInfo = new HashMap<>();
            aptInfo.put("id", apt.getId());
            aptInfo.put("doctorId", apt.getDoctor().getId());
            aptInfo.put("startTime", apt.getStartTime());
            aptInfo.put("endTime", apt.getEndTime());
            aptInfo.put("status", apt.getStatus());
            appointmentsInfo.add(aptInfo);
        }
        response.put("appointments", appointmentsInfo);
        
        // Информация об очередях
        response.put("queues", queues);
        response.put("queuesCount", queues.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Получает все очереди для пациента
     * 
     * @param patientId ID пациента
     * @return Список очередей
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getPatientQueues(@PathVariable Long patientId) {
        // Проверяем, существует ли пациент
        Patient patient = patientRepository.findById(patientId)
                .orElse(null);
        
        if (patient == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент с ID " + patientId + " не найден"));
        }

        // Получаем все очереди пациента
        List<QueueEntryDto> queues = redisQueueService.getQueuesByPatient(patientId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("patientId", patientId);
        response.put("queues", queues);
        response.put("queuesCount", queues.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Получает очередь к конкретному врачу
     * 
     * @param doctorId ID врача
     * @return Очередь к врачу
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<Map<String, Object>> getDoctorQueue(@PathVariable Long doctorId) {
        // Проверяем, существует ли врач
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElse(null);
        
        if (doctor == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Врач с ID " + doctorId + " не найден"));
        }

        // Получаем очередь к врачу
        List<QueueEntryDto> queue = redisQueueService.getQueueByDoctor(doctorId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("doctorId", doctorId);
        response.put("queue", queue);
        response.put("queueSize", queue.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Пропускает пациента в очереди (удаляет из очереди и помечает приём как "no_show")
     * 
     * @param patientId ID пациента
     * @param doctorId ID врача
     * @return Информация об обновлённой очереди
     */
    @PostMapping("/skip-patient/{patientId}/doctor/{doctorId}")
    public ResponseEntity<Map<String, Object>> skipPatientInQueue(
            @PathVariable Long patientId,
            @PathVariable Long doctorId) {
        
        // Проверяем, существует ли пациент
        Patient patient = patientRepository.findById(patientId)
                .orElse(null);
        
        if (patient == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент с ID " + patientId + " не найден"));
        }

        // Проверяем, существует ли врач
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElse(null);
        
        if (doctor == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Врач с ID " + doctorId + " не найден"));
        }

        // Проверяем, находится ли пациент в очереди
        Integer positionBefore = redisQueueService.getPatientPosition(patientId, doctorId);
        
        if (positionBefore == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент не находится в очереди к этому врачу"));
        }

        // Находим активный appointment пациента к этому врачу
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        Appointment activeAppointment = appointments.stream()
                .filter(a -> doctorId.equals(a.getDoctor().getId()))
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .findFirst()
                .orElse(null);

        // Удаляем пациента из очереди
        boolean removed = redisQueueService.removeFromQueue(patientId, doctorId);
        
        if (!removed) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Не удалось удалить пациента из очереди"));
        }

        // Если есть активный appointment, помечаем его как "no_show"
        String appointmentStatus = null;
        Long appointmentId = null;
        if (activeAppointment != null) {
            appointmentService.updateAppointmentStatus(activeAppointment.getId(), "no_show");
            appointmentStatus = "no_show";
            appointmentId = activeAppointment.getId();
        }

        // Получаем обновлённую очередь
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId);

        // Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Пациент пропущен в очереди");
        response.put("patientId", patientId);
        response.put("doctorId", doctorId);
        response.put("positionBefore", positionBefore);
        response.put("removed", true);
        
        if (appointmentId != null) {
            response.put("appointmentId", appointmentId);
            response.put("appointmentStatus", appointmentStatus);
        }
        
        response.put("updatedQueue", updatedQueue);
        response.put("queueSize", updatedQueue.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Пропускает пациента в очереди по ID приёма
     * 
     * @param appointmentId ID приёма
     * @return Информация об обновлённой очереди
     */
    @PostMapping("/skip-appointment/{appointmentId}")
    public ResponseEntity<Map<String, Object>> skipAppointment(@PathVariable Long appointmentId) {
        // Находим appointment
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElse(null);
        
        if (appointment == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Приём с ID " + appointmentId + " не найден"));
        }

        if (appointment.getPatient() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "У приёма нет привязанного пациента"));
        }

        if (appointment.getDoctor() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "У приёма нет привязанного врача"));
        }

        Long patientId = appointment.getPatient().getId();
        Long doctorId = appointment.getDoctor().getId();

        // Проверяем, находится ли пациент в очереди
        Integer positionBefore = redisQueueService.getPatientPosition(patientId, doctorId);
        
        if (positionBefore == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент не находится в очереди к этому врачу"));
        }

        // Помечаем appointment как "no_show" (это автоматически удалит из очереди)
        var updatedAppointment = appointmentService.updateAppointmentStatus(appointmentId, "no_show");
        
        if (updatedAppointment.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Не удалось обновить статус приёма"));
        }

        // Получаем обновлённую очередь
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId);

        // Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Приём пропущен, пациент удалён из очереди");
        response.put("appointmentId", appointmentId);
        response.put("patientId", patientId);
        response.put("doctorId", doctorId);
        response.put("positionBefore", positionBefore);
        response.put("appointmentStatus", "no_show");
        response.put("updatedQueue", updatedQueue);
        response.put("queueSize", updatedQueue.size());

        return ResponseEntity.ok(response);
    }
}

