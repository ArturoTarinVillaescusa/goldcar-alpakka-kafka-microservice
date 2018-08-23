package com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import com.typesafe.config.Config;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.concurrent.CompletionStage;

public abstract class KafkaAlpakkaSettings {
    protected final ActorSystem system = ActorSystem.create("goldcarmicroservices");

    protected final Materializer materializer = ActorMaterializer.create(system);

    // HARDCODED TO THE KUBERNETES KAFKA STANDALONE DEPLOYMENT!!!
    // NEEDS TO BE CONFIGURABLE.
    private String bootstrapServer = "localhost:9092";
    // private String bootstrapServer = "kafka-0.kafka-hs.default.svc.cluster.local:9093";
    private String consumerGroupId = "group1";

    // PRODUCER SETTINGS
    final Config producerConfig = system.settings().config().getConfig("akka.kafka.producer");
    protected final ProducerSettings<String, String> producerSettings =
            ProducerSettings.create(system, new StringSerializer(), new StringSerializer())
                    .withBootstrapServers(bootstrapServer);

    final KafkaProducer<String, String> kafkaProducer =
            producerSettings.createKafkaProducer();
    // END PRODUCER SETTINGS

    // CONSUMER SETTINGS
    protected final int maxPartitions = 100;

    protected <T> Flow<T, T, NotUsed> business() {
        return Flow.create();
    }

    final Config consumerConfig = system.settings().config().getConfig("akka.kafka.consumer");
    public final ConsumerSettings<String, byte[]> consumerSettings =
            ConsumerSettings.create(consumerConfig, new StringDeserializer(), new ByteArrayDeserializer())
                    .withBootstrapServers(bootstrapServer)
                    .withGroupId(consumerGroupId)
                    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    final ConsumerSettings<String, byte[]> consumerSettingsWithAutoCommit =
            consumerSettings
                    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
                    .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000");

    protected final ProducerSettings<String, byte[]> consumerToProducerSettings =
            ProducerSettings.create(system, new StringSerializer(), new ByteArraySerializer())
                    .withBootstrapServers(bootstrapServer);
    // END CONSUMER SETTINGS


    protected void terminateWhenDone(CompletionStage<Done> result) {
        result
                .exceptionally(e -> {
                    system.log().error(e, e.getMessage());
                    return Done.getInstance();
                })
                .thenAccept(d -> system.terminate());
    }

}

