package com.swiftlogistics.sagaorchestrator.api;

import com.swiftlogistics.sagaorchestrator.api.dto.SagaInstanceResponse;
import com.swiftlogistics.sagaorchestrator.repository.SagaInstanceRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view into the saga state — for debugging and demo purposes.
 *
 * This endpoint is on the internal network only (port 8082 is not published
 * to the host in docker-compose.yml). It is safe for a prototype. In
 * production, restrict it to an internal admin role.
 */
@RestController
@RequestMapping("/api/saga")
public class SagaController {

    private final SagaInstanceRepository sagaRepository;

    public SagaController(SagaInstanceRepository sagaRepository) {
        this.sagaRepository = sagaRepository;
    }

    /** Full state of one saga, including every step. */
    @GetMapping("/{orderId}")
    public ResponseEntity<SagaInstanceResponse> getSaga(@PathVariable Long orderId) {
        return sagaRepository.findByOrderId(orderId)
                .map(saga -> ResponseEntity.ok(SagaInstanceResponse.from(saga)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** All sagas — useful during demo to show the full history. */
    @GetMapping
    public List<SagaInstanceResponse> getAllSagas() {
        return sagaRepository.findAll().stream()
                .map(SagaInstanceResponse::from)
                .toList();
    }
}
