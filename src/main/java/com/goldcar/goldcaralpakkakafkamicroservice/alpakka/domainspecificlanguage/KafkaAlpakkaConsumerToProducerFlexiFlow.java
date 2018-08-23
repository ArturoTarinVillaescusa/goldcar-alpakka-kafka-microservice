package com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage;

import akka.Done;
import akka.kafka.ConsumerMessage;
import akka.kafka.ProducerMessage;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

// Connect a KafkaAlpakkaConsumer to KafkaAlpakkaProducer
public class KafkaAlpakkaConsumerToProducerFlexiFlow extends KafkaAlpakkaSettings {
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

  public boolean kafkaIsUp() {
      try {
          Properties props = new Properties();
          props.put("bootstrap.servers", consumerSettings.getProperty("bootstrap.servers")); // "kafka-0.kafka-hs.default.svc.cluster.local:9093"
          props.put("group.id",consumerSettings.getProperty("group.id")); // "group1"
          props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
          props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
          KafkaConsumer simpleConsumer = new KafkaConsumer(props);
          simpleConsumer.listTopics();
      } catch (Exception e) {
        return false;
      }


      return true;
  }
}
