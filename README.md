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
  <groupId>com.stract-api</groupId>
  <artifactId>stract-api</artifactId>
  <version>0.9.4</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.stract-api:stract-api:0.9.4")
```

Requires Java 17 or later.

## Authentication

Requests are authenticated with an API key, sent as a bearer token. To create one, sign in to the Stract app and go to **Account → API Keys**. Additional keys require a Team or Enterprise subscription.

Create one `ApiClient` with your API key:

```java
import com.stractapi.client.ApiClient;

ApiClient client = new ApiClient();
client.setBearerToken(System.getenv("STRACT_API_KEY"));
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

For synchronous jobs, always use the `…Call` variant of the method and check the status before reading the result. See [Why use the `…Call` variant for synchronous jobs](#why-use-the-call-variant-for-synchronous-jobs).

```java
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import okhttp3.Response;

SyncTextJobRequest request = new SyncTextJobRequest()
    .name("order-1042")
    .text("Two hamburgers and a large fries")
    .additionalInstructions("")
    .outputType(SyncTextJobRequest.OutputTypeEnum.JSON);

Type recordsType = new TypeToken<List<JobOutputRecord>>() {}.getType();

try (Response res = jobs.uploadTextDocumentAndRunJobCall(struct.getId(), request, null).execute()) {
    if (res.code() == 202) {
        // Still running: save the job ID and fetch the output later.
        String jobId = res.header("stract-job-id");
    } else {
        List<JobOutputRecord> records = client.handleResponse(res, recordsType);
        for (JobOutputRecord record : records) {
            System.out.println(record.getDocumentName());                           // order-1042
            System.out.println(record.getAdditionalProperties().get("hamburgers")); // 2
        }
    }
}
```

Each record has the document name plus one entry per field, available through `getAdditionalProperties()`.

### 4. Extract data from a file

```java
import java.io.File;

try (Response res = jobs.uploadDocumentProcessAndRunJobCall(
        struct.getId(),
        new File("invoice.pdf"),
        "Focus on extracting financial figures only",
        "json",
        null,   // llmModelId: null uses the default model
        null).execute()) {
    if (res.code() == 202) {
        String jobId = res.header("stract-job-id");
    } else {
        List<JobOutputRecord> records = client.handleResponse(res, recordsType);
    }
}
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

The examples above request JSON output. To get CSV, run a batch job and call `getJobOutputCsv`.

### Why use the `…Call` variant for synchronous jobs

Every endpoint has several methods: a plain one (`uploadTextDocumentAndRunJob`) that returns the parsed result, a `…WithHttpInfo` one that also returns the status code and headers, and a `…Call` one (`uploadTextDocumentAndRunJobCall`) that builds the request and lets you execute it yourself to get the raw OkHttp `Response`.

If a synchronous job takes longer than the wait time, the API responds with `202` and the job keeps running. The response body is then an error message rather than records, and the job ID is only in the `stract-job-id` header. The plain and `…WithHttpInfo` methods treat `202` as success and try to parse the body as records, so the parse fails and you lose the job ID. The `…Call` variant lets you check `res.code()` first and read the job ID with `res.header("stract-job-id")`. Use it to poll the job and fetch its output, as shown below. Pass `null` for the final callback argument. Because it skips the generated null checks on required arguments, make sure those are set.

`200`, `202` and `422` responses from a synchronous job include `stract-document-id` and `stract-job-id` headers, so you can look up the document and job later.

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

This SDK is generated from the OpenAPI spec with [OpenAPI Generator](https://openapi-generator.tech) (the `java` generator, `okhttp-gson` library) through the Maven plugin. The spec is read from the live URL on every build, and the generated sources are written to `target/`. There is no hand-written source to edit, and nothing generated is committed.

Requirements: Java 17+ and Maven, plus network access to fetch the spec.

```bash
mvn clean package   # regenerate from the live spec and build the jar
mvn verify          # also run the smoke test against the live API
```

The smoke test (`SmokeIT`) reads `STRACT_API_KEY` from a `.env` file in the repo root, falling back to the environment:

```
STRACT_API_KEY=your-api-key
```

### Publishing a new version

Releases go to [Maven Central](https://central.sonatype.com) as `com.stract-api:stract-api`. A published version can never be changed or deleted, so test before you deploy. All steps below use the **Maven** tool window in IntelliJ (View → Tool Windows → Maven).

One-time setup, already done on the release machine:

- The `com.stract-api` namespace is verified in the Central Portal.
- A Central Portal user token is saved in `~/.m2/settings.xml` under a `<server>` with id `central`.
- A GPG key exists and its public key is on a keyserver such as `keyserver.ubuntu.com`.

Steps for each release:

1. **Regenerate the SDK.** Under **Lifecycle**, select **clean** and **compile** (Ctrl+click both) and run
2. **Set the version to match the API spec.**
   - **Where to find it:** the spec downloaded in step 1, at `target/generated-sources/openapi/api/openapi.yaml`. Open it and read the `version:` value under the top-level `info:` section (around line 17). The same file is also served at [api.stract-api.com/spec/openapi.yaml](https://api.stract-api.com/spec/openapi.yaml).
   - **What to update:**
     - `<version>` in `pom.xml`, the project's own version near the top of the file (line 9, directly under `<artifactId>stract-api</artifactId>`). Leave the plugin and dependency versions alone.
     - The `<version>` in the Maven snippet under **Installation** in this README.
     - The version in the Gradle `implementation("com.stract-api:stract-api:…")` line under **Installation** in this README.
     - The tag name in the tagging step below (`v` followed by the version).
   - Then click **Reload All Maven Projects** in the Maven window.
3. **Check the hand-written docs against the regenerated code.** The SDK itself is generated, but the README is not. Compare it with the new classes under `target/generated-sources/openapi/` and update anything that drifted:
   - The **API groups** table: methods added, renamed or removed in the `*Api` classes.
   - The code examples and the `…Call` section: method signatures, argument order, and model getters.
   - The **Common statuses** table under Error handling, if the API's responses changed.
   - The copyright year in `LICENSE`, once a year.
4. **Run the smoke test.** With the `release` profile **unticked**, run **verify**. This needs `STRACT_API_KEY` in `.env`. Fix any failures before going on.
5. **Dry-run the release build.** Under **Profiles**, tick **release**. Run **verify** again. This also builds the sources and javadoc jars and signs everything with GPG, but uploads nothing. A passphrase prompt appears from gpg's own window, which can open behind IntelliJ.
6. **Deploy.** With **release** still ticked, run **deploy**. This repeats the build and the smoke test, then uploads a bundle to the Central Portal. To skip the smoke test, use a run configuration (Run → Edit Configurations → **+** → Maven) with the command line `deploy -DskipITs` and the `release` profile enabled.
7. **Publish in the portal.** Sign in at [central.sonatype.com](https://central.sonatype.com), open **Deployments**, wait for the upload to show as validated, then click **Publish**.
8. **Tag the release in git.** Commit and push the release changes. Then, in **Terminal** run the following, replacing `v0.9.4` with `v` plus the new version:
   ```
   git tag v0.9.4 && git push origin v0.9.4
   ```
9. **Confirm it's live.** The artifact usually appears at `repo1.maven.org/maven2/com/stract-api/stract-api/` within 15–30 minutes. Untick the `release` profile when you're done so later builds don't try to sign.

## Support

Questions, or need a file type that isn't supported? Contact [matt@stract-api.com](mailto:matt@stract-api.com).

## License

[MIT](LICENSE)
