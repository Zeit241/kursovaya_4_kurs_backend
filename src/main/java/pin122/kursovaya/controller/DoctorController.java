package pin122.kursovaya.controller;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.model.Doctor;
import pin122.kursovaya.service.DoctorService;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping({"/", ""})
    public ResponseEntity<List<DoctorDto>> getAllDoctors(@RequestParam(name = "q", required = false) String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(doctorService.getAllDoctors());
        }
        return ResponseEntity.ok(doctorService.searchDoctors(q.trim()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorDto> getDoctorById(@PathVariable Long id) {
        return doctorService.getDoctorById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/create")
    public ResponseEntity<DoctorDto> createDoctor(@Valid @RequestBody Doctor doctor) {
        return ResponseEntity.ok(doctorService.saveDoctor(doctor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DoctorDto> updateDoctor(@PathVariable Long id, @Valid @RequestBody Doctor doctorDetails) {
        if (!doctorService.getDoctorById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        doctorDetails.setId(id);
        return ResponseEntity.ok(doctorService.saveDoctor(doctorDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return ResponseEntity.noContent().build();
    }
}