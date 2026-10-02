package pe.edu.nova.java.starters.observability.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import pe.edu.nova.java.starters.observability.StubCollector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** El indicador verifica el endpoint que recibe, contra un Collector de mentira. */
class CollectorHealthIndicatorTest {

    @Test
    void aCollectorThatAnswersIsUpAndTheDetailNamesTheEndpoint() {
        try (StubCollector collector = new StubCollector()) {
            Health health = new CollectorHealthIndicator(collector.endpoint()).health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails())
                    .containsEntry("endpoint", collector.endpoint())
                    .containsEntry("statusCode", 404);
            assertThat(collector.requests()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("GET");
                assertThat(request.path()).isEqualTo("/");
            });
        }
    }

    @Test
    void aCollectorThatFailsWithAServerErrorIsDown() {
        try (StubCollector collector = new StubCollector()) {
            collector.answerGetWith(503);

            Health health = new CollectorHealthIndicator(collector.endpoint()).health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails())
                    .containsEntry("endpoint", collector.endpoint())
                    .containsEntry("statusCode", 503);
        }
    }

    @Test
    void aCollectorThatDoesNotAnswerIsDown() {
        String endpoint;
        try (StubCollector collector = new StubCollector()) {
            endpoint = collector.endpoint();
        }

        Health health = new CollectorHealthIndicator(endpoint).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("endpoint", endpoint).containsKey("error");
    }

    @Test
    void anIndicatorWithoutAnEndpointCannotExist() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CollectorHealthIndicator(null))
                .withMessage("El endpoint del OpenTelemetry Collector no puede estar vacío");
        assertThatIllegalArgumentException().isThrownBy(() -> new CollectorHealthIndicator("  "));
    }
}
