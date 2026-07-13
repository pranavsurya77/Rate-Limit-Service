package com.example.rate_limit;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RateLimitSimulation extends Simulation {

        // HTTP protocol configuration
        HttpProtocolBuilder httpProtocol = http
                        .baseUrl("http://localhost:8080")
                        .acceptHeader("application/json")
                        .contentTypeHeader("application/json");

        // Scenario 1: Single-client high-throughput (tests token bucket drain + refill)
        ScenarioBuilder singleClientScenario = scenario("Single Client Burst")
                        .exec(http("Check Rate Limit - Single Client")
                                        .post("/api/v1/rate-limit/check")
                                        .body(StringBody(
                                                        "{\"clientKey\":\"payment-api\",\"identifier\":\"user_fixed\"}"))
                                        .check(status().in(200, 429)));

        // Scenario 2: Multi-client distributed load (simulates real-world traffic)
        ScenarioBuilder multiClientScenario = scenario("Multi Client Distributed Load")
                        .exec(session -> {
                                int randomNum = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 201);
                                return session.set("randomId", "user_" + randomNum);
                        })
                        .exec(http("Check Rate Limit - Multi Client")
                                        .post("/api/v1/rate-limit/check")
                                        .body(StringBody(
                                                        "{\"clientKey\":\"payment-api\",\"identifier\":\"#{randomId}\"}"))
                                        .check(status().in(200, 429)));

        // Scenario 3: Rapid-fire burst (spike test)
        ScenarioBuilder spikeScenario = scenario("Spike Burst")
                        .exec(session -> {
                                int randomNum = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 51);
                                return session.set("spikeId", "spike_user_" + randomNum);
                        })
                        .exec(http("Check Rate Limit - Spike")
                                        .post("/api/v1/rate-limit/check")
                                        .body(StringBody(
                                                        "{\"clientKey\":\"payment-api\",\"identifier\":\"#{spikeId}\"}"))
                                        .check(status().in(200, 429)));

        public RateLimitSimulation() {
                this.setUp(
                                // Phase 1: Single client burst — 200 RPS sustained for 15s
                                singleClientScenario.injectOpen(
                                                rampUsersPerSec(1).to(200).during(5),
                                                constantUsersPerSec(200).during(15)),
                                // Phase 2: Multi client distributed — ramp to 500 RPS over 10s, sustain 20s
                                multiClientScenario.injectOpen(
                                                nothingFor(5), // stagger start
                                                rampUsersPerSec(10).to(500).during(10),
                                                constantUsersPerSec(500).during(20)),
                                // Phase 3: Spike burst — sudden 1000 RPS for 5s
                                spikeScenario.injectOpen(
                                                nothingFor(25), // starts after other phases are warm
                                                constantUsersPerSec(1000).during(5)))
                                .protocols(httpProtocol);
        }

        @Override
        public void before() {
                System.out.println("============================================================");
                System.out.println("  GATLING LOAD TEST - Rate Limit Service (Token Bucket)");
                System.out.println("============================================================");
                System.out.println("Registering test client 'payment-api' in Redis...");
                try {
                        HttpClient client = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create("http://localhost:8080/api/v1/admin/client/register"))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(
                                                        "{\"clientKey\":\"payment-api\",\"maxTokens\":100,\"refillRate\":10,\"algorithm\":\"TOKEN_BUCKET\"}"))
                                        .build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        System.out.println("Client registration status: " + response.statusCode());
                        System.out.println("Client registration body: " + response.body());
                } catch (Exception e) {
                        System.err.println("Failed to register client (may already exist): " + e.getMessage());
                }
                System.out.println("============================================================");
                System.out.println("Starting load test phases:");
                System.out.println("  Phase 1: Single client burst    → 200 RPS for 15s");
                System.out.println("  Phase 2: Multi client (200 IDs) → 500 RPS for 20s");
                System.out.println("  Phase 3: Spike burst (50 IDs)   → 1000 RPS for 5s");
                System.out.println("============================================================");
        }

        @Override
        public void after() {
                System.out.println("============================================================");
                System.out.println("  GATLING LOAD TEST COMPLETE");
                System.out.println("  Check target/gatling/ for detailed HTML reports");
                System.out.println("============================================================");
        }
}
