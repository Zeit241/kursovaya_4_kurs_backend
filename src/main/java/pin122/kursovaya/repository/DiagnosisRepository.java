package pin122.kursovaya.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pin122.kursovaya.model.Diagnosis;

import java.util.Optional;

/**
 * Репозиторий диагнозов (справочник кодов/записей диагнозов).
 * <p>
 * Наследует операции {@link JpaRepository} и предоставляет поиск по коду.
 */
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    /**
     * Ищет диагноз по уникальному коду.
     *
     * @param code код диагноза
     * @return контейнер с диагнозом, если найден; иначе пустой {@link Optional}
     */
    Optional<Diagnosis> findByCode(String code);
}
