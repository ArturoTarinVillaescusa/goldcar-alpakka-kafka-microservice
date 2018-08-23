/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage.tobeused;


import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Pair;
import akka.kafka.*;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage.KafkaAlpakka;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

// Consume messages and store a representation, including offset, in OffsetStorage
class ExternalOffsetStorage extends KafkaAlpakka {
  public static void main(String[] args) {
    new ExternalOffsetStorage().demo();
  }

  public void demo() {
    // #plainSource
    final OffsetStorage db = new OffsetStorage();

    CompletionStage<akka.kafka.javadsl.Consumer.Control> controlCompletionStage =
        db.loadOffset().thenApply(fromOffset -> {
            return akka.kafka.javadsl.Consumer
                .plainSource(
                    consumerSettings,
                    Subscriptions.assignmentWithOffset(
                        new TopicPartition("topic1", /* partition: */  0),
                        fromOffset
                    )
                )
                .mapAsync(1, db::businessLogicAndStoreOffset)
                .to(Sink.ignore())
                .run(materializer);
        });
    // #plainSource
  }

  // #plainSource


  class OffsetStorage {
  // #plainSource
    private final AtomicLong offsetStore = new AtomicLong();

  // #plainSource
    public CompletionStage<Done> businessLogicAndStoreOffset(ConsumerRecord<String, byte[]> record) { // ... }
  // #plainSource
      offsetStore.set(record.offset());
      return CompletableFuture.completedFuture(Done.getInstance());
    }

  // #plainSource
    public CompletionStage<Long> loadOffset() { // ... }
  // #plainSource

      return CompletableFuture.completedFuture(offsetStore.get());
    }

  // #plainSource
  }
  // #plainSource

}

// Consume messages at-most-once
class AtMostOnce extends KafkaAlpakka {
  public static void main(String[] args) {
    new AtMostOnce().demo();
  }


  public void demo() {
    // #atMostOnce
    akka.kafka.javadsl.Consumer.Control control =
        akka.kafka.javadsl.Consumer
            .atMostOnceSource(consumerSettings, Subscriptions.topics("topic1"))
            .mapAsync(10, record -> business(record.key(), record.value()))
            .to(Sink.foreach(it -> System.out.println("Done with " + it)))
            .run(materializer);

    // #atMostOnce
  }

  // #atMostOnce
  CompletionStage<String> business(String key, byte[] value) { // .... }
  // #atMostOnce
    return CompletableFuture.completedFuture("");
  }
}

// Consume messages at-least-once
class KafkaAlpakkaConsumerAtLeastOnce extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerAtLeastOnce().consumeMessages();
  }

  public void consumeMessages() {
    // #atLeastOnce
    akka.kafka.javadsl.Consumer.Control control =
        akka.kafka.javadsl.Consumer
            .committableSource(consumerSettings, Subscriptions.topics("topic1"))
            .mapAsync(1, msg ->
                business(msg.record().key(), msg.record().value())
                    .thenApply(done -> msg.committableOffset()))
            .mapAsync(1, offset -> offset.commitJavadsl())
            .to(Sink.ignore())
            .run(materializer);
    // #atLeastOnce
  }

  // #atLeastOnce

  CompletionStage<String> business(String key, byte[] value) { // .... }
  // #atLeastOnce
    return CompletableFuture.completedFuture("");
  }

}

// Consume messages at-least-once, and commit in batches
class AtLeastOnceWithBatchCommit extends KafkaAlpakka {
  public static void main(String[] args) {
    new AtLeastOnceWithBatchCommit().demo();
  }

  public void demo() {
    // #atLeastOnceBatch
    akka.kafka.javadsl.Consumer.Control control =
        akka.kafka.javadsl.Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1"))
            .mapAsync(1, msg ->
                business(msg.record().key(), msg.record().value())
                        .thenApply(done -> msg.committableOffset())
            )
            .batch(
                20,
                ConsumerMessage::createCommittableOffsetBatch,
                ConsumerMessage.CommittableOffsetBatch::updated
            )
            .mapAsync(3, c -> c.commitJavadsl())
            .to(Sink.ignore())
            .run(materializer);
    // #atLeastOnceBatch
  }

  CompletionStage<String> business(String key, byte[] value) { // .... }
    return CompletableFuture.completedFuture("");
  }
}

// Connect a KafkaAlpakka to KafkaAlpakkaProducer
class KafkaAlpakkaConsumerToProducerSink extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerToProducerSink().demo();
  }

  public void demo() {
    // #consumerToProducerSink
    akka.kafka.javadsl.Consumer.Control control =
        akka.kafka.javadsl.Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1", "topic2"))
          .map(msg ->
              new ProducerMessage.Message<String, byte[], ConsumerMessage.Committable>(
                  new ProducerRecord<>("targetTopic", msg.record().key(), msg.record().value()),
                  msg.committableOffset()
              )
          )
          .to(Producer.commitableSink(consumerToProducerSettings))
          .run(materializer);
    // #consumerToProducerSink
    control.shutdown();
  }
}


