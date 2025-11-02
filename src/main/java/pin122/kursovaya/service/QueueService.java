package pin122.kursovaya.service;

import org.springframework.stereotype.Service;
import pin122.kursovaya.model.QueueEntry;
import pin122.kursovaya.repository.QueueEntryRepository;

import java.util.List;
import java.util.Optional;

@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;

    public QueueService(QueueEntryRepository queueEntryRepository) {
        this.queueEntryRepository = queueEntryRepository;
    }

    public List<QueueEntry> getQueueByDoctor(Long doctorId) {
        return queueEntryRepository.findByDoctorIdOrderByPositionAsc(doctorId);
    }

    public Optional<QueueEntry> getQueueEntryById(Long id) {
        return queueEntryRepository.findById(id);
    }

    public QueueEntry saveQueueEntry(QueueEntry entry) {
        return queueEntryRepository.save(entry);
    }

    public void deleteQueueEntry(Long id) {
        queueEntryRepository.deleteById(id);
    }
}