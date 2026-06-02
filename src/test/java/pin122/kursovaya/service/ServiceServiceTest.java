package pin122.kursovaya.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pin122.kursovaya.dto.ServiceDto;
import pin122.kursovaya.model.Service;
import pin122.kursovaya.repository.BookingCatalogRepository;
import pin122.kursovaya.repository.ServiceRepository;
import pin122.kursovaya.repository.SpecializationRepository;
import pin122.kursovaya.repository.SpecializationServiceLinkRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для ServiceService - сервис медицинских услуг
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceService - тесты сервиса медицинских услуг")
class ServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private BookingCatalogRepository bookingCatalogRepository;

    @Mock
    private SpecializationServiceLinkRepository specializationServiceLinkRepository;

    @Mock
    private SpecializationRepository specializationRepository;

    @InjectMocks
    private ServiceService serviceService;

    private Service testService;

    @BeforeEach
    void setUp() {
        testService = new Service();
        testService.setId(1L);
        testService.setName("Консультация терапевта");
        testService.setCode("consult-therapist");
        testService.setPrice(new BigDecimal("1500.00"));
        testService.setDurationMinutes(30);
        testService.setDescription("Первичный осмотр");
    }

    @Test
    @DisplayName("Получение всех услуг")
    void getAllServices_returnsList() {
        when(serviceRepository.findAll()).thenReturn(List.of(testService));
        when(specializationServiceLinkRepository.findActiveByServiceIds(anyList()))
                .thenReturn(Collections.emptyList());
        when(bookingCatalogRepository.findSpecializationNamesByServiceIds(anyList()))
                .thenReturn(Collections.emptyList());

        List<ServiceDto> result = serviceService.getAllServices();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Консультация терапевта", result.get(0).getName());
        verify(serviceRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Получение услуги по ID - найдена")
    void getServiceById_existing_returnsDto() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
        when(specializationServiceLinkRepository.findActiveByServiceIds(anyList()))
                .thenReturn(Collections.emptyList());
        when(bookingCatalogRepository.findSpecializationNamesByServiceIds(anyList()))
                .thenReturn(Collections.emptyList());

        Optional<ServiceDto> result = serviceService.getServiceById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("consult-therapist", result.get().getCode());
    }

    @Test
    @DisplayName("Получение услуги по ID - не найдена")
    void getServiceById_notExisting_returnsEmpty() {
        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<ServiceDto> result = serviceService.getServiceById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Получение услуг для врача - пустой список")
    void getServicesForDoctor_noServices_returnsEmptyList() {
        when(bookingCatalogRepository.findServiceIdsByDoctorId(1L)).thenReturn(Collections.emptyList());

        List<ServiceDto> result = serviceService.getServicesForDoctor(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(serviceRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Удаление услуги - удаляет связи и саму услугу")
    void deleteService_deletesLinksAndService() {
        doNothing().when(specializationServiceLinkRepository).deleteByServiceId(1L);
        doNothing().when(serviceRepository).deleteById(1L);

        serviceService.deleteService(1L);

        verify(specializationServiceLinkRepository, times(1)).deleteByServiceId(1L);
        verify(serviceRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Установка специализаций - несуществующая специализация, ошибка 400")
    void setServiceSpecializations_invalidSpecialization_throwsBadRequest() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        when(specializationRepository.countByIdIn(List.of(99L))).thenReturn(0L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> serviceService.setServiceSpecializations(1L, List.of(99L)));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("несуществующая специализация"));
    }
}