// Connect a KafkaAlpakka to KafkaAlpakkaProducer, and commit in batches
class KafkaAlpakkaConsumerToProducerWithBatchCommits extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerToProducerWithBatchCommits().demo();
  }

  public void demo() {
    // #consumerToProducerFlowBatch
    Source<ConsumerMessage.CommittableOffset, akka.kafka.javadsl.Consumer.Control> source =
      akka.kafka.javadsl.Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1"))
      .map(msg -> {
          ProducerMessage.Envelope<String, byte[], ConsumerMessage.CommittableOffset> prodMsg =
              new ProducerMessage.Message<>(
                  new ProducerRecord<>("topic2", msg.record().value()),
                  msg.committableOffset()
              );
          return prodMsg;
      })
      .via(Producer.flexiFlow(consumerToProducerSettings))
      .map(result -> result.passThrough());

    source
        .batch(
          20,
          ConsumerMessage::createCommittableOffsetBatch,
          ConsumerMessage.CommittableOffsetBatch::updated
        )
        .mapAsync(3, c -> c.commitJavadsl())
        .runWith(Sink.ignore(), materializer);
    // #consumerToProducerFlowBatch
  }
}

// Connect a KafkaAlpakka to KafkaAlpakkaProducer, and commit in batches
class KafkaAlpakkaConsumerToProducerWithBatchCommits2 extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerToProducerWithBatchCommits2().demo();
  }

  public void demo() {
    Source<ConsumerMessage.CommittableOffset, akka.kafka.javadsl.Consumer.Control> source =
      akka.kafka.javadsl.Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1"))
      .map(msg -> {
          ProducerMessage.Envelope<String, byte[], ConsumerMessage.CommittableOffset> prodMsg =
              new ProducerMessage.Message<>(
                  new ProducerRecord<>("topic2", msg.record().value()),
                  msg.committableOffset()
              );
          return prodMsg;
      })
      .via(Producer.flexiFlow(consumerToProducerSettings))
      .map(result -> result.passThrough());

      // #groupedWithin
      source
        .groupedWithin(20, java.time.Duration.of(5, ChronoUnit.SECONDS))
        .map(ConsumerMessage::createCommittableOffsetBatch)
        .mapAsync(3, c -> c.commitJavadsl())
      // #groupedWithin
        .runWith(Sink.ignore(), materializer);
  }
}

// Backpressure per partition with batch commit
class KafkaAlpakkaConsumerWithPerPartitionBackpressure extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerWithPerPartitionBackpressure().demo();
  }

  public void demo() {
    final Executor ec = Executors.newCachedThreadPool();
    // #committablePartitionedSource
    akka.kafka.javadsl.Consumer.DrainingControl<Done> control =
        akka.kafka.javadsl.Consumer
            .committablePartitionedSource(consumerSettings, Subscriptions.topics("topic1"))
            .flatMapMerge(maxPartitions, Pair::second)
            .via(business())
            .map(msg -> msg.committableOffset())
            .batch(
                100,
                ConsumerMessage::createCommittableOffsetBatch,
                ConsumerMessage.CommittableOffsetBatch::updated
            )
            .mapAsync(3, offsets -> offsets.commitJavadsl())
            .toMat(Sink.ignore(), Keep.both())
            .mapMaterializedValue(akka.kafka.javadsl.Consumer::createDrainingControl)
            .run(materializer);
    // #committablePartitionedSource
    control.drainAndShutdown(ec);
  }
}

class KafkaAlpakkaConsumerWithIndependentFlowsPerPartition extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerWithIndependentFlowsPerPartition().demo();
  }

  public void demo() {
    final Executor ec = Executors.newCachedThreadPool();
    // #committablePartitionedSource-stream-per-partition
    akka.kafka.javadsl.Consumer.DrainingControl<Done> control =
        akka.kafka.javadsl.Consumer
            .committablePartitionedSource(consumerSettings, Subscriptions.topics("topic1"))
            .map(pair -> {
                Source<ConsumerMessage.CommittableMessage<String, byte[]>, NotUsed> source = pair.second();
                return source
                    .via(business())
                    .mapAsync(1, message -> message.committableOffset().commitJavadsl())
                    .runWith(Sink.ignore(), materializer);
            })
            .mapAsyncUnordered(maxPartitions, completion -> completion)
            .toMat(Sink.ignore(), Keep.both())
            .mapMaterializedValue(akka.kafka.javadsl.Consumer::createDrainingControl)
            .run(materializer);
    // #committablePartitionedSource-stream-per-partition
    control.drainAndShutdown(ec);
  }
}

class ExternallyControlledKafkaKafkaAlpakkaConsumer extends KafkaAlpakka {
  public static void main(String[] args) {
    new ExternallyControlledKafkaKafkaAlpakkaConsumer().demo();
  }

