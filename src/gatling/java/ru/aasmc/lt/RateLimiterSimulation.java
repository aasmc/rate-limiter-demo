package ru.aasmc.lt;


import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Тестируем, что за 10 минут, один пользователь может отправить не более
 * 300 GET-запросов (1 запрос в 2 секунды). Нагрузка неравномерная:
 * 1. Начинаем с 5 запросов в секунду в течение 3 минут3
 * 2. Увеличиваем нагрузку до 50 запросов в секунду в течение 3 минут3
 * 3. Поддерживаем нагрузку в 100 запросов в секунду в течение 4 минут
 *
 * Сценариев запуска нагрузки два - по одному на инстанс сервиса. Они выполняются параллельно.
 * Независимо от количества инстансов, общее количество успешных запросов пользователя на панели
 * мониторинга после отработки скрипта должно быть не более 300.
 */
public class RateLimiterSimulation extends Simulation {


    private static final HttpProtocolBuilder PROTOCOL_BUILDER = setupProtocolForSimulation("9091");
    private static final HttpProtocolBuilder PROTOCOL_BUILDER_2 = setupProtocolForSimulation("9095");
    private static final ScenarioBuilder CREATE_SCENARIO_BUILDER = createScenarioBuilder("Load Test Get Items 1");
    private static final ScenarioBuilder CREATE_SCENARIO_BUILDER_2 = createScenarioBuilder("Load Test Get Items 2");


    private static HttpProtocolBuilder setupProtocolForSimulation(String port) {
        return http.baseUrl("http://localhost:" + port)
                .maxConnectionsPerHost(1)
                .userAgentHeader("Gatling/Load Test");
    }


    private static ScenarioBuilder createScenarioBuilder(String name) {
        return CoreDsl.scenario(name)
                .exec(http("get-item-response").get("/items/Alex")
                        .check(status().in(200, 429)));
    }

    public RateLimiterSimulation() {
        setUp(CREATE_SCENARIO_BUILDER
                        .injectOpen(
                                constantUsersPerSec(5).during(Duration.ofMinutes(3)),
                                rampUsersPerSec(5).to(50).during(Duration.ofMinutes(3)),
                                constantUsersPerSec(100).during(Duration.ofMinutes(4))
                        ).protocols(PROTOCOL_BUILDER),
                CREATE_SCENARIO_BUILDER_2
                        .injectOpen(
                                constantUsersPerSec(5).during(Duration.ofMinutes(3)),
                                rampUsersPerSec(5).to(50).during(Duration.ofMinutes(3)),
                                constantUsersPerSec(100).during(Duration.ofMinutes(4))
                        ).protocols(PROTOCOL_BUILDER_2)
        );
    }

}
