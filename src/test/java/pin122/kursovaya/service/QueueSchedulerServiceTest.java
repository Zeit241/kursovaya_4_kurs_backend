package pin122.kursovaya.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pin122.kursovaya.dto.QueueEntryDto;
import pin122.kursovaya.dto.WebSocketSessionData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueSchedulerService - тесты планировщика очереди")
class QueueSchedulerServiceTest {

    @Mock
    private RedisQueueService redisQueueService;

    @InjectMocks
    private QueueSchedulerService queueSchedulerService;

    @Test
    @DisplayName("checkExpiredAppointments пропускает обработку без активных сессий")
    void checkExpiredAppointments_noActiveSessions_skipsProcessing() {
        when(redisQueueService.getAllActiveSessions()).thenReturn(Collections.emptyList());

        queueSchedulerService.checkExpiredAppointments();

        verify(redisQueueService).getAllActiveSessions();
        verify(redisQueueService, never()).removeExpiredAppointments(anyString(), any());
        verify(redisQueueService, never()).notifyUserQueueUpdate(anyString(), any());
    }

    @Test
    @DisplayName("checkExpiredAppointments удаляет просроченные записи и уведомляет пользователя")
    void checkExpiredAppointments_removesExpiredAndNotifiesUser() {
        WebSocketSessionData session = new WebSocketSessionData(
                "sess-1", 1L, 10L, "patient@test.com", List.of(100L), LocalDateTime.now());
        List<QueueEntryDto> updatedQueues = List.of(
                new QueueEntryDto(null, 2L, 100L, 10L, 0, OffsetDateTime.now()));

        when(redisQueueService.getAllActiveSessions()).thenReturn(List.of(session));
        when(redisQueueService.removeExpiredAppointments(eq("sess-1"), any())).thenReturn(1);
        when(redisQueueService.getQueuesByPatient(10L)).thenReturn(updatedQueues);

        queueSchedulerService.checkExpiredAppointments();

        verify(redisQueueService).removeExpiredAppointments(eq("sess-1"), any());
        verify(redisQueueService).getQueuesByPatient(10L);
        verify(redisQueueService).notifyUserQueueUpdate("patient@test.com", updatedQueues);
    }

    @Test
    @DisplayName("checkExpiredAppointments уведомляет врачей о затронутых очередях")
    void checkExpiredAppointments_notifiesAffectedDoctors() {
        WebSocketSessionData session = new WebSocketSessionData(
                "sess-2", 2L, 20L, "user@test.com", List.of(200L), LocalDateTime.now());
        LocalDate today = LocalDate.now();

        when(redisQueueService.getAllActiveSessions()).thenReturn(List.of(session));
        when(redisQueueService.removeExpiredAppointments(eq("sess-2"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.Collection<String> affected = invocation.getArgument(1);
            affected.add("5|" + today);
            return 1;
        });

        queueSchedulerService.checkExpiredAppointments();

        verify(redisQueueService).notifyQueueUpdated(5L, today);
    }

    @Test
    @DisplayName("recalculateAllQueues делегирует пересчёт RedisQueueService")
    void recalculateAllQueues_delegatesToRedisQueueService() {
        queueSchedulerService.recalculateAllQueues();

        verify(redisQueueService).recalculateQueuesForAllActiveSessionPatients();
    }
}