  public void demo() {
    ActorRef self = system.deadLetters();
    // #consumerActor
    //KafkaAlpakkaConsumer is represented by actor
    ActorRef consumer = system.actorOf((KafkaConsumerActor.props(consumerSettings)));

    //Manually assign topic partition to it
    akka.kafka.javadsl.Consumer.Control controlPartition1 = akka.kafka.javadsl.Consumer
        .plainExternalSource(
            consumer,
            Subscriptions.assignment(new TopicPartition("topic1", 1))
        )
        .via(business())
        .to(Sink.ignore())
        .run(materializer);

    //Manually assign another topic partition
    akka.kafka.javadsl.Consumer.Control controlPartition2 = akka.kafka.javadsl.Consumer
        .plainExternalSource(
            consumer,
            Subscriptions.assignment(new TopicPartition("topic1", 2))
        )
        .via(business())
        .to(Sink.ignore())
        .run(materializer);

    consumer.tell(KafkaConsumerActor.stop(), self);
    // #consumerActor
  }
}


class RebalanceListenerCallbacks extends KafkaAlpakka {
  public static void main(String[] args) {
    new ExternallyControlledKafkaKafkaAlpakkaConsumer().demo();
  }

  // #withRebalanceListenerActor
  class RebalanceListener extends AbstractLoggingActor {

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(TopicPartitionsAssigned.class, assigned -> {
            log().info("Assigned: {}", assigned);
          })
          .match(TopicPartitionsRevoked.class, revoked -> {
            log().info("Revoked: {}", revoked);
          })
          .build();
    }
  }

  // #withRebalanceListenerActor

  public void demo(ActorSystem system) {
    // #withRebalanceListenerActor
    ActorRef rebalanceListener = this.system.actorOf(Props.create(RebalanceListener.class));

    Subscription subscription = Subscriptions.topics("topic")
        // additionally, pass the actor reference:
        .withRebalanceListener(rebalanceListener);

    // use the subscription as usual:
    akka.kafka.javadsl.Consumer
      .plainSource(consumerSettings, subscription);
    // #withRebalanceListenerActor
  }

}

class KafkaAlpakkaConsumerMetrics extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerMetrics().demo();
  }

  public void demo() {
    // #consumerMetrics
    // run the stream to obtain the materialized Control value
    akka.kafka.javadsl.Consumer.Control control = akka.kafka.javadsl.Consumer
        .plainSource(consumerSettings, Subscriptions.assignment(new TopicPartition("topic1", 2)))
        .via(business())
        .to(Sink.ignore())
        .run(materializer);

    CompletionStage<Map<MetricName, Metric>> metrics = control.getMetrics();
    metrics.thenAccept(map -> System.out.println("Metrics: " + map));
    // #consumerMetrics
  }
}

// Shutdown via KafkaAlpakkaConsumer.Control
class ShutdownPlainSource extends KafkaAlpakka {
  public static void main(String[] args) {
    new ExternalOffsetStorage().demo();
  }

  public void demo() {
    // #shutdownPlainSource
    final OffsetStorage db = new OffsetStorage();

    db.loadOffset().thenAccept(fromOffset -> {
      akka.kafka.javadsl.Consumer.Control control = akka.kafka.javadsl.Consumer
          .plainSource(
              consumerSettings,
              Subscriptions.assignmentWithOffset(new TopicPartition("topic1", 0), fromOffset)
          )
          .mapAsync(10, record -> {
              return business(record.key(), record.value())
                  .thenApply(res -> db.storeProcessedOffset(record.offset()));
          })
          .toMat(Sink.ignore(), Keep.left())
          .run(materializer);

      // Shutdown the consumer when desired
      control.shutdown();
    });
    // #shutdownPlainSource
  }

  CompletionStage<String> business(String key, byte[] value) { // .... }
    return CompletableFuture.completedFuture("");
  }

  class OffsetStorage {

    private final AtomicLong offsetStore = new AtomicLong();

    public CompletionStage<Done> storeProcessedOffset(long offset) { // ... }
      offsetStore.set(offset);
      return CompletableFuture.completedFuture(Done.getInstance());
    }

    public CompletionStage<Long> loadOffset() { // ... }
      return CompletableFuture.completedFuture(offsetStore.get());
    }

  }

}

// Shutdown when batching commits
class ShutdownCommittableSource extends KafkaAlpakka {
  public static void main(String[] args) {
    new KafkaAlpakkaConsumerAtLeastOnce().consumeMessages();
  }

  public void demo() {
    // #shutdownCommitableSource
    final Executor ec = Executors.newCachedThreadPool();

    akka.kafka.javadsl.Consumer.DrainingControl<Done> control =
        akka.kafka.javadsl.Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1"))
            .mapAsync(1, msg ->
                business(msg.record().key(), msg.record().value()).thenApply(done -> msg.committableOffset()))
            .batch(20,
                first -> ConsumerMessage.createCommittableOffsetBatch(first),
                (batch, elem) -> batch.updated(elem)
            )
            .mapAsync(3, c -> c.commitJavadsl())
            .toMat(Sink.ignore(), Keep.both())
            .mapMaterializedValue(akka.kafka.javadsl.Consumer::createDrainingControl)
            .run(materializer);

    control.drainAndShutdown(ec);
    // #shutdownCommitableSource
  }

  CompletionStage<String> business(String key, byte[] value) { // .... }
    return CompletableFuture.completedFuture("");
  }

}

