package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.Catalog;
import com.stractapi.model.CreateCatalogRequest;
import com.stractapi.model.Document;
import com.stractapi.model.UploadDocumentFromUrlRequest;
import com.stractapi.model.UploadTextDocumentRequest;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Every test deletes what it uploads.
class CatalogDocumentIT {

    private static ApiClient client;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() {
        client = TestClient.create();
    }

    @Test
    void catalogDocumentsLifecycle() throws ApiException, IOException {
        CatalogApi catalogs = new CatalogApi(client);
        DocumentApi documents = new DocumentApi(client);

        Catalog catalog = catalogs.createCatalog(
                new CreateCatalogRequest().name(TestClient.uniqueName("catalog")));
        UUID catalogId = catalog.getId();
        Document textDoc = null;
        Document fileDoc = null;
        try {
            assertTrue(catalogs.getCatalogs().stream().anyMatch(c -> catalogId.equals(c.getId())));
            assertTrue(documents.getCatalogDocuments(catalogId).isEmpty());

            textDoc = documents.uploadTextDocument(catalogId, new UploadTextDocumentRequest()
                    .name("catalog-note")
                    .text("Two hamburgers and a large fries"));
            fileDoc = documents.uploadDocument(catalogId, writeTemp("catalog-file.txt",
                    "Three hamburgers and a soda"));
            assertEquals(catalogId, textDoc.getCatalogId());
            assertEquals(catalogId, fileDoc.getCatalogId());
            assertEquals("txt", fileDoc.getExtension().replaceFirst("^\\.", ""));

            UUID textId = textDoc.getId();
            UUID fileId = fileDoc.getId();
            assertEquals(Set.of(textId, fileId), ids(documents, catalogId));
            Set<UUID> userDocs = documents.getUserDocuments().stream()
                    .map(Document::getId).collect(Collectors.toSet());
            assertTrue(userDocs.containsAll(Set.of(textId, fileId)));

            // Text is available immediately for text uploads.
            String text = documents.getDocumentText(catalogId, textId).getText();
            assertNotNull(text);
            assertTrue(text.contains("hamburgers"));

            // File uploads are processed asynchronously, so the text may still be null.
            String fileText = documents.getDocumentText(catalogId, fileId).getText();
            assertTrue(fileText == null || fileText.contains("hamburgers"));

            // The link points at a downloadable file, so it only exists for file uploads.
            assertTrue(documents.getDocumentLink(fileId).getLink().startsWith("http"));

            // Deleting one document leaves the rest of the catalog alone.
            assertEquals(fileId, documents.deleteDocument(fileId).getDocumentId());
            assertEquals(Set.of(textId), ids(documents, catalogId));
        } finally {
            // Deleting the catalog also deletes any documents still in it.
            catalogs.deleteCatalog(catalogId);
        }
        assertFalse(catalogs.getCatalogs().stream().anyMatch(c -> catalogId.equals(c.getId())));
        UUID textId = textDoc.getId();
        assertFalse(documents.getUserDocuments().stream().anyMatch(d -> textId.equals(d.getId())));
    }

    @Test
    void textDocumentWithoutACatalogLifecycle() throws ApiException {
        DocumentApi documents = new DocumentApi(client);
        Document doc = documents.uploadTextDocumentNoCatalog(new UploadTextDocumentRequest()
                .name(TestClient.uniqueName("text-doc"))
                .text("One hamburger, no onions"));
        UUID id = doc.getId();
        UUID removed;
        try {
            assertTrue(documents.getUserDocuments().stream().anyMatch(d -> id.equals(d.getId())));
            String text = documents.getDocumentText(doc.getCatalogId(), id).getText();
            assertNotNull(text);
            assertTrue(text.contains("hamburger"));
        } finally {
            removed = documents.deleteDocument(id).getDocumentId();
        }
        assertEquals(id, removed);
        assertFalse(documents.getUserDocuments().stream().anyMatch(d -> id.equals(d.getId())));
    }

    @Test
    void fileDocumentWithoutACatalogLifecycle() throws ApiException, IOException {
        DocumentApi documents = new DocumentApi(client);
        Document doc = documents.uploadDocumentNoCatalog(
                writeTemp(TestClient.uniqueName("file-doc") + ".txt", "Five hamburgers"));
        UUID id = doc.getId();
        UUID removed;
        try {
            assertEquals("txt", doc.getExtension().replaceFirst("^\\.", ""));
            assertTrue(documents.getUserDocuments().stream().anyMatch(d -> id.equals(d.getId())));
            assertTrue(documents.getDocumentLink(id).getLink().startsWith("http"));
        } finally {
            removed = documents.deleteDocument(id).getDocumentId();
        }
        assertEquals(id, removed);
        assertFalse(documents.getUserDocuments().stream().anyMatch(d -> id.equals(d.getId())));
    }

