package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Smoke test: runs the generated SDK against the live Stract API.
// Requires STRACT_API_KEY, read from .env in the repo root or the environment.
// Run with `mvn verify`.
class SmokeIT {

    private static ApiClient client;

    @BeforeAll
    static void setUp() {
        client = TestClient.create();
    }

    @Test
    void authenticatesAndReturnsTheCurrentUser() throws ApiException {
        User user = new UserApi(client).getMyUser();
        assertNotNull(user.getId());
        assertNotNull(user.getEmail());
        assertNotNull(user.getDtCreated());
    }

    @Test
    void rejectsAnInvalidApiKeyWith401() {
        UserApi unauthenticated = new UserApi(clientWithKey("invalid-key"));
        ApiException e = assertThrows(ApiException.class, unauthenticated::getMyUser);
        assertEquals(401, e.getCode());
    }

    @Test
    void listsLlmModels() throws ApiException {
        var models = new LlmModelApi(client).getLlmModels();
        assertNotNull(models);
        assertFalse(models.isEmpty());
    }

    @Test
    void listsStructs() throws ApiException {
        assertNotNull(new StructApi(client).getStructs());
    }

    @Test
    void returnsTheCreditBalance() throws ApiException {
        assertNotNull(new CreditsApi(client).getCurrentCredits());
    }

    private static ApiClient clientWithKey(String apiKey) {
        return TestClient.createWithKey(apiKey);
    }

}
