package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.client.ApiResponse;
import com.stractapi.model.Catalog;
import com.stractapi.model.CreateCatalogRequest;
import com.stractapi.model.CreateJobRequest;
import com.stractapi.model.Document;
import com.stractapi.model.Job;
import com.stractapi.model.JobOutputRecord;
import com.stractapi.model.Struct;
import com.stractapi.model.SyncTextJobRequest;
import com.stractapi.model.UploadTextDocumentRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// These tests spend a small amount of credits. Every job is archived afterwards
// (the struct's jobs are swept in @AfterAll, which also covers the sync runs).
class JobIT {

    private static ApiClient client;
    private static Struct struct;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() throws ApiException {
        client = TestClient.create();
        struct = TestClient.createHamburgerStruct(client);
    }

    @AfterAll
    static void tearDown() throws ApiException {
        TestClient.archiveStructAndJobs(client, struct.getId());
    }

    @Test
    void syncTextJob() throws ApiException {
        ApiResponse<List<JobOutputRecord>> res = new JobApi(client)
                .uploadTextDocumentAndRunJobWithHttpInfo(struct.getId(), new SyncTextJobRequest()
                        .name("order-1042")
                        .text("Two hamburgers and a large fries")
                        .additionalInstructions("")
                        .outputType(SyncTextJobRequest.OutputTypeEnum.JSON));

        assertTrue(res.getStatusCode() == 200 || res.getStatusCode() == 202);
        // Both statuses carry the job ID header.
        assertTrue(res.getHeaders().containsKey("stract-job-id"));
        assumeFalse(res.getStatusCode() == 202,
                "Job took longer than the sync wait time; returned 202");

        assertEquals(1, res.getData().size());
        JobOutputRecord record = res.getData().get(0);
        assertEquals("order-1042", record.getDocumentName());
        assertTrue(record.getAdditionalProperties().containsKey("hamburgers"));
    }

    @Test
    void syncFileJob() throws ApiException, IOException {
        File file = Files.writeString(tempDir.resolve("order-1043.txt"),
                "Three hamburgers and a soda").toFile();
        ApiResponse<List<JobOutputRecord>> res = new JobApi(client)
                .uploadDocumentProcessAndRunJobWithHttpInfo(
                        struct.getId(), file, "", "json", null);

        assertTrue(res.getStatusCode() == 200 || res.getStatusCode() == 202);
        assertTrue(res.getHeaders().containsKey("stract-job-id"));
        assumeFalse(res.getStatusCode() == 202,
                "Job took longer than the sync wait time; returned 202");

        assertEquals(1, res.getData().size());
        assertTrue(res.getData().get(0).getAdditionalProperties().containsKey("hamburgers"));
    }

    @Test
    void createCancelAndArchiveAJob() throws ApiException {
        DocumentApi documents = new DocumentApi(client);
        JobApi jobs = new JobApi(client);
        Document doc = documents.uploadTextDocumentNoCatalog(new UploadTextDocumentRequest()
                .name(TestClient.uniqueName("cancel-job-doc"))
                .text("Six hamburgers and nothing else"));
        Job job = null;
        try {
            job = jobs.createJob(struct.getId(), new CreateJobRequest()
                    .additionalInstructions("")
                    .documentIds(List.of(doc.getId())));
            Job cancelled = jobs.cancelJob(job.getId());
            assertEquals(job.getId(), cancelled.getId());
            // If the job was fast enough to finish before the cancel landed, that's fine too.
            assertTrue(TestClient.TERMINAL.contains(cancelled.getStatus()));
        } finally {
            try {
                if (job != null) {
                    assertEquals(job.getId(), jobs.archiveJob(job.getId()));
                }
            } finally {
                documents.deleteDocument(doc.getId());
            }
        }
    }

    // createJob can target a whole catalog instead of individual documents.
    @Test
    void createsAJobOverACatalog() throws ApiException {
        CatalogApi catalogs = new CatalogApi(client);
        JobApi jobs = new JobApi(client);
        Catalog catalog = catalogs.createCatalog(
                new CreateCatalogRequest().name(TestClient.uniqueName("job-catalog")));
        Job job = null;
        try {
            new DocumentApi(client).uploadTextDocument(catalog.getId(),
                    new UploadTextDocumentRequest().name("catalog-order").text("Seven hamburgers"));
            job = jobs.createJob(struct.getId(), new CreateJobRequest()
                    .additionalInstructions("")
                    .catalogId(catalog.getId()));
            assertEquals(catalog.getId(), job.getCatalogId());
            // Don't pay for the analysis; the point is that the catalog was accepted.
            jobs.cancelJob(job.getId());
        } finally {
            try {
                if (job != null) {
                    jobs.archiveJob(job.getId());
                }
            } finally {
                catalogs.deleteCatalog(catalog.getId());
            }
        }
        assertFalse(catalogs.getCatalogs().stream()
                .anyMatch(c -> catalog.getId().equals(c.getId())));
    }

}
