package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pin122.kursovaya.model.Appointment;
import pin122.kursovaya.model.Doctor;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий приёмов и слотов расписания (записи на приём).
 * <p>
 * Содержит запросы для кабинета врача и пациента, отчётов, напоминаний, очереди на день,
 * агрегатов по статусам и вспомогательных операций обслуживания данных.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Возвращает все приёмы, назначенные указанному врачу (без явной подгрузки связей).
     *
     * @param doctorId идентификатор врача
     * @return список приёмов
     */
    List<Appointment> findByDoctorId(Long doctorId);

    /**
     * Возвращает приёмы врача с жадной подгрузкой пациента, пользователей, врача, специализаций и кабинета.
     * <p>
     * JPQL-запрос: {@code DISTINCT} выборка {@code Appointment} с {@code LEFT JOIN FETCH} по цепочкам
     * {@code patient}, {@code patient.user}, {@code doctor}, {@code doctor.user}, {@code doctor.specializations},
     * {@code specialization}, {@code room}; фильтр {@code doctor.id}; сортировка по убыванию {@code startTime}.
     *
     * @param doctorId идентификатор врача
     * @return список приёмов с заполненными связями
     */
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization " +
           "LEFT JOIN FETCH a.room " +
           "WHERE a.doctor.id = :doctorId " +
           "ORDER BY a.startTime DESC")
    List<Appointment> findByDoctorIdWithDetails(@Param("doctorId") Long doctorId);

    /**
     * Возвращает все приёмы указанного пациента (без расширенной подгрузки графа).
     *
     * @param patientId идентификатор пациента
     * @return список приёмов
     */
    List<Appointment> findByPatientId(Long patientId);

    /**
     * Возвращает приёмы пациента с подгрузкой пациента, пользователей, врача, специализаций и кабинета.
     * <p>
     * JPQL-запрос: аналогично {@link #findByDoctorIdWithDetails(Long)}, но фильтр по {@code patient.id}
     * и сортировка по убыванию {@code startTime}.
     *
     * @param patientId идентификатор пациента
     * @return список приёмов с заполненными связями
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization " +
           "LEFT JOIN FETCH a.room " +
           "WHERE a.patient.id = :patientId " +
           "ORDER BY a.startTime DESC")
    List<Appointment> findByPatientIdWithDetails(@Param("patientId") Long patientId);

    /**
     * Возвращает приёмы врача за календарный интервал [{@code startOfDay}, {@code startOfNextDay}).
     * <p>
     * JPQL-запрос: фильтр по {@code doctor.id} и полуинтервалу времени начала приёма, сортировка по {@code startTime}.
     *
     * @param doctorId        идентификатор врача
     * @param startOfDay      нижняя граница времени (включительно)
     * @param startOfNextDay  верхняя граница времени (исключительно)
     * @return список приёмов за сутки
     */
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime")
    List<Appointment> findByDoctorIdAndDate(@Param("doctorId") Long doctorId,
                                             @Param("startOfDay") OffsetDateTime startOfDay,
                                             @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает приёмы врача за сутки с подгрузкой услуги, чтобы избежать проблемы N+1 при отображении записи.
     * <p>
     * JPQL-запрос: {@code DISTINCT} и {@code LEFT JOIN FETCH a.service}, фильтр по врачу и полуинтервалу {@code startTime}.
     *
     * @param doctorId        идентификатор врача
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов с загруженной услугой
     */
    @Query("SELECT DISTINCT a FROM Appointment a LEFT JOIN FETCH a.service WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime")
    List<Appointment> findByDoctorIdAndDateFetchingService(@Param("doctorId") Long doctorId,
                                                           @Param("startOfDay") OffsetDateTime startOfDay,
                                                           @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает предстоящие приёмы пациента: не завершённые и не отменённые, время начала не раньше {@code currentTime}.
     * <p>
     * JPQL-запрос: {@code patient.id}, привязанный пациент, {@code startTime >= currentTime},
     * статус не в {@code completed}/{@code cancelled}, сортировка по возрастанию {@code startTime}.
     *
     * @param patientId   идентификатор пациента
     * @param currentTime текущий момент (или нижняя граница «предстоящих»)
     * @return список предстоящих приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.patient IS NOT NULL " +
           "AND a.startTime >= :currentTime " +
           "AND a.status NOT IN ('completed', 'cancelled') " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findUpcomingAppointmentsByPatient(@Param("patientId") Long patientId,
                                                         @Param("currentTime") OffsetDateTime currentTime);

    /**
     * Возвращает предстоящие приёмы пациента к конкретному врачу с теми же условиями статуса и времени.
     * <p>
     * JPQL-запрос: дополнительно {@code doctor.id = :doctorId}.
     *
     * @param patientId   идентификатор пациента
     * @param doctorId    идентификатор врача
     * @param currentTime нижняя граница по времени начала
     * @return список предстоящих приёмов к выбранному врачу
     */
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.doctor.id = :doctorId " +
           "AND a.patient IS NOT NULL " +
           "AND a.startTime >= :currentTime " +
           "AND a.status NOT IN ('completed', 'cancelled') " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findUpcomingAppointmentsByPatientAndDoctor(@Param("patientId") Long patientId,
                                                                   @Param("doctorId") Long doctorId,
                                                                   @Param("currentTime") OffsetDateTime currentTime);

    /**
     * Выполняет поиск врачей по подстроке в ФИО пользователя и в названии/коде специализации (без учёта регистра).
     * <p>
     * JPQL-запрос: выборка сущностей {@link Doctor} с соединениями по пользователю и специализациям и набором условий {@code LIKE}
     * по параметру {@code :query}. Сигнатура метода содержит параметры {@code start}, {@code end}, {@code doctorId} на уровне API Spring Data.
     *
     * @param start    параметр метода (использование определяется вызывающим кодом)
     * @param end      параметр метода (использование определяется вызывающим кодом)
     * @param doctorId параметр метода (использование определяется вызывающим кодом)
     * @return список найденных врачей согласно JPQL-запросу
     */
    @Query("""
        SELECT d FROM Doctor d
        JOIN d.user u
        LEFT JOIN DoctorSpecialization ds ON d.id = ds.doctor.id
        LEFT JOIN Specialization s ON ds.specialization.id = s.id
        WHERE 
            LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(u.middleName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.code) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    List<Doctor> checkAvailability(Date start, Date end, Long doctorId);

    /**
     * Возвращает приёмы со статусами {@code scheduled} или {@code confirmed}, у которых время окончания уже прошло относительно {@code currentTime}.
     * <p>
     * JPQL-запрос: фильтр по статусам, {@code endTime < currentTime}, наличие пациента.
     *
     * @param currentTime момент сравнения («сейчас»)
     * @return список просроченных по времени окончания приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.status IN ('scheduled', 'confirmed') " +
           "AND a.endTime < :currentTime AND a.patient IS NOT NULL")
    List<Appointment> findExpiredAppointments(@Param("currentTime") OffsetDateTime currentTime);

    /**
     * Подсчитывает количество приёмов, связанных с пациентом.
     * <p>
     * JPQL-запрос: {@code COUNT} по {@code patient.id}.
     *
     * @param patientId идентификатор пациента
     * @return число приёмов
     */
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :patientId")
    Long countByPatientId(@Param("patientId") Long patientId);

    /**
     * Обнуляет связь приёмов с пациентом для всех записей данного пациента (отвязка без удаления приёмов).
     * <p>
     * JPQL-запрос: {@code UPDATE} поля {@code patient} в {@code null} по {@code patient.id}.
     *
     * @param patientId идентификатор пациента
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Appointment a SET a.patient = null WHERE a.patient.id = :patientId")
    void clearPatientFromAppointments(@Param("patientId") Long patientId);

    /**
     * Возвращает все приёмы пациента в статусе {@code scheduled}, по возрастанию времени начала.
     * <p>
     * JPQL-запрос: {@code patient.id} и {@code status = 'scheduled'}.
     *
     * @param patientId идентификатор пациента
     * @return список запланированных приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.status = 'scheduled' " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findScheduledAppointmentsByPatient(@Param("patientId") Long patientId);

    /**
     * Возвращает приёмы за календарные сутки с подгрузкой пациента, пользователей, врача и кабинета (для отчётов).
     * <p>
     * JPQL-запрос: полуинтервал {@code startTime}, только записи с назначенным пациентом, сортировка по {@code startTime}.
     *
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов с деталями для отчёта
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.startTime >= :startOfDay AND a.startTime < :startOfNextDay " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findAllByDate(@Param("startOfDay") OffsetDateTime startOfDay,
                                    @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает приёмы конкретного врача за сутки с подгрузкой связей (для отчётов).
     * <p>
     * JPQL-запрос: как {@link #findAllByDate(OffsetDateTime, OffsetDateTime)}, плюс {@code doctor.id = :doctorId}.
     *
     * @param doctorId        идентификатор врача
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов врача за день
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findByDoctorIdAndDateForReport(@Param("doctorId") Long doctorId,
                                                      @Param("startOfDay") OffsetDateTime startOfDay,
                                                      @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает приёмы за произвольный полуинтервал времени с подгрузкой связей (для отчётов).
     * <p>
     * JPQL-запрос: {@code startTime} в [{@code startDate}, {@code endDate}), с пациентом, сортировка по началу.
     *
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (исключительно)
     * @return список приёмов за период
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findAllByDateRange(@Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate);

    /**
     * Возвращает приёмы врача за период с подгрузкой связей (для отчётов).
     * <p>
     * JPQL-запрос: {@code doctor.id} и полуинтервал {@code startTime}, только с пациентом.
     *
     * @param doctorId  идентификатор врача
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (исключительно)
     * @return список приёмов врача за период
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user du " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findByDoctorIdAndDateRangeForReport(@Param("doctorId") Long doctorId,
                                                           @Param("startDate") OffsetDateTime startDate,
                                                           @Param("endDate") OffsetDateTime endDate);

    /**
     * Группирует приёмы за период по статусу и возвращает пары «статус — количество».
     * <p>
     * JPQL-запрос: {@code SELECT status, COUNT} с условием по полуинтервалу {@code startTime} и наличию пациента, {@code GROUP BY status}.
     *
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (исключительно)
     * @return список массивов: [0] — строка статуса, [1] — число приёмов ({@code Long})
     */
    @Query("SELECT a.status, COUNT(a) FROM Appointment a " +
           "WHERE a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "GROUP BY a.status")
    List<Object[]> countByStatusAndDateRange(@Param("startDate") OffsetDateTime startDate,
                                              @Param("endDate") OffsetDateTime endDate);

    /**
     * Группирует приёмы врача за период по статусу (количество по каждому статусу).
     * <p>
     * JPQL-запрос: как {@link #countByStatusAndDateRange(OffsetDateTime, OffsetDateTime)}, с фильтром {@code doctor.id}.
     *
     * @param doctorId  идентификатор врача
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (исключительно)
     * @return список пар «статус — количество»
     */
    @Query("SELECT a.status, COUNT(a) FROM Appointment a " +
           "WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startDate AND a.startTime < :endDate " +
           "AND a.patient IS NOT NULL " +
           "GROUP BY a.status")
    List<Object[]> countByStatusAndDoctorAndDateRange(@Param("doctorId") Long doctorId,
                                                       @Param("startDate") OffsetDateTime startDate,
                                                       @Param("endDate") OffsetDateTime endDate);

    /**
     * Возвращает запланированные или подтверждённые приёмы в полуинтервале времени с подгрузкой связей (напоминания).
     * <p>
     * JPQL-запрос: {@code startTime} в [{@code startTime}, {@code endTime}), статусы {@code scheduled}/{@code confirmed},
     * с пациентом; жадная подгрузка пациента, пользователя, врача, специализаций и кабинета.
     *
     * @param startTime начало окна (включительно)
     * @param endTime   конец окна (исключительно)
     * @return отсортированный по началу список приёмов
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user pu " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization s " +
           "LEFT JOIN FETCH a.room r " +
           "WHERE a.startTime >= :startTime AND a.startTime < :endTime " +
           "AND a.status IN ('scheduled', 'confirmed') " +
           "AND a.patient IS NOT NULL " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findScheduledAppointmentsBetween(@Param("startTime") OffsetDateTime startTime,
                                                        @Param("endTime") OffsetDateTime endTime);

    /**
     * Возвращает все приёмы (слоты), начинающиеся в заданном полуинтервале времени, с пациентом и врачом (очередь на день).
     * <p>
     * JPQL-запрос: {@code startTime} в [{@code startTime}, {@code endTime}), {@code LEFT JOIN FETCH} пациента и врача, сортировка по началу.
     *
     * @param startTime начало интервала (включительно)
     * @param endTime   конец интервала (исключительно)
     * @return список приёмов за интервал
     */
    @Query("SELECT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH a.doctor d " +
           "WHERE a.startTime >= :startTime AND a.startTime < :endTime " +
           "ORDER BY a.startTime ASC")
    List<Appointment> findByStartTimeBetween(@Param("startTime") OffsetDateTime startTime,
                                              @Param("endTime") OffsetDateTime endTime);

    /**
     * Возвращает приёмы с указанным строковым статусом (метод по соглашению Spring Data).
     *
     * @param status код статуса приёма
     * @return список приёмов
     */
    List<Appointment> findByStatus(String status);

    /**
     * Возвращает приём по идентификатору с полной подгрузкой графа для уведомлений и карточки приёма.
     * <p>
     * JPQL-запрос: {@code DISTINCT}, множественные {@code LEFT JOIN FETCH} (пациент, пользователи, врач, специализации, кабинет, диагноз, услуга), фильтр по {@code id}.
     *
     * @param id идентификатор приёма
     * @return контейнер с приёмом, если найден; иначе пустой {@link Optional}
     */
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.patient p " +
           "LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH a.doctor d " +
           "LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH d.specializations ds " +
           "LEFT JOIN FETCH ds.specialization " +
           "LEFT JOIN FETCH a.room " +
           "LEFT JOIN FETCH a.diagnosis " +
           "LEFT JOIN FETCH a.service " +
           "WHERE a.id = :id")
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    /**
     * Возвращает приёмы врача с заданным статусом, по возрастанию времени начала.
     * <p>
     * JPQL-запрос: {@code status} и {@code doctor.id}.
     *
     * @param status   статус приёма
     * @param doctorId идентификатор врача
     * @return отсортированный список приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.status = :status AND a.doctor.id = :doctorId ORDER BY a.startTime ASC")
    List<Appointment> findByStatusAndDoctorId(@Param("status") String status, @Param("doctorId") Long doctorId);

    /**
     * Возвращает приёмы с указанным статусом за календарные сутки.
     * <p>
     * JPQL-запрос: {@code status} и полуинтервал {@code startTime}.
     *
     * @param status          статус приёма
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов за день
     */
    @Query("SELECT a FROM Appointment a WHERE a.status = :status " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByStatusAndDate(@Param("status") String status,
                                          @Param("startOfDay") OffsetDateTime startOfDay,
                                          @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает приёмы врача с заданным статусом за указанные сутки.
     * <p>
     * JPQL-запрос: {@code status}, {@code doctor.id} и полуинтервал {@code startTime}.
     *
     * @param status          статус приёма
     * @param doctorId        идентификатор врача
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.status = :status AND a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByStatusAndDoctorIdAndDate(@Param("status") String status,
                                                      @Param("doctorId") Long doctorId,
                                                      @Param("startOfDay") OffsetDateTime startOfDay,
                                                      @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает все приёмы врача за сутки независимо от статуса.
     * <p>
     * JPQL-запрос: {@code doctor.id} и полуинтервал {@code startTime}.
     *
     * @param doctorId        идентификатор врача
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов
     */
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByDoctorIdAndDateRange(@Param("doctorId") Long doctorId,
                                                  @Param("startOfDay") OffsetDateTime startOfDay,
                                                  @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает все приёмы за сутки по всем врачам.
     * <p>
     * JPQL-запрос: только полуинтервал {@code startTime}.
     *
     * @param startOfDay      начало суток (включительно)
     * @param startOfNextDay  начало следующих суток (исключительно)
     * @return список приёмов за день
     */
    @Query("SELECT a FROM Appointment a WHERE a.startTime >= :startOfDay AND a.startTime < :startOfNextDay ORDER BY a.startTime ASC")
    List<Appointment> findByDateRange(@Param("startOfDay") OffsetDateTime startOfDay,
                                      @Param("startOfNextDay") OffsetDateTime startOfNextDay);

    /**
     * Возвращает уникальные календарные даты (в часовом поясе UTC), в которые у врача есть свободный для записи слот:
     * без назначенного пациента, в будущем относительно {@code nowTs}, в окне [{@code fromTs}, {@code toTs}).
     * При {@code hasServiceFilter == true} учитываются слоты без услуги или с услугой {@code serviceId} (как в логике доступных приёмов).
     * <p>
     * Нативный SQL: {@code DISTINCT CAST((start_time AT TIME ZONE 'UTC') AS date)} из таблицы {@code appointments} с фильтрами по врачу,
     * пустому {@code patient_id}, времени и опционально услуге.
     * <p>
     * Драйвер JDBC возвращает элементы типа {@link java.sql.Date}; Spring Data не приводит их к {@link java.time.LocalDate} без конвертера.
     *
     * @param doctorId         идентификатор врача
     * @param nowTs            текущий момент (слоты строго позже этого времени)
     * @param fromTs           нижняя граница окна поиска (включительно)
     * @param toTs             верхняя граница окна поиска (исключительно)
     * @param hasServiceFilter если {@code true}, применяется фильтр по услуге (см. описание запроса)
     * @param serviceId        идентификатор услуги для фильтра при включённом {@code hasServiceFilter}
     * @return отсортированный список дат в виде {@link java.sql.Date}
     */
    @Query(value = """
            SELECT DISTINCT CAST((a.start_time AT TIME ZONE 'UTC') AS date)
            FROM appointments a
            WHERE a.doctor_id = :doctorId
              AND a.patient_id IS NULL
              AND a.start_time > :nowTs
              AND a.start_time >= :fromTs
              AND a.start_time < :toTs
              AND (
                :hasServiceFilter = FALSE
                OR a.service_id IS NULL
                OR a.service_id = :serviceId
              )
            ORDER BY 1
            """, nativeQuery = true)
    List<java.sql.Date> findDistinctBookableDates(
            @Param("doctorId") Long doctorId,
            @Param("nowTs") OffsetDateTime nowTs,
            @Param("fromTs") OffsetDateTime fromTs,
            @Param("toTs") OffsetDateTime toTs,
            @Param("hasServiceFilter") boolean hasServiceFilter,
            @Param("serviceId") Long serviceId);
}
