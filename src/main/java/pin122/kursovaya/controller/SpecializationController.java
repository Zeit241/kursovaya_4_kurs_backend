package pin122.kursovaya.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.dto.SpecializationDto;
import pin122.kursovaya.model.Specialization;
import pin122.kursovaya.service.SpecializationService;

import java.util.List;

@RestController
@RequestMapping("/api/specializations")
public class SpecializationController {

    private final SpecializationService specializationService;

    public SpecializationController(SpecializationService specializationService) {
        this.specializationService = specializationService;
    }

    @GetMapping
    public ResponseEntity<List<SpecializationDto>> getAllSpecializations() {
        return ResponseEntity.ok(specializationService.getAllSpecializations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpecializationDto> getSpecializationById(@PathVariable Long id) {
        return specializationService.getSpecializationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<SpecializationDto> getSpecializationByCode(@PathVariable String code) {
        return specializationService.getSpecializationByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SpecializationDto> createSpecialization(@Valid @RequestBody Specialization specialization) {
        return ResponseEntity.ok(specializationService.saveSpecialization(specialization));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SpecializationDto> updateSpecialization(
            @PathVariable Long id,
            @Valid @RequestBody Specialization specialization) {
        if (!specializationService.getSpecializationById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        specialization.setId(id);
        return ResponseEntity.ok(specializationService.saveSpecialization(specialization));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpecialization(@PathVariable Long id) {
        specializationService.deleteSpecialization(id);
        return ResponseEntity.noContent().build();
    }
}

