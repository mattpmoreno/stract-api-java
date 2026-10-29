# Stract API Java SDK

Java client for [Stract API](https://stract-api.com).

Define a **struct**, give it **fields**, and run a **job** against text or documents to get back one record per document.

- API reference: [docs.stract-api.com](https://docs.stract-api.com)
- OpenAPI spec: [api.stract-api.com/spec/openapi.yaml](https://api.stract-api.com/spec/openapi.yaml)
- API quick start: [stract-api.com/guide/quick-start-api](https://stract-api.com/guide/quick-start-api/)

## Installation

Maven:

```xml
<dependency>
  <groupId>com.stractapi</groupId>
  <artifactId>stract-api</artifactId>
  <version>0.9.2</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.stractapi:stract-api:0.9.2")
```

Requires Java 17 or later.

## Authentication

Requests are authenticated with an API key, sent as a bearer token. To create one, sign in to the Stract app and go to **Account → API Keys**. Additional keys require a Team or Enterprise subscription.

The generated client does not add the key for you. Create one `ApiClient` and attach the `Authorization` header with a request interceptor:

```java
import com.stractapi.client.ApiClient;

String apiKey = System.getenv("STRACT_API_KEY");

ApiClient client = new ApiClient()
    .setRequestInterceptor(b -> b.header("Authorization", "Bearer " + apiKey));
```

Keep your API key out of source control. Load it from an environment variable or a secrets manager.

## API groups

Endpoints are grouped by area, and each group is its own class. Create an instance for each group you use, passing the same `client` to each:

```java
import com.stractapi.*;

StructApi structs = new StructApi(client);
FieldApi fields = new FieldApi(client);
JobApi jobs = new JobApi(client);
DocumentApi documents = new DocumentApi(client);
```

| Class | Methods |
|---|---|
| `StructApi` | `createStruct`, `getStructs`, `archiveStruct` |
| `FieldApi` | `createFields`, `getFields`, `editField`, `deleteField` |
| `JobApi` | `uploadTextDocumentAndRunJob`, `uploadDocumentProcessAndRunJob`, `createJob`, `getJobById`, `getJobOutputJson`, `getJobOutputCsv`, `getStructJobs`, `getUserJobs`, `cancelJob`, `archiveJob` |
| `JobDocumentApi` | `getJobDocuments`, `getJobDocumentOutput` |
| `DocumentApi` | `uploadDocument`, `uploadDocumentNoCatalog`, `uploadTextDocument`, `uploadTextDocumentNoCatalog`, `getUserDocuments`, `getCatalogDocuments`, `getDocumentsForJob`, `getDocumentText`, `getDocumentLink`, `deleteDocument` |
| `CatalogApi` | `createCatalog`, `getCatalogs`, `deleteCatalog` |
| `LlmModelApi` | `getLlmModels` |
| `CreditsApi` | `getCurrentCredits`, `getCurrentOrgCredits` |
| `AccountCreditLedgerApi` | `getAccountCreditLedgerForSpendingUser` |
| `UserApi` | `getMyUser`, `editMyUser` |
| `AccountApi` | `getAccount`, `editAccount`, `getAccountUsers`, `createAccountUser` |
| `ApiKeyApi` | `getApiKeys` |

Request and response types live in `com.stractapi.model`. The examples below use the instances created above.

## Quick start

### 1. Create a struct

A struct describes the data you want extracted.

```java
import com.stractapi.model.*;

Struct struct = structs.createStruct(new CreateStructRequest().name("Restaurant orders"));
```

### 2. Add fields

Each field has a name, a description that guides the extraction, and a type.

```java
import java.util.List;

fields.createFields(struct.getId(), List.of(
    new CreateFieldRequest()
        .name("hamburgers")
        .description("Number of hamburgers the customer ordered")
        .type(FieldType.NUMBER)));
```

Field types: `STRING`, `BOOLEAN`, `NUMBER`, `DATE`, `DATETIME`, `ENUM`.

### 3. Extract data from text

For synchronous jobs, capture the job ID so you can recover if the job doesn't finish in time. See [Handling `202` on synchronous jobs](#handling-202-on-synchronous-jobs) for the client setup this example assumes.

```java
try {
    List<JobOutputRecord> records = jobs.uploadTextDocumentAndRunJob(
        struct.getId(),
        new SyncTextJobRequest()
            .name("order-1042")
            .text("Two hamburgers and a large fries")
            .additionalInstructions("")
            .outputType(SyncTextJobRequest.OutputTypeEnum.JSON));

    for (JobOutputRecord record : records) {
        System.out.println(record.getDocumentName());                          // order-1042
        System.out.println(record.getAdditionalProperties().get("hamburgers")); // 2
    }
} catch (ApiException e) {
    String jobId = lastJobId.get(); // set when the response was a 202
    // ...
}
```

Each record has the document name plus one entry per field, available through `getAdditionalProperties()`.

### 4. Extract data from a file

```java
import java.io.File;

List<JobOutputRecord> records = jobs.uploadDocumentProcessAndRunJob(
    struct.getId(),
    new File("invoice.pdf"),
    "Focus on extracting financial figures only",
    "json",
    null); // llmModelId: null uses the default model
```

The API uses the file extension to decide how to process it.

Supported file types include PDF, images (PNG, JPEG, WebP, HEIC, TIFF, AVIF, BMP), Office documents (DOC/DOCX, XLS/XLSX, PPT/PPTX), email (EML, MSG), RTF, and text formats (TXT, CSV, JSON, XML, HTML, Markdown, YAML and more). Audio is not supported on this endpoint.

## Synchronous vs. batch jobs

`uploadTextDocumentAndRunJob` and `uploadDocumentProcessAndRunJob` upload, analyze, and return results in a single request. They work best for short documents (under about 20,000 characters).

| Limit | Value |
|---|---|
| File size | 100 MB |
| Raw text files (.txt, .csv, .json, …) | 10 MB |
| Text request body | 10 MB |
| Wait time before returning `202` | About 100 seconds |

The generated synchronous methods parse the response as JSON records, so use `json` as the output type. To get CSV, run a batch job and call `getJobOutputCsv`.

### Handling `202` on synchronous jobs

If a synchronous job takes longer than the wait time, the API responds with `202` and the job keeps running. The response body is then an error message rather than records, and the job ID is only in the `stract-job-id` header.

The generated client tries to parse that body as records and throws an `ApiException` with code `0`, so the job ID is not available from the exception. To keep it, register a response interceptor on the client that records the header:

```java
ThreadLocal<String> lastJobId = new ThreadLocal<>();

ApiClient client = new ApiClient()
    .setRequestInterceptor(b -> b.header("Authorization", "Bearer " + apiKey))
    .setResponseInterceptor(r ->
        lastJobId.set(r.headers().firstValue("stract-job-id").orElse(null)));
```

After a failed synchronous call, read `lastJobId.get()` on the same thread, then poll the job and fetch its output as shown below. `200`, `202` and `422` responses from a synchronous job include `stract-document-id` and `stract-job-id` headers, so you can look up the document and job later.

### Batch jobs

For larger documents, or many documents at once, upload first and run an asynchronous job:

```java
// Upload a document
Document doc = documents.uploadDocumentNoCatalog(new File("contract.pdf"));

// Start a job across one or more documents
Job job = jobs.createJob(
    struct.getId(),
    new CreateJobRequest()
        .additionalInstructions("")
        .documentIds(List.of(doc.getId())));

// Poll until the job finishes
while (!Boolean.TRUE.equals(job.getIsComplete())) {
    Thread.sleep(5000);
    job = jobs.getJobById(job.getId());
}

// Fetch the results
if (job.getStatus() == JobStatus.COMPLETE) {
    List<JobOutputRecord> records = jobs.getJobOutputJson(job.getId());
    String csv = jobs.getJobOutputCsv(job.getId());
}
```

To upload into a catalog, use `uploadDocument(catalogId, file)` instead. A job can also run against every document in a catalog: set `catalogId` instead of `documentIds`.

## Choosing a model

List the available LLM models with `getLlmModels()`, then pass a model's ID as `llmModelId` on any job request. If you omit it, the job uses the default model.

```java
List<LlmModel> models = new LlmModelApi(client).getLlmModels();
```

## Error handling

Non-2xx responses throw an `ApiException`. The status code, headers and body are available on the exception.

```java
import com.stractapi.client.ApiException;

try {
    jobs.getJobById(jobId);
} catch (ApiException e) {
    System.err.println(e.getCode() + " " + e.getResponseBody());
    throw e;
}
```

Common statuses:

| Status | Meaning |
|---|---|
| `400` | Invalid request, unsupported file type, or file too large |
| `401` | Missing or invalid API key, or not permitted |
| `402` | Not enough credits |
| `422` | The job finished without producing output |

## Credits

Processing documents uses credits. Check your balance with `CreditsApi`, and see the history with `AccountCreditLedgerApi`:

```java
AccountCreditCache credits = new CreditsApi(client).getCurrentCredits();
AccountCreditCache orgCredits = new CreditsApi(client).getCurrentOrgCredits();
List<AccountCreditLedger> ledger =
    new AccountCreditLedgerApi(client).getAccountCreditLedgerForSpendingUser();
```

## Development

This SDK is generated from the OpenAPI spec with [OpenAPI Generator](https://openapi-generator.tech) (the `java` generator, `native` library) through the Maven plugin. The spec is read from the live URL on every build, and the generated sources are written to `target/`. There is no hand-written source to edit, and nothing generated is committed.

Requirements: Java 17+ and Maven, plus network access to fetch the spec.

```bash
mvn clean package   # regenerate from the live spec and build the jar
```

## Support

Questions, or need a file type that isn't supported? Contact [matt@stract-api.com](mailto:matt@stract-api.com).

## License

[MIT](LICENSE)
