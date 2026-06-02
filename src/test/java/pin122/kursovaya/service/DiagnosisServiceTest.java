package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.DiagnosisDto;
import pin122.kursovaya.model.Diagnosis;
import pin122.kursovaya.repository.DiagnosisRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosisService - тесты справочника диагнозов")
class DiagnosisServiceTest {

    @Mock
    private DiagnosisRepository diagnosisRepository;

    @InjectMocks
    private DiagnosisService diagnosisService;

    private Diagnosis diagnosis;

    @BeforeEach
    void setUp() {
        diagnosis = new Diagnosis();
        diagnosis.setId(1L);
        diagnosis.setCode("J06.9");
        diagnosis.setName("Острая инфекция верхних дыхательных путей");
        diagnosis.setCategory("respiratory");
    }

    @Test
    @DisplayName("getAllDiagnoses возвращает список DTO")
    void getAllDiagnoses_returnsList() {
        when(diagnosisRepository.findAll()).thenReturn(List.of(diagnosis));

        List<DiagnosisDto> result = diagnosisService.getAllDiagnoses();

        assertEquals(1, result.size());
        assertEquals("J06.9", result.get(0).getCode());
        assertEquals("Острая инфекция верхних дыхательных путей", result.get(0).getName());
        verify(diagnosisRepository).findAll();
    }

    @Test
    @DisplayName("getDiagnosisById возвращает DTO для найденного диагноза")
    void getDiagnosisById_found() {
        when(diagnosisRepository.findById(1L)).thenReturn(Optional.of(diagnosis));

        Optional<DiagnosisDto> result = diagnosisService.getDiagnosisById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("respiratory", result.get().getCategory());
    }

    @Test
    @DisplayName("getDiagnosisById возвращает empty если диагноз не найден")
    void getDiagnosisById_notFound() {
        when(diagnosisRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<DiagnosisDto> result = diagnosisService.getDiagnosisById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("getDiagnosisByCode находит диагноз по коду МКБ")
    void getDiagnosisByCode_found() {
        when(diagnosisRepository.findByCode("J06.9")).thenReturn(Optional.of(diagnosis));

        Optional<DiagnosisDto> result = diagnosisService.getDiagnosisByCode("J06.9");

        assertTrue(result.isPresent());
        assertEquals("J06.9", result.get().getCode());
    }

    @Test
    @DisplayName("saveDiagnosis сохраняет и возвращает DTO")
    void saveDiagnosis_persistsAndReturnsDto() {
        when(diagnosisRepository.save(any(Diagnosis.class))).thenReturn(diagnosis);

        DiagnosisDto result = diagnosisService.saveDiagnosis(diagnosis);

        assertNotNull(result);
        assertEquals("J06.9", result.getCode());
        verify(diagnosisRepository).save(diagnosis);
    }
}
