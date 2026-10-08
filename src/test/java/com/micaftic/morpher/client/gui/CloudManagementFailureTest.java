package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudHttpException;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudManagementFailureTest {
    @Test
    void preservesConnectionFailureInsteadOfDiscardingItForClosedChannel() throws Exception {
        ConnectException connection = new ConnectException();
        connection.initCause(new ClosedChannelException());
        assertSame(connection, unwrap(new CompletionException(new ExecutionException(connection))));
    }

    @Test
    void preservesIdentityProviderErrorForSavedAccountRecovery() throws Exception {
        CloudHttpException unavailable = new CloudHttpException(502, CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
                "Minecraft Session Service rejected the Cloud identity challenge");
        unavailable.initCause(new ConnectException());
        assertSame(unavailable, unwrap(new CompletionException(unavailable)));
        assertEquals("identity.provider_unavailable", CloudManagementScreen.errorKey(new CompletionException(unavailable)));
    }

    @Test
    void connectionFailureShowsActionableMessage() {
        ConnectException connection = new ConnectException();
        connection.initCause(new ClosedChannelException());
        assertEquals("error.connection", CloudManagementScreen.errorKey(new CompletionException(connection)));
    }

    @Test
    void registrationValidationSelectsLocalizedMessagesThroughAsyncFailures() {
        var account = new com.micaftic.morpher.cloud.client.CloudValidationException(
                com.micaftic.morpher.cloud.client.CloudValidationException.Reason.INVALID_ACCOUNT_ID);
        var password = new com.micaftic.morpher.cloud.client.CloudValidationException(
                com.micaftic.morpher.cloud.client.CloudValidationException.Reason.INVALID_PASSWORD);
        assertEquals("validation.account_id", CloudManagementScreen.errorKey(new CompletionException(account)));
        assertEquals("validation.password", CloudManagementScreen.errorKey(new ExecutionException(password)));
    }

    private static Throwable unwrap(Throwable failure) throws Exception {
        Method unwrap = CloudManagementScreen.class.getDeclaredMethod("unwrap", Throwable.class);
        unwrap.setAccessible(true);
        return (Throwable) unwrap.invoke(null, failure);
    }
}
