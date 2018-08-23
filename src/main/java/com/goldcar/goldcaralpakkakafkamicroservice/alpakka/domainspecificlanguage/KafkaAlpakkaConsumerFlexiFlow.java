package com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage;

import akka.Done;
import akka.kafka.ProducerMessage;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/*
    KafkaAlpakkaConsumerFlexiFlow allows the stream to continue after publishing messages to Kafka.
    It accepts implementations of ProducerMessage.Envelope (API) as input, which continue in the flow as implementations
    of ProducerMessage.Results (API).

 */
public class KafkaAlpakkaConsumerFlexiFlow extends KafkaAlpakkaSettings {

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

    public void produceMessages(int numMessages, String topic) {
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

    public class PlainSink extends KafkaAlpakkaSettings {
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

    public class PlainSinkWithSettings extends KafkaAlpakkaSettings {
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

    public class ObserveMetrics extends KafkaAlpakkaSettings {
        public void main(String[] args) {
            new PlainSink().demo();
        }

        public void demo() {
            // #producerMetrics
            Map<MetricName, ? extends Metric> metrics =
                    kafkaProducer.metrics();// observe metrics
            // #producerMetrics
        }
    }

}
