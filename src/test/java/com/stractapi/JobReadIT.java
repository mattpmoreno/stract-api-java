package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.CreateJobRequest;
import com.stractapi.model.Document;
import com.stractapi.model.Job;
import com.stractapi.model.JobDocument;
import com.stractapi.model.JobOutputRecord;
import com.stractapi.model.JobStatus;
import com.stractapi.model.Struct;
import com.stractapi.model.UploadTextDocumentRequest;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Read endpoints over one finished job, so only one job is paid for.
// Cleanup archives the job and struct and deletes the document.
class JobReadIT {

    private static ApiClient client;
    private static Struct struct;
    private static Document doc;
    private static Job job;

    @BeforeAll
    static void setUp() throws ApiException, InterruptedException {
        client = TestClient.create();
        struct = TestClient.createHamburgerStruct(client);
        doc = new DocumentApi(client).uploadTextDocumentNoCatalog(new UploadTextDocumentRequest()
                .name(TestClient.uniqueName("finished-job-doc"))
                .text("Four hamburgers and a chocolate shake"));
        JobApi jobs = new JobApi(client);
        Job created = jobs.createJob(struct.getId(), new CreateJobRequest()
                .additionalInstructions("")
                .documentIds(List.of(doc.getId())));
        job = TestClient.waitForJob(jobs, created);
        assertEquals(JobStatus.COMPLETE, job.getStatus(),
                "Job ended as " + job.getStatus() + ": " + job.getNotes());
    }

    @AfterAll
    static void tearDown() throws ApiException {
        try {
            new DocumentApi(client).deleteDocument(doc.getId());
        } finally {
            TestClient.archiveStructAndJobs(client, struct.getId());
        }
    }

    @Test
    void jobIsListed() throws ApiException {
        JobApi jobs = new JobApi(client);
        assertTrue(jobs.getStructJobs(struct.getId()).stream()
                .anyMatch(j -> job.getId().equals(j.getId())));
        assertTrue(jobs.getUserJobs().stream().anyMatch(j -> job.getId().equals(j.getId())));
        assertEquals(struct.getId(), jobs.getJobById(job.getId()).getStructId());
    }

    @Test
    void jobDocuments() throws ApiException {
        List<Document> documents = new DocumentApi(client).getDocumentsForJob(job.getId());
        assertEquals(List.of(doc.getId()), documents.stream().map(Document::getId).toList());

        List<JobDocument> jobDocs = new JobDocumentApi(client).getJobDocuments(job.getId());
        assertEquals(1, jobDocs.size());
        assertEquals(job.getId(), jobDocs.get(0).getJobId());
        assertEquals(doc.getId(), jobDocs.get(0).getDocumentId());
        assertEquals(JobStatus.COMPLETE, jobDocs.get(0).getStatus());
    }

    @Test
    void jobDocumentOutput() throws ApiException {
        JobDocumentApi api = new JobDocumentApi(client);
        JobDocument jobDoc = api.getJobDocuments(job.getId()).get(0);
        var output = api.getJobDocumentOutput(jobDoc.getId());
        assertNotNull(output);
        assertEquals(jobDoc.getId(), output.getJobDocumentId());
        assertTrue(output.getData().containsKey("hamburgers"));
    }

    @Test
    void jobOutputs() throws ApiException {
        JobApi jobs = new JobApi(client);

        List<JobOutputRecord> records = jobs.getJobOutputJson(job.getId());
        assertEquals(1, records.size());
        assertTrue(records.get(0).getAdditionalProperties().containsKey("hamburgers"));

        String[] lines = jobs.getJobOutputCsv(job.getId()).strip().split("\\R");
        assertTrue(lines[0].contains("document_name"));
        assertTrue(lines[0].contains("hamburgers"));
        assertEquals(2, lines.length, "header plus one row");
    }

}
