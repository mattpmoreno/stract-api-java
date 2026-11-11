package com.stractapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.stractapi.client.ApiClient;
import com.stractapi.client.ApiException;
import com.stractapi.model.Account;
import com.stractapi.model.EditAccountRequest;
import com.stractapi.model.EditMyUserRequest;
import com.stractapi.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Edits the account and user, then restores the original values.
// createAccountUser is not covered: the API has no way to remove a user again.
class AccountUserIT {

    private static ApiClient client;

    @BeforeAll
    static void setUp() {
        client = TestClient.create();
    }

    @Test
    void editsTheAccountNameAndRestoresIt() throws ApiException {
        AccountApi api = new AccountApi(client);
        Account original = api.getAccount();
        String renamed = TestClient.uniqueName("account");
        Account edited = TestClient.skipIfUnauthorized(
                "Editing the account requires an admin key",
                () -> api.editAccount(new EditAccountRequest().name(renamed)));
        Account restored;
        try {
            assertEquals(renamed, edited.getName());
            assertEquals(renamed, api.getAccount().getName());
        } finally {
            restored = api.editAccount(new EditAccountRequest().name(original.getName()));
        }
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getId(), restored.getId());
    }

    @Test
    void editsMyUserAndRestoresIt() throws ApiException {
        UserApi api = new UserApi(client);
        User original = api.getMyUser();
        String temp = TestClient.uniqueName("first");
        User restored;
        try {
            User edited = api.editMyUser(new EditMyUserRequest().firstName(temp));
            assertEquals(temp, edited.getFirstName());
            assertEquals(temp, api.getMyUser().getFirstName());
        } finally {
            restored = api.editMyUser(new EditMyUserRequest().firstName(original.getFirstName()));
        }
        assertEquals(original.getFirstName(), restored.getFirstName());
        assertEquals(original.getLastName(), restored.getLastName());
    }

}
