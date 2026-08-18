package com.swiftlogistics.sagaorchestrator.repository;

import com.swiftlogistics.sagaorchestrator.domain.SagaInstance;
import com.swiftlogistics.sagaorchestrator.domain.SagaState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {

    Optional<SagaInstance> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    /**
     * Sagas that have not reached a final state, with their steps already
     * loaded.
     *
     * "join fetch" pulls the steps in the same query so the timeout monitor can
     * read them after the query's transaction has closed.
     */
    @Query("select distinct saga from SagaInstance saga join fetch saga.steps where saga.state in :states")
    List<SagaInstance> findUnfinishedWithSteps(@Param("states") List<SagaState> states);
}
