package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.AccountUser;
import com.stractapi.model.ApiKey;
import com.stractapi.model.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Endpoints that only read state, so there is nothing to clean up.
class ReadOnlyIT {

    private static ApiClient client;

    @BeforeAll
    static void setUp() {
        client = TestClient.create();
    }

    @Test
    void getsTheCurrentUser() throws ApiException {
        User user = new UserApi(client).getMyUser();
        assertNotNull(user.getEmail());
        assertTrue(user.getIsActive());
    }

    @Test
    void getsTheAccount() throws ApiException {
        var account = new AccountApi(client).getAccount();
        assertNotNull(account.getName());
        assertNotNull(account.getAccountUserRole());
    }

    @Test
    void listsAccountUsers() throws ApiException {
        List<AccountUser> users = TestClient.skipIfUnauthorized(
                "Listing account users requires an admin key",
                () -> new AccountApi(client).getAccountUsers());
        UUID me = new UserApi(client).getMyUser().getId();
        assertTrue(users.stream().anyMatch(u -> me.equals(u.getSuserId())),
                "current user should be listed among the account users");
    }

    @Test
    void listsApiKeys() throws ApiException {
        List<ApiKey> keys = TestClient.skipIfUnauthorized(
                "Listing API keys requires an admin key",
                () -> new ApiKeyApi(client).getApiKeys());
        assertFalse(keys.isEmpty());
        String apiKey = TestClient.apiKey();
        assertTrue(keys.stream().anyMatch(k -> apiKey.endsWith(k.getKeyLastFour())));
    }

    @Test
    void listsTheCreditLedger() throws ApiException {
        assertNotNull(new AccountCreditLedgerApi(client).getAccountCreditLedgerForSpendingUser());
    }

    @Test
    void returnsBothCreditBalances() throws ApiException {
        CreditsApi api = new CreditsApi(client);
        assertNotNull(api.getCurrentCredits());
        assertNotNull(api.getCurrentOrgCredits());
    }

    @Test
    void unknownIdsRaiseApiException() {
        UUID missing = UUID.randomUUID();
        for (var call : List.<TestClient.ApiCall<?>>of(
                () -> new JobApi(client).getJobById(missing),
                () -> new DocumentApi(client).deleteDocument(missing),
                () -> new FieldApi(client).deleteField(missing))) {
            ApiException e = assertThrows(ApiException.class, call::run);
            assertTrue(List.of(400, 401, 404).contains(e.getCode()), "status " + e.getCode());
        }
    }

}
