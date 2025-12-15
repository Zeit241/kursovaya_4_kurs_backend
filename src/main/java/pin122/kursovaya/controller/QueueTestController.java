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
 * Контроллер для работы с очередями через Redis
 * Получение всех пациентов в очередях, где есть текущий пациент
 * Пропуск других пациентов в очереди
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
     * Создает тестовую очередь для пациента
     * Создает appointments и строит очередь в Redis
     * 
     * @param patientId ID пациента (опционально, если не указан - берется первый)
     * @param doctorId ID врача (опционально, если не указан - берется первый)
     * @param patientsBefore Количество пациентов перед текущим (по умолчанию 3)
     * @param patientsAfter Количество пациентов после текущего (по умолчанию 2)
     * @return Информация о созданной очереди
     */
    @PostMapping("/create-test-queue")
    public ResponseEntity<Map<String, Object>> createTestQueue(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(defaultValue = "3") Integer patientsBefore,
            @RequestParam(defaultValue = "2") Integer patientsAfter) {
        
        // Если patientId не указан, берем первого пациента
        if (patientId == null) {
            List<Patient> allPatients = patientRepository.findAll();
            if (allPatients.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "В системе нет пациентов"));
            }
            patientId = allPatients.get(0).getId();
        }
        
        // Если doctorId не указан, берем первого врача
        if (doctorId == null) {
            List<Doctor> allDoctors = doctorRepository.findAll();
            if (allDoctors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "В системе нет врачей"));
            }
            doctorId = allDoctors.get(0).getId();
        }
        
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
        
        // Получаем других пациентов для создания очереди
        List<Patient> otherPatients = patientRepository.findAll().stream()
                .filter(p -> !p.getId().equals(patientId))
                .toList();
        
        int totalPatientsNeeded = patientsBefore + patientsAfter;
        if (otherPatients.size() < totalPatientsNeeded) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", 
                        String.format("Недостаточно пациентов. Нужно минимум %d других пациентов (сейчас: %d)", 
                            totalPatientsNeeded, otherPatients.size())));
        }
        
        // Выбираем пациентов для очереди
        List<Patient> selectedPatients = new ArrayList<>(otherPatients);
        java.util.Collections.shuffle(selectedPatients);
        selectedPatients = selectedPatients.subList(0, totalPatientsNeeded);
        
        // Разделяем на тех, кто будет перед и после
        List<Patient> beforePatients = selectedPatients.subList(0, patientsBefore);
        List<Patient> afterPatients = selectedPatients.subList(patientsBefore, totalPatientsNeeded);
        
        // Создаем appointments
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime baseStart = now.plusMinutes(10);
        List<Appointment> createdAppointments = new ArrayList<>();
        
        int appointmentIndex = 0;
        
        // Создаем appointments для пациентов перед текущим
        for (Patient p : beforePatients) {
            OffsetDateTime appointmentStart = baseStart.plusMinutes(appointmentIndex * 10);
            OffsetDateTime appointmentEnd = appointmentStart.plusMinutes(5);
            
            Appointment appointment = new Appointment();
            appointment.setDoctor(doctor);
            appointment.setPatient(p);
            appointment.setStartTime(appointmentStart);
            appointment.setEndTime(appointmentEnd);
            appointment.setStatus("scheduled");
            appointment.setSource("test");
            appointment.setCreatedAt(OffsetDateTime.now());
            appointment.setUpdatedAt(OffsetDateTime.now());
            
            createdAppointments.add(appointment);
            appointmentIndex++;
        }
        
        // Создаем appointment для текущего пациента
        OffsetDateTime currentAppointmentStart = baseStart.plusMinutes(appointmentIndex * 10);
        OffsetDateTime currentAppointmentEnd = currentAppointmentStart.plusMinutes(5);
        
        Appointment currentAppointment = new Appointment();
        currentAppointment.setDoctor(doctor);
        currentAppointment.setPatient(patient);
        currentAppointment.setStartTime(currentAppointmentStart);
        currentAppointment.setEndTime(currentAppointmentEnd);
        currentAppointment.setStatus("scheduled");
        currentAppointment.setSource("test");
        currentAppointment.setCreatedAt(OffsetDateTime.now());
        currentAppointment.setUpdatedAt(OffsetDateTime.now());
        
        createdAppointments.add(currentAppointment);
        appointmentIndex++;
        
        // Создаем appointments для пациентов после текущего
        for (Patient p : afterPatients) {
            OffsetDateTime appointmentStart = baseStart.plusMinutes(appointmentIndex * 10);
            OffsetDateTime appointmentEnd = appointmentStart.plusMinutes(5);
            
            Appointment appointment = new Appointment();
            appointment.setDoctor(doctor);
            appointment.setPatient(p);
            appointment.setStartTime(appointmentStart);
            appointment.setEndTime(appointmentEnd);
            appointment.setStatus("scheduled");
            appointment.setSource("test");
            appointment.setCreatedAt(OffsetDateTime.now());
            appointment.setUpdatedAt(OffsetDateTime.now());
            
            createdAppointments.add(appointment);
            appointmentIndex++;
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
        
        // Пересобираем очереди для всех задействованных пациентов через Redis
        for (Long pid : allPatientIds) {
            redisQueueService.buildQueueForToday(pid);
        }
        
        // Получаем полную очередь к врачу
        List<QueueEntryDto> fullQueue = redisQueueService.getQueueByDoctor(doctorId);
        
        // Получаем позицию текущего пациента
        Integer currentPosition = redisQueueService.getPatientPosition(patientId, doctorId);
        
        // Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", String.format("Создана тестовая очередь: %d пациентов перед, текущий пациент, %d пациентов после", 
            patientsBefore, patientsAfter));
        response.put("patientId", patientId);
        response.put("doctorId", doctorId);
        response.put("currentPosition", currentPosition);
        response.put("appointmentsCreated", savedAppointments.size());
        response.put("patientsBefore", patientsBefore);
        response.put("patientsAfter", patientsAfter);
        response.put("totalPatientsInQueue", fullQueue.size());
        
        // Информация о созданных appointments
        List<Map<String, Object>> appointmentsInfo = new ArrayList<>();
        for (Appointment apt : savedAppointments) {
            Map<String, Object> aptInfo = new HashMap<>();
            aptInfo.put("id", apt.getId());
            aptInfo.put("patientId", apt.getPatient().getId());
            aptInfo.put("doctorId", apt.getDoctor().getId());
            aptInfo.put("startTime", apt.getStartTime());
            aptInfo.put("endTime", apt.getEndTime());
            aptInfo.put("status", apt.getStatus());
            aptInfo.put("isCurrentPatient", patientId.equals(apt.getPatient().getId()));
            appointmentsInfo.add(aptInfo);
        }
        response.put("appointments", appointmentsInfo);
        
        // Полная очередь
        response.put("fullQueue", fullQueue);
        
        // Информация о позициях
        Map<String, Object> positionInfo = new HashMap<>();
        positionInfo.put("currentPatientId", patientId);
        positionInfo.put("currentPosition", currentPosition);
        positionInfo.put("patientsBeforeCount", currentPosition != null ? currentPosition : 0);
        positionInfo.put("patientsAfterCount", currentPosition != null ? fullQueue.size() - currentPosition - 1 : 0);
        response.put("positionInfo", positionInfo);

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
     * Получает всех пациентов в очередях, где есть текущий пациент
     * Возвращает полные очереди к каждому врачу, где находится указанный пациент
     * 
     * @param patientId ID текущего пациента
     * @return Полные очереди к врачам, где есть этот пациент
     */
    @GetMapping("/patient/{patientId}/all-queues")
    public ResponseEntity<Map<String, Object>> getAllPatientsInQueues(@PathVariable Long patientId) {
        // Проверяем, существует ли пациент
        Patient patient = patientRepository.findById(patientId)
                .orElse(null);
        
        if (patient == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент с ID " + patientId + " не найден"));
        }

        // Получаем все очереди пациента (врачи, к которым он записан)
        List<QueueEntryDto> patientQueues = redisQueueService.getQueuesByPatient(patientId);
        
        // Собираем уникальные ID врачей
        java.util.Set<Long> doctorIds = patientQueues.stream()
                .map(QueueEntryDto::getDoctorId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        
        // Для каждого врача получаем полную очередь
        List<Map<String, Object>> fullQueues = new ArrayList<>();
        for (Long doctorId : doctorIds) {
            List<QueueEntryDto> fullQueue = redisQueueService.getQueueByDoctor(doctorId);
            
            // Находим позицию текущего пациента в этой очереди
            Integer currentPatientPosition = redisQueueService.getPatientPosition(patientId, doctorId);
            
            Map<String, Object> queueInfo = new HashMap<>();
            queueInfo.put("doctorId", doctorId);
            queueInfo.put("queue", fullQueue);
            queueInfo.put("queueSize", fullQueue.size());
            queueInfo.put("currentPatientId", patientId);
            queueInfo.put("currentPatientPosition", currentPatientPosition);
            
            // Разделяем пациентов на тех, кто перед текущим, и тех, кто после
            List<QueueEntryDto> beforeCurrent = new ArrayList<>();
            List<QueueEntryDto> afterCurrent = new ArrayList<>();
            QueueEntryDto currentPatientEntry = null;
            
            for (QueueEntryDto entry : fullQueue) {
                if (patientId.equals(entry.getPatientId())) {
                    currentPatientEntry = entry;
                } else if (currentPatientPosition != null) {
                    if (entry.getPosition() < currentPatientPosition) {
                        beforeCurrent.add(entry);
                    } else if (entry.getPosition() > currentPatientPosition) {
                        afterCurrent.add(entry);
                    }
                }
            }
            
            queueInfo.put("patientsBefore", beforeCurrent);
            queueInfo.put("patientsAfter", afterCurrent);
            queueInfo.put("currentPatient", currentPatientEntry);
            queueInfo.put("patientsBeforeCount", beforeCurrent.size());
            queueInfo.put("patientsAfterCount", afterCurrent.size());
            
            fullQueues.add(queueInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("patientId", patientId);
        response.put("fullQueues", fullQueues);
        response.put("doctorsCount", doctorIds.size());
        response.put("totalQueues", fullQueues.size());

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

    /**
     * Пропускает всех пациентов перед текущим в очереди к врачу
     * Удаляет из очереди всех пациентов, которые находятся перед указанным пациентом
     * 
     * @param patientId ID текущего пациента
     * @param doctorId ID врача
     * @return Информация об обновлённой очереди
     */
    @PostMapping("/skip-others/{patientId}/doctor/{doctorId}")
    public ResponseEntity<Map<String, Object>> skipOthersInQueue(
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
        Integer currentPosition = redisQueueService.getPatientPosition(patientId, doctorId);
        
        if (currentPosition == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент не находится в очереди к этому врачу"));
        }

        // Получаем полную очередь к врачу
        List<QueueEntryDto> fullQueue = redisQueueService.getQueueByDoctor(doctorId);
        
        // Находим всех пациентов перед текущим
        List<QueueEntryDto> patientsToSkip = fullQueue.stream()
                .filter(entry -> entry.getPosition() < currentPosition)
                .sorted((e1, e2) -> Integer.compare(e2.getPosition(), e1.getPosition())) // Сортируем от большего к меньшему
                .collect(java.util.stream.Collectors.toList());
        
        if (patientsToSkip.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Нет пациентов перед текущим в очереди");
            response.put("patientId", patientId);
            response.put("doctorId", doctorId);
            response.put("currentPosition", currentPosition);
            response.put("skippedCount", 0);
            response.put("updatedQueue", fullQueue);
            return ResponseEntity.ok(response);
        }

        // Удаляем всех пациентов перед текущим (в обратном порядке, чтобы не нарушить позиции)
        List<Map<String, Object>> skippedPatients = new ArrayList<>();
        int skippedCount = 0;
        
        for (QueueEntryDto entryToSkip : patientsToSkip) {
            Long skipPatientId = entryToSkip.getPatientId();
            Integer skipPosition = entryToSkip.getPosition();
            
            // Находим активный appointment для пропускаемого пациента
            List<Appointment> skipAppointments = appointmentRepository.findByPatientId(skipPatientId);
            Appointment skipAppointment = skipAppointments.stream()
                    .filter(a -> doctorId.equals(a.getDoctor().getId()))
                    .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                    .findFirst()
                    .orElse(null);
            
            // Удаляем из очереди
            boolean removed = redisQueueService.removeFromQueue(skipPatientId, doctorId);
            
            if (removed) {
                skippedCount++;
                
                Map<String, Object> skippedInfo = new HashMap<>();
                skippedInfo.put("patientId", skipPatientId);
                skippedInfo.put("position", skipPosition);
                skippedInfo.put("appointmentId", skipAppointment != null ? skipAppointment.getId() : null);
                skippedPatients.add(skippedInfo);
                
                // Помечаем appointment как "no_show" если есть
                if (skipAppointment != null) {
                    appointmentService.updateAppointmentStatus(skipAppointment.getId(), "no_show");
                }
            }
        }

        // Получаем обновлённую очередь
        List<QueueEntryDto> updatedQueue = redisQueueService.getQueueByDoctor(doctorId);
        
        // Получаем новую позицию текущего пациента
        Integer newPosition = redisQueueService.getPatientPosition(patientId, doctorId);

        // Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Пропущено " + skippedCount + " пациентов перед текущим");
        response.put("patientId", patientId);
        response.put("doctorId", doctorId);
        response.put("positionBefore", currentPosition);
        response.put("positionAfter", newPosition);
        response.put("skippedCount", skippedCount);
        response.put("skippedPatients", skippedPatients);
        response.put("updatedQueue", updatedQueue);
        response.put("queueSize", updatedQueue.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Пропускает всех пациентов перед текущим во всех очередях, где он находится
     * 
     * @param patientId ID текущего пациента
     * @return Информация об обновлённых очередях
     */
    @PostMapping("/skip-others-all/{patientId}")
    public ResponseEntity<Map<String, Object>> skipOthersInAllQueues(@PathVariable Long patientId) {
        // Проверяем, существует ли пациент
        Patient patient = patientRepository.findById(patientId)
                .orElse(null);
        
        if (patient == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пациент с ID " + patientId + " не найден"));
        }

        // Получаем все очереди пациента
        List<QueueEntryDto> patientQueues = redisQueueService.getQueuesByPatient(patientId);
        
        // Собираем уникальные ID врачей
        java.util.Set<Long> doctorIds = patientQueues.stream()
                .map(QueueEntryDto::getDoctorId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        
        List<Map<String, Object>> results = new ArrayList<>();
        int totalSkipped = 0;
        
        // Для каждого врача пропускаем пациентов перед текущим
        for (Long doctorId : doctorIds) {
            try {
                // Используем существующий метод для пропуска
                ResponseEntity<Map<String, Object>> skipResponse = skipOthersInQueue(patientId, doctorId);
                
                if (skipResponse.getStatusCode().is2xxSuccessful() && skipResponse.getBody() != null) {
                    Map<String, Object> body = skipResponse.getBody();
                    Map<String, Object> result = new HashMap<>();
                    result.put("doctorId", doctorId);
                    result.put("skippedCount", body.get("skippedCount"));
                    result.put("positionBefore", body.get("positionBefore"));
                    result.put("positionAfter", body.get("positionAfter"));
                    result.put("success", true);
                    results.add(result);
                    
                    if (body.get("skippedCount") instanceof Number) {
                        totalSkipped += ((Number) body.get("skippedCount")).intValue();
                    }
                } else {
                    Map<String, Object> result = new HashMap<>();
                    result.put("doctorId", doctorId);
                    result.put("success", false);
                    result.put("error", "Не удалось пропустить пациентов");
                    results.add(result);
                }
            } catch (Exception e) {
                Map<String, Object> result = new HashMap<>();
                result.put("doctorId", doctorId);
                result.put("success", false);
                result.put("error", e.getMessage());
                results.add(result);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("patientId", patientId);
        response.put("doctorsProcessed", doctorIds.size());
        response.put("totalSkipped", totalSkipped);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }
}

