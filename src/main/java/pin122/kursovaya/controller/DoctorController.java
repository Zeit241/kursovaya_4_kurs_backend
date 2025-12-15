package pin122.kursovaya.controller;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.CreateDoctorRequest;
import pin122.kursovaya.dto.DoctorDto;
import pin122.kursovaya.dto.UpdateDoctorRequest;
import pin122.kursovaya.service.DoctorService;
import pin122.kursovaya.utils.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping({"/", ""})
    public ResponseEntity<ApiResponse<List<DoctorDto>>> getAllDoctors(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortOrder", required = false, defaultValue = "asc") String sortOrder) {
        List<DoctorDto> doctors = (q == null || q.trim().isEmpty())
                ? doctorService.getAllDoctors(limit, offset, sortBy, sortOrder)
                : doctorService.searchDoctors(q.trim(), limit, offset, sortBy, sortOrder);

        return ResponseEntity.ok(new ApiResponse<>(true, "Список врачей успешно получен", doctors));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> getDoctorById(@PathVariable Long id) {
        return doctorService.getDoctorById(id)
                .map(doctor -> ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно получен", doctor)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Врач не найден", null)));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<DoctorDto>> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        DoctorDto created = doctorService.createDoctor(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно создан", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> updateDoctor(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request) {
        return doctorService.updateDoctor(id, request)
                .map(updated -> ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно обновлён", updated)))
                .orElse(ResponseEntity.status(404)
                        .body(new ApiResponse<>(false, "Врач не найден", null)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Врач успешно удалён", null));
    }
}