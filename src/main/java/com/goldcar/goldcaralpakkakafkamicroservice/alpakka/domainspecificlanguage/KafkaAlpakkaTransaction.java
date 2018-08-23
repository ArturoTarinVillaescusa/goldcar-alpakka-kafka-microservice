package com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage;

import akka.NotUsed;
import akka.kafka.ConsumerMessage;
import akka.kafka.ProducerMessage;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Transactional;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/*
Transactions

Kafka Transactions provide guarantees that messages processed in a consume-transform-produce workflow
(consumed from a source topic, transformed, and produced to a destination topic)
are processed exactly once or not at all.

This is achieved through coordination between the Kafka consumer group coordinator, transaction coordinator,
and the consumer and producer clients used in the user application.

The Kafka producer marks messages that are consumed from the source topic as “committed” only once the
transformed messages are successfully produced to the sink.

For full details on how transactions are achieved in Kafka you may wish to review the
Kafka Improvement Proposal KIP-98: Exactly Once Delivery and Transactional Messaging

https://cwiki.apache.org/confluence/display/KAFKA/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging

and its associated design document.

https://docs.google.com/document/d/11Jqy_GjUGtdXJK94XGsEIK7CP1SnQGdp2eF0wSw9ra8/edit#heading=h.xq0ee1vnpz4o

SO IN ESSENCE: Use this class if you want delivery guarantees. It will guarantee the "all-or-nothing" and the
the "exactly-once" message delivery, and will give you the ability to run transactions along several microservices that
are comunicating between them through Kafka topics.
 */
public class KafkaAlpakkaTransaction extends KafkaAlpakka {


  /*

  transactionalSink: implements the Consume-Transform-Produce Workflow that we mentioned in the header

  Kafka transactions are handled transparently to the user.

  The Transactional.source will enforce that a consumer group id is specified and the Transactional.flow
  or Transactional.sink will enforce that a transactional.id is specified.

  All other Kafka consumer and producer properties required to enable transactions are overridden.

  Transactions are committed on an interval which can be controlled with the producer config akka.kafka.producer.eos-commit-interval,
  similar to how "exactly once" works with Kafka Streams. The default value is 100ms.

  The larger commit interval is, the more records will need to be reprocessed in the event
  of a failure and it's corresponding transaction abortion.

  When the stream is materialized the producer will initialize the transaction for the provided transactional.id
  and a transaction will begin.

  We check if there are any offsets available to commit in every commit interval (eos-commit-interval) .

  If offsets exist then we suspend backpressured demand while we drain all outstanding messages
  that have not yet been successfully acknowledged (if any), and then commit the transaction.

  After the commit succeeds a new transaction is begun and we re-initialize demand for upstream messages.

  To gracefully shutdown the stream and commit the current transaction you must call shutdown() on the Control
  (https://doc.akka.io/api/akka-stream-kafka/0.22/akka/kafka/javadsl/Consumer$$Control.html)
  materialized value to await all produced message acknowledgements and commit the final transaction.

   */
  public void transactionalSink(String sourceTopic, String sinkTopic, String transactionalId) {
    Consumer.Control control =
      Transactional
          .source(consumerSettings, Subscriptions.topics(sourceTopic))
          .via(business())
          .map(msg ->
              new ProducerMessage.Message<String, byte[], ConsumerMessage.PartitionOffset>(
                  new ProducerRecord<>(sinkTopic, msg.record().value()), msg.partitionOffset()))
          .to(Transactional.sink(consumerToProducerSettings, transactionalId))
          .run(materializer);

    // ...

    control.shutdown();
  }

  /*

    transactionalFailureRetry: implements transactional recovery from a failure

    When any stage in the stream fails the whole stream will be torn down.

    In the general case it’s desirable to allow transient errors to fail the whole stream
    because they cannot be recovered from within the application.

    Transient errors can be caused by network partitions, Kafka broker failures,
    ProducerFencedException’s from other application instances, and so on.

    When the stream encounters transient errors then the current transaction will be aborted before
    the stream is torn down.

    Any produced messages that were not committed will not be available to downstream consumers
    as long as those consumers are configured with

    isolation.level = read_committed.

    For transient errors we can choose to rely on the Kafka producer’s configuration to retry,
    or we can handle it ourselves at the Akka Streams or Application layer.

    Using the RestartSource (https://doc.akka.io/docs/akka/2.5.13/stream/stream-error.html#delayed-restarts-with-a-backoff-stage)
    we can backoff connection attempts so that we don’t hammer the Kafka cluster in a tight loop.

   */
  public void transactionalFailureRetry(String sourceTopic, String sinkTopic, String transactionalId) {
    AtomicReference<Consumer.Control> innerControl = null;

    Source<ProducerMessage.Results<String, byte[], ConsumerMessage.PartitionOffset>,NotUsed> stream =
      RestartSource.onFailuresWithBackoff(
        java.time.Duration.of(3, ChronoUnit.SECONDS), // min backoff
        java.time.Duration.of(30, ChronoUnit.SECONDS), // max backoff
        0.2, // adds 20% "noise" to vary the intervals slightly
        () -> Transactional.source(consumerSettings, Subscriptions.topics(sourceTopic))
          .via(business())
          .map(msg ->
            new ProducerMessage.Message<String, byte[], ConsumerMessage.PartitionOffset>(
              new ProducerRecord<>(sinkTopic, msg.record().value()), msg.partitionOffset()))
              // side effect out the `Control` materialized value because it can't be propagated through the `RestartSource`
              .mapMaterializedValue(control -> {
                innerControl.set(control);
                return control;
              })
              .via(Transactional.flow(consumerToProducerSettings, transactionalId)));

      stream.runWith(Sink.ignore(), materializer);

      // Add shutdown hook to respond to SIGTERM and gracefully shutdown stream
      Runtime.getRuntime().addShutdownHook(new Thread(() -> innerControl.get().shutdown()));
    }

}
