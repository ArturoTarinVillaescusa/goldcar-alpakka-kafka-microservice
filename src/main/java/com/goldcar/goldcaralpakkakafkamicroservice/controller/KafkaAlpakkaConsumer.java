/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.goldcar.goldcaralpakkakafkamicroservice.controller;


import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Pair;
import akka.kafka.*;
import akka.kafka.javadsl.Producer;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.*;
import com.typesafe.config.Config;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

abstract class KafkaAlpakkaConsumer {
  protected final ActorSystem system = ActorSystem.create("example");

  protected final Materializer materializer = ActorMaterializer.create(system);

  protected final int maxPartitions = 100;

  protected <T> Flow<T, T, NotUsed> business() {
    return Flow.create();
  }

  // #settings
  final Config config = system.settings().config().getConfig("akka.kafka.consumer");
  final ConsumerSettings<String, byte[]> consumerSettings =
      ConsumerSettings.create(config, new StringDeserializer(), new ByteArrayDeserializer())
          //.withBootstrapServers("localhost:9092");
          // HARDCODED TO THE KUBERNETES KAFKA STANDALONE DEPLOYMENT!!!
          // NEEDS TO BE CONFIGURABLE.
          .withBootstrapServers("kafka-0.kafka-hs.default.svc.cluster.local:9093")
          .withGroupId("group1")
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
  // #settings

  final ConsumerSettings<String, byte[]> consumerSettingsWithAutoCommit =
          // #settings-autocommit
          consumerSettings
                  .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
                  .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000");
          // #settings-autocommit

  protected final ProducerSettings<String, byte[]> producerSettings =
      ProducerSettings.create(system, new StringSerializer(), new ByteArraySerializer())
          .withBootstrapServers("localhost:9092");


}

// Consume messages and store a representation, including offset, in OffsetStorage
class ExternalOffsetStorage extends KafkaAlpakkaConsumer {
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
class AtMostOnce extends KafkaAlpakkaConsumer {
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
class KafkaAlpakkaConsumerAtLeastOnce extends KafkaAlpakkaConsumer {
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
class AtLeastOnceWithBatchCommit extends KafkaAlpakkaConsumer {
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

// Connect a KafkaAlpakkaConsumer to KafkaAlpakkaProducer
class KafkaAlpakkaConsumerToProducerSink extends KafkaAlpakkaConsumer {
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
          .to(Producer.commitableSink(producerSettings))
          .run(materializer);
    // #consumerToProducerSink
    control.shutdown();
  }
}

// Connect a KafkaAlpakkaConsumer to KafkaAlpakkaProducer
class KafkaAlpakkaConsumerToProducerFlexiFlow extends KafkaAlpakkaConsumer {
  public void consumeMessages(String topicoDelQueConsumimos, String topicoAlQueProducimos) {
      // #consumerToProducerFlow
      akka.kafka.javadsl.Consumer.DrainingControl<Done> control =
          akka.kafka.javadsl.Consumer.committableSource(consumerSettings, Subscriptions.topics(topicoDelQueConsumimos))
              .map(msg -> {
                ProducerMessage.Envelope<String, byte[], ConsumerMessage.Committable> prodMsg =
                    new ProducerMessage.Message<>(
                        new ProducerRecord<>(topicoAlQueProducimos, msg.record().value()),
                        msg.committableOffset() // the passThrough
                    );
                return prodMsg;
              })

              .via(Producer.flexiFlow(producerSettings))

              .mapAsync(producerSettings.parallelism(), result -> {
                  ConsumerMessage.Committable committable = result.passThrough();
                  return committable.commitJavadsl();
              })
              .toMat(Sink.ignore(), Keep.both())
              .mapMaterializedValue(akka.kafka.javadsl.Consumer::createDrainingControl)
              .run(materializer);
      // #consumerToProducerFlow
  }
}

// Connect a KafkaAlpakkaConsumer to KafkaAlpakkaProducer, and commit in batches
class KafkaAlpakkaConsumerToProducerWithBatchCommits extends KafkaAlpakkaConsumer {
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
      .via(Producer.flexiFlow(producerSettings))
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

// Connect a KafkaAlpakkaConsumer to KafkaAlpakkaProducer, and commit in batches
class KafkaAlpakkaConsumerToProducerWithBatchCommits2 extends KafkaAlpakkaConsumer {
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
      .via(Producer.flexiFlow(producerSettings))
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
class KafkaAlpakkaConsumerWithPerPartitionBackpressure extends KafkaAlpakkaConsumer {
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

class KafkaAlpakkaConsumerWithIndependentFlowsPerPartition extends KafkaAlpakkaConsumer {
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

class ExternallyControlledKafkaKafkaAlpakkaConsumer extends KafkaAlpakkaConsumer {
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


class RebalanceListenerCallbacks extends KafkaAlpakkaConsumer {
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

class KafkaAlpakkaConsumerMetrics extends KafkaAlpakkaConsumer {
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
class ShutdownPlainSource extends KafkaAlpakkaConsumer {
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
class ShutdownCommittableSource extends KafkaAlpakkaConsumer {
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

