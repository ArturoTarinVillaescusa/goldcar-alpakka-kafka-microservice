package com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage;

import akka.Done;
import akka.kafka.ConsumerMessage;
import akka.kafka.ProducerMessage;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class KafkaAlpakkaFlexiFlow extends KafkaAlpakka {

    /*
    produceMessagesAsAFlow produces `numMessages` messages to the topic `topic` and allows the stream to continue
    after publishing messages to Kafka.

    It accepts implementations of ProducerMessage.Envelope (API) as input, which continue in the flow as implementations
    of ProducerMessage.Results (API).
    */
    public void produceMessagesAsAFlow(int numMessages, String topic) {
        // #flow
        CompletionStage<Done> done =
                Source.range(1, numMessages)
                        .map(number -> {
                            int partition = 0;
                            String value = String.valueOf(number);
                            ProducerMessage.Envelope<String, String, Integer> msg =
                                    new ProducerMessage.Message<String, String, Integer>(
                                            new ProducerRecord<>(topic, partition, "key", value),
                                            number
                                    );
                            return msg;
                        })
                        // allows the stream to continue after publishing messages to Kafka
                        // It accepts implementations of `ProducerMessage.Envelope` (@scaladoc[API](akka.kafka.ProducerMessage$$Envelope))
                        //  as input, which continue in the flow as implementations of `ProducerMessage.Results`
                        //  (@scaladoc[API](akka.kafka.ProducerMessage$$Results)).
                        .via(akka.kafka.javadsl.Producer.flexiFlow(producerSettings))

                        .map(result -> {
                            if (result instanceof ProducerMessage.Result) {
                                ProducerMessage.Result<String, String, Integer> res = (ProducerMessage.Result<String, String, Integer>) result;
                                ProducerRecord<String, String> record = res.message().record();
                                RecordMetadata meta = res.metadata();
                                return meta.topic() + "/" + meta.partition() + " " + res.offset() + ": " + record.value();
                            } else if (result instanceof ProducerMessage.MultiResult) {
                                ProducerMessage.MultiResult<String, String, Integer> res = (ProducerMessage.MultiResult<String, String, Integer>) result;
                                return res.getParts() .stream().map( part -> {
                                    RecordMetadata meta = part.metadata();
                                    return meta.topic() + "/" + meta.partition() + " " + part.metadata().offset() + ": " + part.record().value();
                                }).reduce((acc, s) -> acc + ", " + s);
                            } else {
                                return "passed through";
                            }
                        })
                        .runWith(Sink.foreach(System.out::println), materializer);
        // #flow

        terminateWhenDone(done);
    }


    /*
    transferMessagesFromTopicAtoTopicB

    This method is useful for cases when you need to read messages from one topic, transform or enrich them,
    and then write to another topic

    This method creates a Consumer.committableSource linked to the `topicoDelQueConsumimos` topic and then connects it
    to a Producer.commitableSink, linked to the `topicoAlQueProducimos` topic.

    The commitableSink will commit the offset back to the consumer when it has successfully published the message in the
    `topicoAlQueProducimos` topic.

    The committableSink accepts implementations ProducerMessage.Envelope (@scaladocAPI) that contain the offset to commit
    the consumption of the originating message (of type ConsumerMessage.Committable (@scaladocAPI)).

    Note that there is a risk that something fails after publishing but before committing, so commitableSink has
    "at-least-once" delivery semantics.

    So if you want to get delivery guarantees, it is better if you use the KafkaAlpakkaTransaction.java class, provided
    with this PoC. This will guarantee the "all-or-nothing", the "exactly-once" giving you the ability to spread
    transactions between multiple microservices comunicating through Kafka topics.

     */
    public void transferMessagesFromTopicAtoTopicB(String topicoDelQueConsumimos, String topicoAlQueProducimos) {
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

                        .via(Producer.flexiFlow(consumerToProducerSettings))

                        .mapAsync(consumerToProducerSettings.parallelism(), result -> {
                            ConsumerMessage.Committable committable = result.passThrough();
                            return committable.commitJavadsl();
                        })
                        .toMat(Sink.ignore(), Keep.both())
                        .mapMaterializedValue(akka.kafka.javadsl.Consumer::createDrainingControl)
                        .run(materializer);
        // #consumerToProducerFlow
    }

    <KeyType, ValueType, PassThroughType> ProducerMessage.Message<KeyType, ValueType, PassThroughType>
    createMessage(KeyType key, ValueType value, PassThroughType passThrough) {
        return
                // #singleMessage
                new ProducerMessage.Message<KeyType, ValueType, PassThroughType>(
                        new ProducerRecord<>("topicName", key, value),
                        passThrough
                );
        // #singleMessage

    }

    <KeyType, ValueType, PassThroughType> ProducerMessage.MultiMessage<KeyType, ValueType, PassThroughType> createMultiMessage(KeyType key, ValueType value, PassThroughType passThrough) {
        return
                // #multiMessage
                new ProducerMessage.MultiMessage<KeyType, ValueType, PassThroughType>(
                        Arrays.asList(
                                new ProducerRecord<>("topicName", key, value),
                                new ProducerRecord<>("anotherTopic", key, value)
                        ),
                        passThrough
                );
        // #multiMessage

    }

    <KeyType, ValueType, PassThroughType> ProducerMessage.PassThroughMessage<KeyType, ValueType, PassThroughType> createPassThroughMessage(KeyType key, ValueType value, PassThroughType passThrough) {

        ProducerMessage.PassThroughMessage<KeyType, ValueType, PassThroughType> ptm =
                // #passThroughMessage
                new ProducerMessage.PassThroughMessage<>(
                        passThrough
                );
        // #passThroughMessage
        return ptm;
    }


    public class PlainSink extends KafkaAlpakka {
        public void main(String[] args) {
            new PlainSink().demo();
        }

        public void demo() {
            // #plainSink
            CompletionStage<Done> done =
                    Source.range(1, 100)
                            .map(number -> number.toString())
                            .map(value -> new ProducerRecord<String, String>("topic1", value))
                            .runWith(Producer.plainSink(producerSettings), materializer);
            // #plainSink

            terminateWhenDone(done);
        }
    }

    public class PlainSinkWith extends KafkaAlpakka {
        public void main(String[] args) {
            new PlainSink().demo();
        }

        public void demo() {
            // #plainSinkWithProducer
            CompletionStage<Done> done =
                    Source.range(1, 100)
                            .map(number -> number.toString())
                            .map(value -> new ProducerRecord<String, String>("topic1", value))
                            .runWith(Producer.plainSink(producerSettings, kafkaProducer), materializer);
            // #plainSinkWithProducer

            terminateWhenDone(done);
        }
    }

    public class ObserveMetrics extends KafkaAlpakka {
        public void getProducerMetrics() {
            // #producerMetrics
            Map<MetricName, ? extends Metric> metrics =
                    kafkaProducer.metrics();// observe metrics
            // #producerMetrics
        }
    }

}
