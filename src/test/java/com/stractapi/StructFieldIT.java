package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.CreateFieldRequest;
import com.stractapi.model.CreateStructRequest;
import com.stractapi.model.EditFieldRequest;
import com.stractapi.model.Field;
import com.stractapi.model.FieldType;
import com.stractapi.model.Struct;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Create -> read -> edit -> delete sequences that end where they started.
class StructFieldIT {

    private static ApiClient client;
    private static Struct sharedStruct;

    @BeforeAll
    static void setUp() throws ApiException {
        client = TestClient.create();
        sharedStruct = TestClient.createHamburgerStruct(client);
    }

    @AfterAll
    static void tearDown() throws ApiException {
        new StructApi(client).archiveStruct(sharedStruct.getId());
    }

    @Test
    void createsListsAndArchivesAStruct() throws ApiException {
        StructApi structs = new StructApi(client);
        Struct created = structs.createStruct(
                new CreateStructRequest().name(TestClient.uniqueName("struct")));
        UUID id = created.getId();
        UUID archivedId;
        try {
            assertTrue(created.getName().startsWith("sdk-it-struct-"));
            assertTrue(structs.getStructs().stream().anyMatch(s -> id.equals(s.getId())));
            // A fresh struct has no fields and no jobs.
            assertTrue(new FieldApi(client).getFields(id).isEmpty());
            assertTrue(new JobApi(client).getStructJobs(id).isEmpty());
        } finally {
            archivedId = structs.archiveStruct(id);
        }
        assertEquals(id, archivedId);
    }

    @Test
    void createsEditsAndDeletesFields() throws ApiException {
        FieldApi api = new FieldApi(client);
        UUID structId = sharedStruct.getId();
        Set<UUID> baseline = ids(api.getFields(structId));

        List<Field> created = api.createFields(structId, List.of(
                new CreateFieldRequest()
                        .name("customer_name")
                        .description("Name of the customer")
                        .type(FieldType.STRING),
                new CreateFieldRequest()
                        .name("size")
                        .description("Size of the drink")
                        .type(FieldType.ENUM)
                        .enumValues(List.of("small", "large"))));
        try {
            Field customer = byName(created, "customer_name");
            Field size = byName(created, "size");
            assertEquals(List.of("small", "large"), size.getEnumValues());

            // Edit a plain property.
            Field renamed = api.editField(customer.getId(), new EditFieldRequest()
                    .name("client_name")
                    .description("Name of the client"));
            assertEquals("client_name", renamed.getName());
            assertEquals("Name of the client", renamed.getDescription());
            assertEquals(FieldType.STRING, renamed.getType());

            // Edit enum values.
            Field resized = api.editField(size.getId(),
                    new EditFieldRequest().enumValues(List.of("small", "medium", "large")));
            assertEquals(List.of("small", "medium", "large"), resized.getEnumValues());

            List<Field> listed = api.getFields(structId);
            assertTrue(ids(listed).containsAll(ids(created)));
            assertEquals("client_name", listed.stream()
                    .filter(f -> customer.getId().equals(f.getId()))
                    .findFirst().orElseThrow().getName());
        } finally {
            for (Field f : created) {
                assertEquals(f.getId(), api.deleteField(f.getId()).getFieldId());
            }
        }
        assertEquals(baseline, ids(api.getFields(structId)));
    }

    private static Set<UUID> ids(List<Field> fields) {
        return fields.stream().map(Field::getId).collect(Collectors.toSet());
    }

    private static Field byName(List<Field> fields, String name) {
        return fields.stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow();
    }

}