    // The source file is uploaded the normal way, then its presigned link is fed back into
    // uploadDocumentFromUrl to create a second document in the same catalog.
    @Test
    void documentFromUrlInACatalogLifecycle() throws ApiException, IOException {
        CatalogApi catalogs = new CatalogApi(client);
        DocumentApi documents = new DocumentApi(client);

        Catalog catalog = catalogs.createCatalog(
                new CreateCatalogRequest().name(TestClient.uniqueName("url-catalog")));
        UUID catalogId = catalog.getId();
        try {
            Document source = documents.uploadDocument(catalogId, writeTemp(
                    TestClient.uniqueName("url-source") + ".txt", "Four hamburgers and a shake"));
            String link = documents.getDocumentLink(source.getId()).getLink();
            assertTrue(link.startsWith("https"));

            Document copy = documents.uploadDocumentFromUrl(catalogId,
                    new UploadDocumentFromUrlRequest()
                            .url(URI.create(link))
                            .filename(TestClient.uniqueName("url-copy") + ".txt"));
            assertNotNull(copy.getId());
            assertNotEquals(source.getId(), copy.getId());
            assertEquals(catalogId, copy.getCatalogId());
            assertEquals("txt", copy.getExtension().replaceFirst("^\\.", ""));
            assertEquals(Set.of(source.getId(), copy.getId()), ids(documents, catalogId));
            assertTrue(documents.getDocumentLink(copy.getId()).getLink().startsWith("http"));
        } finally {
            // Deleting the catalog also deletes both documents.
            catalogs.deleteCatalog(catalogId);
        }
        assertFalse(catalogs.getCatalogs().stream().anyMatch(c -> catalogId.equals(c.getId())));
    }

    @Test
    void documentFromUrlWithoutACatalogLifecycle() throws ApiException, IOException {
        DocumentApi documents = new DocumentApi(client);
        Document source = documents.uploadDocumentNoCatalog(writeTemp(
                TestClient.uniqueName("url-source") + ".txt", "Six hamburgers"));
        Document copy = null;
        try {
            String link = documents.getDocumentLink(source.getId()).getLink();
            assertTrue(link.startsWith("https"));

            copy = documents.uploadDocumentFromUrlNoCatalog(new UploadDocumentFromUrlRequest()
                    .url(URI.create(link))
                    .filename(TestClient.uniqueName("url-copy") + ".txt"));
            assertNotNull(copy.getId());
            assertNotEquals(source.getId(), copy.getId());
            assertEquals("txt", copy.getExtension().replaceFirst("^\\.", ""));
            UUID copyId = copy.getId();
            assertTrue(documents.getUserDocuments().stream()
                    .anyMatch(d -> copyId.equals(d.getId())));
        } finally {
            if (copy != null) {
                documents.deleteDocument(copy.getId());
            }
            documents.deleteDocument(source.getId());
        }
        UUID sourceId = source.getId();
        UUID copyId = copy.getId();
        Set<UUID> remaining = documents.getUserDocuments().stream()
                .map(Document::getId).collect(Collectors.toSet());
        assertFalse(remaining.contains(sourceId));
        assertFalse(remaining.contains(copyId));
    }

    // The spec says only https URLs are accepted.
    @Test
    void documentFromUrlRejectsNonHttpsUrl() {
        DocumentApi documents = new DocumentApi(client);
        ApiException e = assertThrows(ApiException.class, () ->
                documents.uploadDocumentFromUrlNoCatalog(new UploadDocumentFromUrlRequest()
                        .url(URI.create("http://stract-api.com/file.txt"))));
        assertEquals(400, e.getCode());
    }

    private File writeTemp(String name, String content) throws IOException {
        return Files.writeString(tempDir.resolve(name), content).toFile();
    }

    private static Set<UUID> ids(DocumentApi documents, UUID catalogId) throws ApiException {
        return documents.getCatalogDocuments(catalogId).stream()
                .map(Document::getId).collect(Collectors.toSet());
    }

}
