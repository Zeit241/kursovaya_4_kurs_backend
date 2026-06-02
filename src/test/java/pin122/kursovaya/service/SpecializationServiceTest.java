package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.repository.SpecializationRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для SpecializationService - сервис справочника специализаций
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpecializationService - тесты сервиса специализаций")
class SpecializationServiceTest {

    @Mock
    private SpecializationRepository specializationRepository;

    @InjectMocks
    private SpecializationService specializationService;

    private Specialization testSpecialization;

    @BeforeEach
    void setUp() {
        testSpecialization = new Specialization();
        testSpecialization.setId(1L);
        testSpecialization.setCode("cardiology");
        testSpecialization.setName("Кардиология");
        testSpecialization.setDescription("Лечение сердечно-сосудистых заболеваний");
    }

    @Test
    @DisplayName("Получение всех специализаций")
    void getAllSpecializations_returnsList() {
        Specialization spec2 = new Specialization();
        spec2.setId(2L);
        spec2.setCode("neurology");
        spec2.setName("Неврология");

        when(specializationRepository.findAll()).thenReturn(Arrays.asList(testSpecialization, spec2));

        List<SpecializationDto> result = specializationService.getAllSpecializations();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("cardiology", result.get(0).getCode());
        assertEquals("neurology", result.get(1).getCode());
        verify(specializationRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение специализации по ID - найдена")
    void getSpecializationById_existing_returnsDto() {
        when(specializationRepository.findById(1L)).thenReturn(Optional.of(testSpecialization));

        Optional<SpecializationDto> result = specializationService.getSpecializationById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("Кардиология", result.get().getName());
    }

    @Test
    @DisplayName("Получение специализации по ID - не найдена")
    void getSpecializationById_notExisting_returnsEmpty() {
        when(specializationRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<SpecializationDto> result = specializationService.getSpecializationById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Получение специализации по коду - найдена")
    void getSpecializationByCode_existing_returnsDto() {
        when(specializationRepository.findByCode("cardiology")).thenReturn(Optional.of(testSpecialization));

        Optional<SpecializationDto> result = specializationService.getSpecializationByCode("cardiology");

        assertTrue(result.isPresent());
        assertEquals("cardiology", result.get().getCode());
        assertEquals("Кардиология", result.get().getName());
    }

    @Test
    @DisplayName("Сохранение специализации")
    void saveSpecialization_savesAndReturnsDto() {
        when(specializationRepository.save(testSpecialization)).thenReturn(testSpecialization);

        SpecializationDto result = specializationService.saveSpecialization(testSpecialization);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("cardiology", result.getCode());
        verify(specializationRepository, times(1)).save(testSpecialization);
    }

    @Test
    @DisplayName("Удаление специализации")
    void deleteSpecialization_callsRepository() {
        doNothing().when(specializationRepository).deleteById(1L);

        specializationService.deleteSpecialization(1L);

        verify(specializationRepository, times(1)).deleteById(1L);
    }
}
