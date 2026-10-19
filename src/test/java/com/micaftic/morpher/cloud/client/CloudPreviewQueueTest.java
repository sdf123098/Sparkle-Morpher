package com.micaftic.morpher.cloud.client;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
class CloudPreviewQueueTest {
 @Test void visibleCardsLoadBeforeAnyApplicationAndConcurrencyIsBounded() {
  var futures=new HashMap<String,CompletableFuture<Void>>();var calls=new ArrayList<String>();
  var queue=new CloudPreviewQueue<String>(2,id->{calls.add(id);var f=new CompletableFuture<Void>();futures.put(id,f);return f;});
  queue.update(List.of("a","b","c"));assertEquals(List.of("a","b"),calls);
  futures.get("a").complete(null);assertEquals(List.of("a","b","c"),calls);
  queue.update(List.of("a","b","c"));assertEquals(3,calls.size());
 }
 @Test void pagingDiscardsQueuedCardsAndFailuresDoNotRetryEveryFrame() {
  var futures=new HashMap<String,CompletableFuture<Void>>();var calls=new ArrayList<String>();
  var queue=new CloudPreviewQueue<String>(1,id->{calls.add(id);var f=new CompletableFuture<Void>();futures.put(id,f);return f;});
  queue.update(List.of("a","b"));queue.update(List.of("c"));futures.get("a").complete(null);
  assertEquals(List.of("a","c"),calls);futures.get("c").completeExceptionally(new RuntimeException("denied"));
  for(int i=0;i<10;i++)queue.update(List.of("c"));assertEquals(2,calls.size());
  queue.retry();assertEquals(List.of("a","c","c"),calls);
 }
 @Test void closingPreventsWorkFromAnOldScreenOrAccount() {
  var first=new CompletableFuture<Void>();var calls=new ArrayList<String>();
  var queue=new CloudPreviewQueue<String>(1,id->{calls.add(id);return first;});
  queue.update(List.of("a","b"));queue.close();first.complete(null);queue.update(List.of("c"));
  assertEquals(List.of("a"),calls);
 }
}
