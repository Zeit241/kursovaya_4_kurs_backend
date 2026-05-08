package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pin122.kursovaya.dto.AiCatalogResponseDto;
import pin122.kursovaya.service.AiCatalogService;

/**
 * REST-контроллер справочников для клиентского приложения.
 * <p>
 * Базовый путь: {@code /api/catalog}. Предоставляет компактные данные для ИИ-помощника.
 *
 * @see AiCatalogService
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final AiCatalogService aiCatalogService;

    /**
     * Создаёт контроллер каталога.
     *
     * @param aiCatalogService сервис построения справочника для ИИ
     */
    public CatalogController(AiCatalogService aiCatalogService) {
        this.aiCatalogService = aiCatalogService;
    }

    /**
     * Возвращает компактные списки врачей и услуг для ИИ-помощника (идентификатор, имя, специализация / идентификатор, название).
     *
     * @return HTTP 200 и тело {@link AiCatalogResponseDto}
     * @apiNote Вызывается методом {@code GET /api/catalog/ai-reference}; авторизация по общим правилам API.
     */
    @GetMapping("/ai-reference")
    public ResponseEntity<AiCatalogResponseDto> getAiReferenceCatalog() {
        return ResponseEntity.ok(aiCatalogService.buildAiReferenceCatalog());
    }
}
