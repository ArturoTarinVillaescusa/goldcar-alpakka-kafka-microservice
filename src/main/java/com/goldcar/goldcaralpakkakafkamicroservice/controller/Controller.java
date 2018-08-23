package com.goldcar.goldcaralpakkakafkamicroservice.controller;

// #use
import akka.NotUsed;
import akka.stream.javadsl.Source;
import akka.stream.scaladsl.Sink;
import com.goldcar.goldcaralpakkakafkamicroservice.alpakka.domainspecificlanguage.KafkaAlpakkaFlexiFlow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scala.collection.immutable.Seq;
import scala.concurrent.Future;

@RestController
public class Controller {

    @RequestMapping("/goldcar-alpakka-producer-microservice/{topico}/{nummensajes}")
    public Source<String, NotUsed> flexiFlowProducer(@PathVariable String topico,
                                         @PathVariable Integer nummensajes) {
        new KafkaAlpakkaFlexiFlow().produceMessagesAsAFlow(nummensajes, topico);
        return Source.repeat("Hello world!").intersperse("\n").take(10);
    }

    @RequestMapping("/goldcar-alpakka-consumer-microservice/{topicoorigen}/{topicodestino}")
    public Sink<String, Future<Seq<String>>> flexiFlowConsumer(@PathVariable String topicoorigen,
                                                    @PathVariable String topicodestino) {
        new KafkaAlpakkaFlexiFlow()
                .transferMessagesFromTopicAtoTopicB(topicoorigen, topicodestino);
        return Sink.seq();
    }

//    @ResponseBody
//    @RequestMapping(value="/metrics", produces="text/plain")
//    public String metrics() {
//        int totalMessages = new KafkaAlpakkaConsumerToProducerFlexiFlow()
//                .getKafkaMetrics(topico);
//        return "# HELP messages Number of messages in the queueService\n"
//                + "# TYPE messages gauge\n"
//                + "messages " + totalMessages;
//    }

    // # Still not ready, it is failing in Kubernetes with the message and need further studying
    // # Liveness probe failed: Get http://172.17.0.9:8080/health: dial tcp 172.17.0.9:8080: getsockopt: connection refused
    @RequestMapping(value="/health")
    public ResponseEntity health() {
        HttpStatus status;
        if (new KafkaAlpakkaFlexiFlow()
                .kafkaIsHealthy()) {
            status = HttpStatus.OK;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return new ResponseEntity<>(status);
    }

}
// #use