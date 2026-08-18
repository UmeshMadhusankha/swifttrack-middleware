package com.swiftlogistics.sagaorchestrator.orchestration;

import com.swiftlogistics.sagaorchestrator.domain.SagaInstance;
import com.swiftlogistics.sagaorchestrator.domain.SagaState;
import com.swiftlogistics.sagaorchestrator.domain.SagaStep;
import com.swiftlogistics.sagaorchestrator.domain.SagaStepType;
import com.swiftlogistics.sagaorchestrator.repository.SagaInstanceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Watches for steps whose adapter never replied.
 *
 * Messaging gives us no failure notification when a service simply dies: the
 * command is delivered, nothing answers, and the saga waits forever. This
 * periodically looks for commands that have gone unanswered too long and tells
 * the orchestrator to treat them as failures, which starts compensation.
 */
@Component
public class SagaTimeoutMonitor {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutMonitor.class);

    private static final List<SagaState> UNFINISHED_STATES =
            List.of(SagaState.RUNNING, SagaState.COMPENSATING);

    private final SagaInstanceRepository sagaRepository;
    private final OrderSagaOrchestrator orchestrator;
    private final Duration stepTimeout;

    public SagaTimeoutMonitor(SagaInstanceRepository sagaRepository,
                              OrderSagaOrchestrator orchestrator,
                              @Value("${saga.step-timeout-seconds}") long stepTimeoutSeconds) {
        this.sagaRepository = sagaRepository;
        this.orchestrator = orchestrator;
        this.stepTimeout = Duration.ofSeconds(stepTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${saga.timeout-check-interval-ms}")
    public void failStepsThatNeverReplied() {
        for (TimedOutStep step : findTimedOutSteps()) {
            // The orchestrator reloads the saga and handles each timeout in its
            // own transaction, so one broken saga cannot block the others.
            orchestrator.handleStepTimeout(step.orderId(), step.stepType());
        }
    }

    /**
     * Collects timed-out steps as plain values before anything is changed.
     *
     * Reading and writing are kept apart on purpose: the orchestrator loads its
     * own copy of the saga, so we must not hand it entities from this scan.
     */
    private List<TimedOutStep> findTimedOutSteps() {
        Instant cutoff = Instant.now().minus(stepTimeout);
        List<TimedOutStep> timedOut = new ArrayList<>();

        for (SagaInstance saga : sagaRepository.findUnfinishedWithSteps(UNFINISHED_STATES)) {
            for (SagaStep step : saga.stepsAwaitingReplySince(cutoff)) {
                log.warn("Order {}: step {} has been waiting since {}, treating as failed",
                        saga.getOrderId(), step.getType(), step.getAwaitingReplySince());
                timedOut.add(new TimedOutStep(saga.getOrderId(), step.getType()));
            }
        }

        return timedOut;
    }

    /** A timed-out step, detached from Hibernate so it is safe to pass around. */
    private record TimedOutStep(Long orderId, SagaStepType stepType) {
    }
}
