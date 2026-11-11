package com.stractapi;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.CreateFieldRequest;
import com.stractapi.model.CreateStructRequest;
import com.stractapi.model.FieldType;
import com.stractapi.model.Job;
import com.stractapi.model.JobStatus;
import com.stractapi.model.Struct;
import io.github.cdimascio.dotenv.Dotenv;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Shared setup for the integration tests. Requires STRACT_API_KEY, read from
// .env in the repo root or the environment.
final class TestClient {

    static final Duration JOB_TIMEOUT = Duration.ofSeconds(120);
    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    static final Set<JobStatus> TERMINAL =
            Set.of(JobStatus.COMPLETE, JobStatus.ERROR, JobStatus.CANCELLED);

    private TestClient() {}

    static String apiKey() {
        // dotenv.get checks the process environment as well as .env.
        String apiKey = Dotenv.configure().ignoreIfMissing().load().get("STRACT_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "STRACT_API_KEY is not set. Add it to .env or your environment.");
        }
        return apiKey;
    }

    // Optional. Read only from .env.build, the same file Maven reads for the spec URL, so
    // generation and tests can't point at different servers. When set, the tests call this
    // URL, overriding the spec's `servers` entry (which always points at production).
    static String baseUrl() {
        String url = Dotenv.configure()
                .filename(".env.build")
                .ignoreIfMissing()
                .load()
                .entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)
                .stream()
                .filter(e -> "STRACT_API_BASE_URL".equals(e.getKey()))
                .map(e -> e.getValue())
                .findFirst()
                .orElse(null);
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.strip().replaceAll("/+$", "");
    }

    static ApiClient create() {
        ApiClient c = new ApiClient();
        String baseUrl = baseUrl();
        if (baseUrl != null) {
            c.setBasePath(baseUrl);
        }
        c.setBearerToken(apiKey());
        return c;
    }

    // Unique name so leftovers from a failed run are easy to spot and never collide.
    static String uniqueName(String prefix) {
        return "sdk-it-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // A struct with a single number field, "hamburgers". Callers archive it when done.
    static Struct createHamburgerStruct(ApiClient client) throws ApiException {
        Struct struct = new StructApi(client)
                .createStruct(new CreateStructRequest().name(uniqueName("struct")));
        new FieldApi(client).createFields(struct.getId(), List.of(new CreateFieldRequest()
                .name("hamburgers")
                .description("Number of hamburgers the customer ordered")
                .type(FieldType.NUMBER)));
        return struct;
    }

    // Archives every job on the struct (including those from sync runs), then the struct.
    static void archiveStructAndJobs(ApiClient client, UUID structId) throws ApiException {
        JobApi jobs = new JobApi(client);
        for (Job job : jobs.getStructJobs(structId)) {
            jobs.archiveJob(job.getId());
        }
        new StructApi(client).archiveStruct(structId);
    }

    // Polls until the job reaches a terminal status.
    static Job waitForJob(JobApi jobs, Job job) throws ApiException, InterruptedException {
        Instant deadline = Instant.now().plus(JOB_TIMEOUT);
        Job current = job;
        while (!Boolean.TRUE.equals(current.getIsComplete())
                && !TERMINAL.contains(current.getStatus())) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Job " + job.getId() + " did not finish in "
                        + JOB_TIMEOUT + "; last status=" + current.getStatus()
                        + ", notes=" + current.getNotes());
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
            current = jobs.getJobById(job.getId());
        }
        return current;
    }

    // Admin-only endpoints return 401 for non-admin keys; skip rather than fail.
    interface ApiCall<T> {
        T run() throws ApiException;
    }

    static <T> T skipIfUnauthorized(String reason, ApiCall<T> call) throws ApiException {
        try {
            return call.run();
        } catch (ApiException e) {
            assumeTrue(e.getCode() != 401, reason);
            throw e;
        }
    }

}
