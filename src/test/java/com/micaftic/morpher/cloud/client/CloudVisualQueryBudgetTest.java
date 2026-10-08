package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CloudVisualQueryBudgetTest {
    @Test void smallVisualBudgetSplitsQueriesEvenWhenTheUuidCountIsAllowed() {
        var instance = CloudInstanceConfig.v1("test", URI.create("https://cloud.example"));
        var info = new CloudInstanceInfo(instance,65536,16777216,262144,134217728,128,15,45,false,null,64,Set.of(),1024,32);
        var context = new EntityDisplayContext("test","a".repeat(64),"scope","e".repeat(64),"minecraft:"+"d".repeat(246),1);
        int batch = CloudVisualQueryBudget.batchLimit(context, info);
        assertTrue(batch < 64 && batch > 0);
        var ids = java.util.stream.IntStream.range(0,batch).mapToObj(i -> UUID.randomUUID()).toList();
        assertTrue(CloudVisualQueryBudget.request(context,ids,info).toString().getBytes(StandardCharsets.UTF_8).length <= 1024);
        assertThrows(IllegalArgumentException.class, () -> CloudVisualQueryBudget.request(context, java.util.stream.IntStream.range(0,64).mapToObj(i -> UUID.randomUUID()).toList(),info));
    }
}
