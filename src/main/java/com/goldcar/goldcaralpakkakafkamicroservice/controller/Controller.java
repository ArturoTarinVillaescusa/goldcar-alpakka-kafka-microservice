package com.goldcar.goldcaralpakkakafkamicroservice.controller;

// #use
import akka.NotUsed;
import akka.stream.javadsl.Source;
import akka.stream.scaladsl.Sink;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scala.collection.immutable.Seq;
import scala.concurrent.Future;

@RestController
public class Controller {

    @RequestMapping("/goldcar-alpakka-producer-microservice/{topico}/{nummensajes}")
    public Source<String, NotUsed> index(@PathVariable String topico,
                                         @PathVariable Integer nummensajes) {
        new KafkaAlpakkaProducerFlexiFlow().produceMessages(nummensajes, topico);
        return Source.repeat("Hello world!").intersperse("\n").take(10);
    }

    @RequestMapping("/goldcar-alpakka-consumer-microservice/{topicoorigen}/{topicodestino}")
    public Sink<String, Future<Seq<String>>> index1(@PathVariable String topicoorigen,
                                                    @PathVariable String topicodestino) {
        new KafkaAlpakkaConsumerToProducerFlexiFlow()
                .consumeMessages(topicoorigen, topicodestino);
        return Sink.seq();
    }
}
// #use