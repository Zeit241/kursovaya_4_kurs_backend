package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.model.Patient;
import pin122.kursovaya.model.QueueEntry;
import pin122.kursovaya.repository.AppointmentRepository;
import pin122.kursovaya.repository.DoctorRepository;
import pin122.kursovaya.repository.PatientRepository;
import pin122.kursovaya.repository.QueueEntryRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Очередь пациентов в реляционной БД ({@link QueueEntry}): позиции, построение по приёмам, сдвиг после удаления.
 *
 * <p>Отдельно от очереди в Redis ({@link RedisQueueService}).
 */
@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * @param queueEntryRepository   хранение записей очереди
     * @param patientRepository      загрузка пациента
     * @param doctorRepository       загрузка врача
     * @param appointmentRepository  привязка к приёму при добавлении в очередь
     */
    public QueueService(QueueEntryRepository queueEntryRepository,
                       PatientRepository patientRepository,
                       DoctorRepository doctorRepository,
                       AppointmentRepository appointmentRepository) {
        this.queueEntryRepository = queueEntryRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Очередь к врачу, упорядоченная по возрастанию позиции.
     *
     * @param doctorId идентификатор врача
     * @return список {@link QueueEntryDto}
     */
    public List<QueueEntryDto> getQueueByDoctor(Long doctorId) {
        return queueEntryRepository.findByDoctorIdOrderByPositionAsc(doctorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Ищет элемент очереди по идентификатору.
     *
     * @param id первичный ключ
     * @return {@link QueueEntryDto}, если найден
     */
    public Optional<QueueEntryDto> getQueueEntryById(Long id) {
        return queueEntryRepository.findById(id)
                .map(this::mapToDto);
    }

    /**
     * Сохраняет запись очереди.
     *
     * @param entry сущность
     * @return DTO после сохранения
     */
    public QueueEntryDto saveQueueEntry(QueueEntry entry) {
        QueueEntry saved = queueEntryRepository.save(entry);
        return mapToDto(saved);
    }

    /**
     * Удаляет запись очереди по id.
     *
     * @param id первичный ключ
     */
    public void deleteQueueEntry(Long id) {
        queueEntryRepository.deleteById(id);
    }

    /**
     * Добавляет пациента в конец очереди к врачу с опциональной привязкой к приёму.
     *
     * @param patientId     идентификатор пациента
     * @param doctorId      идентификатор врача
     * @param appointmentId идентификатор приёма или {@code null}
     * @return созданный {@link QueueEntryDto}
     * @throws IllegalStateException    если пациент уже стоит в очереди к этому врачу
     * @throws IllegalArgumentException если пациент или врач не найдены
     */
    @Transactional
    public QueueEntryDto addPatientToQueue(Long patientId, Long doctorId, Long appointmentId) {
        // Проверяем, не находится ли пациент уже в очереди
        Optional<QueueEntry> existingEntry = queueEntryRepository.findByPatientIdAndDoctorId(patientId, doctorId);
        if (existingEntry.isPresent()) {
            throw new IllegalStateException("Пациент уже находится в очереди к этому врачу");
        }

        // Получаем пациента и врача
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Пациент не найден"));
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Врач не найден"));

        // Определяем следующую позицию в очереди
        Integer maxPosition = queueEntryRepository.findMaxPositionByDoctorId(doctorId);
        Integer nextPosition = (maxPosition == null) ? 0 : maxPosition + 1;

        // Создаем новую запись в очереди
        QueueEntry queueEntry = new QueueEntry();
        queueEntry.setPatient(patient);
        queueEntry.setDoctor(doctor);
        queueEntry.setPosition(nextPosition);
        queueEntry.setLastUpdated(OffsetDateTime.now());
        if (appointmentId != null) {
            // Если есть appointment, устанавливаем связь
            Optional<Appointment> appointment = appointmentRepository.findById(appointmentId);
            appointment.ifPresent(queueEntry::setAppointment);
        }

        QueueEntry saved = queueEntryRepository.save(queueEntry);
        return mapToDto(saved);
    }

    /**
     * Возвращает запись очереди для пары пациент–врач.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача
     * @return {@link QueueEntryDto}, если запись есть
     */
    public Optional<QueueEntryDto> getPatientQueuePosition(Long patientId, Long doctorId) {
        return queueEntryRepository.findByPatientIdAndDoctorId(patientId, doctorId)
                .map(this::mapToDto);
    }

    /**
     * Определяет, является ли пациент «следующим» (позиция 0 или нет записей с меньшей позицией перед ним).
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача
     * @return {@code true}, если пациент следующий в очереди
     */
    public boolean isPatientNextInQueue(Long patientId, Long doctorId) {
        Optional<QueueEntry> patientEntry = queueEntryRepository.findByPatientIdAndDoctorId(patientId, doctorId);
        if (patientEntry.isEmpty()) {
            return false;
        }

        QueueEntry entry = patientEntry.get();
        Integer position = entry.getPosition();

        // Если позиция 0, значит пациент первый в очереди
        if (position == 0) {
            return true;
        }

        // Проверяем, есть ли записи перед текущей позицией
        List<QueueEntry> entriesBefore = queueEntryRepository.findBeforePosition(doctorId, position);
        
        // Если нет записей перед текущей, значит пациент следующий
        return entriesBefore.isEmpty();
    }

    /**
     * Все очереди, в которых участвует пациент (по разным врачам).
     *
     * @param patientId идентификатор пациента
     * @return список {@link QueueEntryDto}
     */
    public List<QueueEntryDto> getQueuesByPatient(Long patientId) {
        return queueEntryRepository.findByPatientId(patientId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Удаляет запись очереди для пары пациент–врач без пересчёта позиций остальных.
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача
     */
    @Transactional
    public void removePatientFromQueue(Long patientId, Long doctorId) {
        Optional<QueueEntry> entry = queueEntryRepository.findByPatientIdAndDoctorId(patientId, doctorId);
        if (entry.isPresent()) {
            queueEntryRepository.delete(entry.get());
            // Можно добавить логику пересчета позиций, если нужно
        }
    }

    /**
     * Удаляет пациента из очереди и уменьшает позиции всех, кто стоял за ним (логика аналогична Lua в Redis).
     *
     * @param patientId идентификатор пациента
     * @param doctorId  идентификатор врача
     * @return {@code true}, если запись была найдена и удалена
     */
    @Transactional
    public boolean removeFromQueueAndShift(Long patientId, Long doctorId) {
        Optional<QueueEntry> entryOpt = queueEntryRepository.findByPatientIdAndDoctorId(patientId, doctorId);
        if (entryOpt.isEmpty()) {
            return false;
        }
        
        QueueEntry entry = entryOpt.get();
        Integer position = entry.getPosition();
        
        // Удаляем пациента из очереди
        queueEntryRepository.delete(entry);
        
        // Сдвигаем всех пациентов с позицией > position: уменьшаем позицию на 1
        queueEntryRepository.shiftPositionsAfter(doctorId, position);
        
        return true;
    }

    /**
     * Пересобирает записи очереди пациента по предстоящим приёмам: группировка по врачу, позиция по времени среди всех пациентов врача.
     *
     * <p>Приёмы сильно ушедшие в прошлое отсекаются относительно {@code now - 20} минут.
     *
     * @param patientId идентификатор пациента
     * @return созданные или обновлённые {@link QueueEntryDto}
     */
    @Transactional
    public List<QueueEntryDto> buildQueueFromAppointments(Long patientId) {
        System.out.println("DEBUG QueueService: buildQueueFromAppointments вызван для patientId=" + patientId);
        OffsetDateTime now = OffsetDateTime.now();
        // Вычитаем 20 минут для учета задержки (если сейчас на 20+ минут больше времени приема, пропускаем)
        OffsetDateTime cutoffTime = now.minusMinutes(20);
        
        // Для отладки: получаем все appointments пациента (без фильтра по времени)
        List<Appointment> allPatientAppointments = appointmentRepository.findByPatientId(patientId);
        System.out.println("DEBUG: Всего appointments для пациента " + patientId + ": " + allPatientAppointments.size());
        for (Appointment a : allPatientAppointments) {
            System.out.println("DEBUG: Appointment ID=" + a.getId() + 
                    ", startTime=" + a.getStartTime() + 
                    ", status=" + a.getStatus() + 
                    ", patient=" + (a.getPatient() != null ? a.getPatient().getId() : "null"));
        }
        
        // Получаем все предстоящие записи пациента
        // Исключаем appointments со статусами 'completed' и 'cancelled'
        List<Appointment> patientAppointments = appointmentRepository.findUpcomingAppointmentsByPatient(patientId, cutoffTime)
                .stream()
                .filter(a -> !"completed".equals(a.getStatus()) && !"cancelled".equals(a.getStatus()))
                .collect(Collectors.toList());
        
        System.out.println("DEBUG: Текущее время: " + now);
        System.out.println("DEBUG: Cutoff время (now - 20 мин): " + cutoffTime);
        System.out.println("DEBUG: Найдено активных appointments после фильтрации: " + patientAppointments.size());
        
        if (patientAppointments.isEmpty()) {
            System.out.println("DEBUG: Нет активных appointments для построения очереди");
            return List.of();
        }
        
        // Группируем appointments по врачам
        return patientAppointments.stream()
                .collect(Collectors.groupingBy(Appointment::getDoctor))
                .entrySet().stream()
                .flatMap(entry -> {
                    Doctor doctor = entry.getKey();
                    List<Appointment> doctorPatientAppointments = entry.getValue();
                    
                    // Сортируем по времени начала приема
                    doctorPatientAppointments.sort((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()));
                    
                    // Получаем все активные appointments к этому врачу (для расчета позиций)
                    // Находим все appointments всех пациентов к этому врачу
                    // Исключаем appointments со статусами 'completed' и 'cancelled'
                    List<Appointment> allDoctorAppointments = appointmentRepository
                            .findByDoctorId(doctor.getId()).stream()
                            .filter(a -> a.getPatient() != null 
                                    && a.getStartTime().isAfter(cutoffTime)
                                    && !"completed".equals(a.getStatus())
                                    && !"cancelled".equals(a.getStatus()))
                            .sorted((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()))
                            .collect(Collectors.toList());
                    
                    // Удаляем старые записи в очереди для этого врача и пациента
                    queueEntryRepository.findByPatientIdAndDoctorId(patientId, doctor.getId())
                            .ifPresent(queueEntryRepository::delete);
                    
                    // Создаем новые записи в очереди
                    List<QueueEntry> queueEntries = new java.util.ArrayList<>();
                    for (Appointment appointment : doctorPatientAppointments) {
                        // Рассчитываем позицию: сколько appointments (всех пациентов) имеют более раннее время
                        long position = allDoctorAppointments.stream()
                                .filter(a -> a.getStartTime().isBefore(appointment.getStartTime()))
                                .count();
                        
                        QueueEntry queueEntry = new QueueEntry();
                        queueEntry.setPatient(appointment.getPatient());
                        queueEntry.setDoctor(doctor);
                        queueEntry.setAppointment(appointment);
                        queueEntry.setPosition((int) position);
                        queueEntry.setLastUpdated(OffsetDateTime.now());
                        
                        queueEntries.add(queueEntry);
                    }
                    
                    // Сохраняем все записи
                    List<QueueEntry> saved = queueEntryRepository.saveAll(queueEntries);
                    return saved.stream().map(this::mapToDto);
                })
                .collect(Collectors.toList());
    }

    /**
     * После завершения приёма пересобирает очереди в БД для каждого пациента, у кого есть записи к этому врачу.
     *
     * @param doctorId идентификатор врача
     */
    @Transactional
    public void updateQueuesAfterAppointmentCompletion(Long doctorId) {
        System.out.println("DEBUG QueueService: Обновление очередей для врача " + doctorId + " после завершения приема");
        
        // Получаем все записи в очереди к этому врачу
        List<QueueEntry> allQueueEntries = queueEntryRepository.findByDoctorIdOrderByPositionAsc(doctorId);
        
        if (allQueueEntries.isEmpty()) {
            System.out.println("DEBUG QueueService: Нет записей в очереди для обновления");
            return;
        }
        
        // Группируем по пациентам
        java.util.Map<Long, List<QueueEntry>> entriesByPatient = allQueueEntries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    entry -> entry.getPatient() != null ? entry.getPatient().getId() : 0L
                ));
        
        // Обновляем очередь для каждого пациента
        for (Long patientId : entriesByPatient.keySet()) {
            if (patientId > 0) {
                System.out.println("DEBUG QueueService: Пересчет очереди для пациента " + patientId);
                buildQueueFromAppointments(patientId);
            }
        }
        
        System.out.println("DEBUG QueueService: Обновление очередей завершено");
    }
    
    /**
     * Уникальные идентификаторы пациентов в очереди к врачу (порядок по позиции в очереди).
     *
     * @param doctorId идентификатор врача
     * @return список id пациентов
     */
    public List<Long> getPatientIdsInQueue(Long doctorId) {
        return queueEntryRepository.findByDoctorIdOrderByPositionAsc(doctorId).stream()
                .filter(entry -> entry.getPatient() != null)
                .map(entry -> entry.getPatient().getId())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Преобразует сущность очереди в DTO с id врача, приёма и пациента.
     *
     * @param entry запись из БД
     * @return {@link QueueEntryDto}
     */
    private QueueEntryDto mapToDto(QueueEntry entry) {
        return new QueueEntryDto(
                entry.getId(),
                entry.getDoctor() != null ? entry.getDoctor().getId() : null,
                entry.getAppointment() != null ? entry.getAppointment().getId() : null,
                entry.getPatient() != null ? entry.getPatient().getId() : null,
                entry.getPosition(),
                entry.getLastUpdated()
        );
    }
}