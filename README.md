# Autoscaling Spring Boot Alpakka Kafka microservices with the Horizontal Pod Autoscaler and custom metrics on Kubernetes

Alpakka is Lightbend's way of solving the Reactive Enterprise Integration problem.
They do so by providing a collection of streaming plug-and-play connectors to various technologies.

In this PoC we'd like to test one specific integration, the Kafka Reactive Streams connector. This will be included in
a Spring Boot application with two rest endpoints, one to produce N messages in a topic, and one to consume messages from a
source topic and replicating them to a destination topic.

We will also add rest endpoints for these Consumer functionalities:

    • ConsumerWithPerPartitionBackpressure
    • RebalanceListenerCallbacks
    • ConsumerAtLeastOnce
    • ConsumerAtMostOnce
    • [github/akka/alpakka/doc-examples/src/main/java/csvsamples/](https://github.com/akka/alpakka/blob/e8ac367cad298c9e866db09db96938f1638006ad/doc-examples/src/main/java/csvsamples/FetchHttpEvery30SecondsAndConvertCsvToJsonToKafkaInJava.java)

And we will implement also rest endpoints for these Transactions functionalitites:

    • TransactionsSink
    • TransactionsFailureRetry


We will also modify these microservices so that they authenticate to Kafka using *Kerberos authentication and authorization*

The resulting application will be bundled in a Docker container image that will be deployed as a Kubernetes application.
This application will be an example of Reactive microservices, communicating between them by subscribing to a Kafka topic,
producing and consuming messages in a backpressure fashion. Microservices metrics will be collected in Grafana, and we will
launch a workload to test the Kubernetes HPA autoscale feature.


## TESTING THE ALPAKKA KAFKA CONNECTOR


See the [Alpakka kafka connector documentation](https://doc.akka.io/docs/akka-stream-kafka/current/home.html)

Clone the project locally:

```bash
tarin.mistralbs@PORTATIL-192~/Documents/2018-08-13 OTD-129. PoC Reactive Microservices communication through Kafka topics using Alpakka
  $ git clone https://github.com/akka/alpakka-kafka.git
Cloning into 'alpakka-kafka'...
remote: Counting objects: 9105, done.
remote: Compressing objects: 100% (108/108), done.
remote: Total 9105 (delta 43), reused 189 (delta 39), pack-reused 8887
Receiving objects: 100% (9105/9105), 1.50 MiB | 2.13 MiB/s, done.
Resolving deltas: 100% (3510/3510), done.
```

Run Kafka locally:

```bash
arturotarin@QOSMIO-X70B:~
 $ ~/Downloads/kafka_2.12-2.0.0/bin/zookeeper-server-start.sh ~/Downloads/kafka_2.12-2.0.0/config/zookeeper.properties  > /dev/null 2>&1 &
arturotarin@QOSMIO-X70B:~
 $ ~/Downloads/kafka_2.12-2.0.0/bin/kafka-server-start.sh ~/Downloads/kafka_2.12-2.0.0/config/server.properties  > /dev/null 2>&1 &
```

Run the project to check the Alpakka kafka connector included in the downloaded Alpakka repository:

![Alt text](images/checkalpakkalocally.png "Check alpakka locally")


## INTEGRATING THE ALPAKKA CONNECTOR IN A SPRING BOOT APPLICATION

For this demo we are going to take advantage of the new integration that has been landed in [Alpakka](https://github.com/akka/akka.github.com/blob/master/blog/_posts/2017-10-23-native-akka-streams-in-spring-web-and-boot-via-alpakka.md):
it can be integrated in Spring web and Spring boot applications.

This feature allows us to seamlessly integrate with our akka.stream.javadsl or
akka.stream.scaladsl.Source types in our Spring Web applications, and have them be understood by Spring natively, thanks to
the fact that Akka Streams is one of the first libraries to provide native support for Java 9's
java.util.concurrent.Flow.* (as of writing, Spring does not provide this support yet, while RxJava provides bridges separately packaged),
so if we're looking for a future-ready Reactive Streams implementation, it's as simple as using Akka Streams 2.5.6+ and we're ready to go.

Let's go!

### Create a Spring Boot project using Spring Boot Initializr

![Alt text](images/springbootinitializr.png "Create an empty Spring Boot application")

Open the application with IntelliJ, add a rest endpoint in the application controller
in order to include our Reactive Alpakka microservice and run it:

![Alt text](images/springbootintellij.png "Add a rest endpoint")


And execute the application

![Alt text](images/springbootrunning.png "Run the application")


### Add parameterizered Producer and Consumer endpoints to the Spring Boot application

Our testing approach will be to have the Producer and Consumer microservices rest endpoints parameterized,
so that we can easily link calls between microservices that are transfering data between them, chaining their
calls in a network of communications through the Alpakka reactive connectors that they're using in their
implementations.

![Alt text](images/microservicescommunication.png "Our microservices communication approach")

To do this we modify the Spring Boot controller adding these two endpoints:

![Alt text](images/parameterizedendpoints.png "Microservices parameterized endpoints")


### Example 1: Testing the communication between 1 Producer and 1 Consumer

1) Call the Producer microservice endpoint in order to create 3 messages in a Kafka topic called "topicoejemplo":

```bash

    http://localhost:8080/goldcar-alpakka-producer-microservice/topicoejemplo/3
```

The execution will create the messages as seen below:

![Alt text](images/producermicroservice.png "Producer microservice 1")

![Alt text](images/producermicroservice1.png "Producer microservice 1")

2) Call the Consumer microservice endpoint in order to consume the messages inserted in the "topicoejemplo"
and replicate them into the "topicodestino" a more sophisticated microservice would deal with the consumed messages
to make some transactions, persist them in a database or make some calculations and display the results in the screen,
but this is enougth for our testing purposes. This would be the endpoint that we should call to do so:

```bash
http://localhost:8080/goldcar-alpakka-consumer-microservice/topicoejemplo/topicodestino
```

The execution will replicate the messages from "topicoejemplo" to "topicodestino" as seen below:

![Alt text](images/consumermicroservice.png "Consumer microservice")

Now, any other microservice can consume the messages produced into "topicodestino".

### Example 2: Testing the communication between 1 Producer and a chain of Consumers

![Alt text](images/chainedconsumers.png "1 producer and multiple consumers")

### Examples conclusion

This would be the initial approach of our implementation of microservices, enabling a workflow communication
using Reactive microservices based on Alpakka conenctors.


## PACKAGING OUR MICROSERVICE APPLICATION IN A CONTAINER IMAGE, DEPLOYING IT IN KUBERNETES, COLLECTING MICROSERVICE METRICS IN GRAFANA AND VERIFYING THAT HPA AUTOSCALING WORKS

### Prerequisites

***Minikube***

You should have minikube installed.

You should start minikube with at least 4GB of RAM:

```bash
minikube start \
  --memory 8192 \
  --extra-config=controller-manager.horizontal-pod-autoscaler-upscale-delay=1m \
  --extra-config=controller-manager.horizontal-pod-autoscaler-downscale-delay=2m \
  --extra-config=controller-manager.horizontal-pod-autoscaler-sync-period=10s
Starting local Kubernetes v1.10.0 cluster...
Starting VM...
Getting VM IP address...
Moving files into cluster...
Setting up certs...
Connecting to cluster...
Setting up kubeconfig...
Starting cluster components...
Kubectl is now configured to use the cluster.
Loading cached images from config file.
```

![Alt text](images/startminikube.png "Start Minikube")


![Alt text](images/startminikube1.png "Minikube in VirtualBox")

> If you're using a pre-existing minikube instance, you can resize the VM by destroying it an recreating it. Just adding the `--memory 4096` won't have any effect.

You should install `jq` — a lightweight and flexible command-line JSON processor.

You can find more [info about `jq` on the official website](https://github.com/stedolan/jq).


[There are other Kubernetes test environment alternatives](https://kubernetes.io/docs/tasks/run-application/run-stateless-application-deployment/)

![Alt text](images/kubernetesenvironments.png "Other Kubernetes environments")

***Kataconda***

https://www.katacoda.com

![Alt text](images/kataconda.png "Kataconda")


***Play with Kubernetes***

https://labs.play-with-k8s.com/

![Alt text](images/playwithkubernetes.png "Play with Kubernetes")

```bash
1. Initialize cluster master node:

kubeadm init --apiserver-advertise-address
```

![Alt text](images/playwithkubernetes1.png "Play with Kubernetes 1")

```bash
2. Initialize cluster networking:

kubectl apply -n kube-system -f \
"https://cloud.weave.works/k8s/net?k8s-version=$(kubectl version | base64 | tr -d '\n')"

3. Create an nginx deployment:

kubectl apply -f https://raw.githubusercontent.com/kubernetes/website/master/content/cn/docs/user-guide/nginx-app.yaml
```


***Docker EE trial***

Alternatively, you can use the Docker EE trial link:


![Alt text](images/dockereetrial.png "Docker EE trial")


![Alt text](images/deploytomcatineetrial.png "How to deploy Tomcat in Docker EE Kubernetes")

![Alt text](images/deploytomcatineetrial1.png "How to deploy Tomcat in Docker EE Kubernetes 1")


![Alt text](images/deploytomcatineetrial2.png "How to deploy Tomcat in Docker EE Kubernetes 2")

![Alt text](images/deploytomcatineetrial3.png "How to deploy Tomcat in Docker EE Kubernetes 3")

![Alt text](images/deploytomcatineetrial4.png "How to deploy Tomcat in Docker EE Kubernetes 4")

![Alt text](images/deploytomcatineetrial5.png "How to deploy Tomcat in Docker EE Kubernetes 5")

![Alt text](images/deploytomcatineetrial6.png "How to deploy Tomcat in Docker EE Kubernetes 6")

![Alt text](images/deploytomcatineetrial7.png "How to deploy Tomcat in Docker EE Kubernetes 7")

![Alt text](images/deploytomcatineetrial8.png "How to deploy Tomcat in Docker EE Kubernetes 8")

![Alt text](images/deploytomcatineetrial9.png "How to deploy Tomcat in Docker EE Kubernetes 9")

![Alt text](images/deploytomcatineetrial10.png "How to deploy Tomcat in Docker EE Kubernetes 10")

![Alt text](images/deploytomcatineetrial11.png "How to deploy Tomcat in Docker EE Kubernetes 11")

![Alt text](images/deploytomcatineetrial12.png "How to deploy Tomcat in Docker EE Kubernetes 12")

![Alt text](images/deploytomcatineetrial13.png "How to deploy Tomcat in Docker EE Kubernetes 13")

![Alt text](images/deploytomcatineetrial14.png "How to deploy Tomcat in Docker EE Kubernetes 14")

![Alt text](images/deploytomcatineetrial15.png "How to deploy Tomcat in Docker EE Kubernetes 15")

![Alt text](images/deploytomcatineetrial16.png "How to deploy Tomcat in Docker EE Kubernetes 16")

[NOTE]
---
If you want to deploy this in Docker EE you will find that Docker EE has its own RBAC system,
so it’s not possible to create ClusterRole objects, ClusterRoleBinding objects, or any other
object that is created by using the /apis/rbac.authorization.k8s.io endpoints.
---

![Alt text](images/deploymonitoring.png "Deploy monitoring metrics-server in Docker EE")

![Alt text](images/dockereekuberneteslimitations.png "Docker EE Kubernetes rbac.authorization.k8s.io limitation")


### Installing Custom Metrics Api


Deploy the Metrics Server in the `kube-system` namespace:

```bash
$ kubectl create -f monitoring/metrics-server

clusterrolebinding "metrics-server:system:auth-delegator" created
rolebinding "metrics-server-auth-reader" created
apiservice "v1beta1.metrics.k8s.io" created
serviceaccount "metrics-server" created
deployment "metrics-server" created
service "metrics-server" created
clusterrole "system:metrics-server" created
clusterrolebinding "system:metrics-server" created
```

![Alt text](images/metrics-server.png "Create metrics server")

After one minute the metric-server starts reporting CPU and memory usage for nodes and pods.

View nodes metrics:

```bash
$ kubectl get --raw "/apis/metrics.k8s.io/v1beta1/nodes" | jq .
```


![Alt text](images/nodemetrics.png "Node metrics")


View pods metrics:

```bash
$ kubectl get --raw "/apis/metrics.k8s.io/v1beta1/pods" | jq .
```

![Alt text](images/podsmetrics.png "Pods metrics")


Create the monitoring namespace:

```bash
$ kubectl create -f monitoring/namespaces.yaml

namespace "monitoring" created

```

Deploy Prometheus v2 in the monitoring namespace:

```bash
$ kubectl create -f monitoring/prometheus

configmap "prometheus-config" created
deployment "prometheus" created
clusterrole "prometheus" created
serviceaccount "prometheus" created
clusterrolebinding "prometheus" created
service "prometheus" created

```

Deploy the Prometheus custom metrics API adapter:

```bash
$ kubectl create -f monitoring/custom-metrics-api

secret "cm-adapter-serving-certs" created
clusterrolebinding "custom-metrics:system:auth-delegator" created
rolebinding "custom-metrics-auth-reader" created
deployment "custom-metrics-apiserver" created
clusterrolebinding "custom-metrics-resource-reader" created
serviceaccount "custom-metrics-apiserver" created
service "custom-metrics-apiserver" created
apiservice "v1beta1.custom.metrics.k8s.io" created
clusterrole "custom-metrics-server-resources" created
clusterrole "custom-metrics-resource-reader" created
clusterrolebinding "hpa-controller-custom-metrics" created

```

![Alt text](images/monitoring.png "Create monitoring components")


List the custom metrics provided by Prometheus:

```bash
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1" | jq .
```

![Alt text](images/prometheuscustommetrics.png "Prometheus custom metrics")


Get the FS usage for all the pods in the `monitoring` namespace:

```bash
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1/namespaces/monitoring/pods/*/fs_usage_bytes" | jq .
```


![Alt text](images/filesystemusagemetrics.png "Filesystem usage metrics")


### Package the application

You package the application as a container with:

```bash
eval $(minikube docker-env)
docker build -t goldcar-alpakka-kafka-microservice .
```

![Alt text](images/dockerbuild.png "docker build")


![Alt text](images/dockerimages.png "docker images")

### Deploying a Tomcat application to make sure that everything will work

In order to check that Kubernetes deployments are working, before deploying our microservices,
let's make a quick test with a Tomcat deployment:

```bash
arturotarin@QOSMIO-X70B:~/Documents/Mistral/2018-08-13 OTD-129. PoC Reactive Microservices communication through Kafka topics using Alpakka/goldcar-alpakka-kafka-microservice
arturotarin@QOSMIO-X70B:~/Documents/Mistral/2018-08-13 OTD-129. PoC Reactive Microservices communication through Kafka topics using Alpakka/goldcar-alpakka-kafka-microservice

$ cat kube/tomcat.yaml
apiVersion: apps/v1beta2
kind: Deployment
metadata:
  name: tomcat
spec:
  selector:
    matchLabels:
      app: tomcat
  replicas: 2
  template:
    metadata:
      labels:
        app: tomcat
    spec:
      containers:
      - name: tomcat
        image: tomcat:9.0
        ports:
        - containerPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: tomcat-service
spec:
  type: NodePort
  selector:
    app: tomcat
  ports:
  - protocol: TCP
    port: 8080
    nodePort: 31000

arturotarin@QOSMIO-X70B:~/Documents/Mistral/2018-08-13 OTD-129. PoC Reactive Microservices communication through Kafka topics using Alpakka/goldcar-alpakka-kafka-microservice

$ kubectl create -f kube/tomcat.yaml
deployment "tomcat" created
service "tomcat-service" created

$ kubectl get deployments
NAME      DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
tomcat    2         2         2            2           4m

$ kubectl get pods
NAME                      READY     STATUS    RESTARTS   AGE
tomcat-56ff5c79c5-4g6fb   1/1       Running   0          4m
tomcat-56ff5c79c5-7cfw5   1/1       Running   0          4m

$ kubectl get services
NAME             TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
kubernetes       ClusterIP   10.96.0.1        <none>        443/TCP          38m
tomcat-service   NodePort    10.111.165.247   <none>        8080:31000/TCP   4m

$ kubectl get endpoints
NAME             ENDPOINTS                         AGE
kubernetes       192.168.99.100:8443               38m
tomcat-service   172.17.0.7:8080,172.17.0.8:8080   5m

$ kubectl expose deployment tomcat --type=NodePort
service "tomcat" exposed

$ kubectl get services
NAME             TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
kubernetes       ClusterIP   10.96.0.1        <none>        443/TCP          43m
tomcat           NodePort    10.103.202.180   <none>        8080:31797/TCP   1m
tomcat-service   NodePort    10.111.165.247   <none>        8080:31000/TCP   10m

$ kubectl describe deployment tomcat
Name:                   tomcat
Namespace:              default
CreationTimestamp:      Tue, 21 Aug 2018 08:25:50 +0200
Labels:                 <none>
Annotations:            deployment.kubernetes.io/revision=1
Selector:               app=tomcat
Replicas:               2 desired | 2 updated | 2 total | 2 available | 0 unavailable
StrategyType:           RollingUpdate
MinReadySeconds:        0
RollingUpdateStrategy:  25% max unavailable, 25% max surge
Pod Template:
  Labels:  app=tomcat
  Containers:
   tomcat:
    Image:        tomcat:9.0
    Port:         8080/TCP
    Environment:  <none>
    Mounts:       <none>
  Volumes:        <none>
Conditions:
  Type           Status  Reason
  ----           ------  ------
  Available      True    MinimumReplicasAvailable
  Progressing    True    NewReplicaSetAvailable
OldReplicaSets:  <none>
NewReplicaSet:   tomcat-56ff5c79c5 (2/2 replicas created)
Events:
  Type    Reason             Age   From                   Message
  ----    ------             ----  ----                   -------
  Normal  ScalingReplicaSet  8m    deployment-controller  Scaled up replica set tomcat-56ff5c79c5 to 2

```

Tomcat is accessible using Minikube IP (mine is 192.168.99.100 VirtualBox's IP) and nodePort (31000):

![Alt text](images/minikubetomcat.png "Tomcat deployed in Minikube")

### Deploying Zookeeper and Kafka in Kubernetes

Our microservice is still not using Spring Cloud Config, and the connection to Kafka is hardcoded with "localhost:9092"
In order to make our initial tests work we need to run Kubernetes and Kafka in this environment.

The [implementation described in this link is handy](https://github.com/kow3ns/kubernetes-kafka/tree/master/manifests), so
we are going to take advantage for our project, so thanks in advance to mr. Kenneth Owens for his work!

***Create a standalone Zookeeper application***

```bash
$ kubectl create -f kube/zookeeper.yaml
service "zk-hs" created
service "zk-cs" created
statefulset "zk" created

$ kubectl get po -lapp=zk
NAME      READY     STATUS    RESTARTS   AGE
zk-0      1/1       Running   0          1m
```

![Alt text](images/zookeeper.png "Zookeeper deployed in Minikube")

![Alt text](images/zookeeperlogs.png "Zookeeper logs")

***Create a standalone Kafka application***

```bash
$ kubectl create -f kube/kafka.yaml
service "kafka-hs" created
poddisruptionbudget "kafka-pdb" created
statefulset "kafka" created

$ kubectl get pods -lapp=kafka
NAME      READY     STATUS    RESTARTS   AGE
kafka-0   1/1       Running   0          1m

```

![Alt text](images/kafka.png "Kafka deployed in Minikube")

![Alt text](images/kafkalogs.png "Kafka logs")


***Get Kafka and Zookeeper metrics***

```bash
$ kubectl get --raw "/apis/metrics.k8s.io/v1beta1/namespaces/default/pods/kafka-0" | jq .
{
  "kind": "PodMetrics",
  "apiVersion": "metrics.k8s.io/v1beta1",
  "metadata": {
    "name": "kafka-0",
    "namespace": "default",
    "selfLink": "/apis/metrics.k8s.io/v1beta1/namespaces/default/pods/kafka-0",
    "creationTimestamp": "2018-08-22T06:20:22Z"
  },
  "timestamp": "2018-08-22T06:20:00Z",
  "window": "1m0s",
  "containers": [
    {
      "name": "k8skafka",
      "usage": {
        "cpu": "123m",
        "memory": "504928Ki"
      }
    }
  ]
}

$ kubectl get --raw "/apis/metrics.k8s.io/v1beta1/namespaces/default/pods/zk-0" | jq .
{
  "kind": "PodMetrics",
  "apiVersion": "metrics.k8s.io/v1beta1",
  "metadata": {
    "name": "zk-0",
    "namespace": "default",
    "selfLink": "/apis/metrics.k8s.io/v1beta1/namespaces/default/pods/zk-0",
    "creationTimestamp": "2018-08-22T06:20:15Z"
  },
  "timestamp": "2018-08-22T06:20:00Z",
  "window": "1m0s",
  "containers": [
    {
      "name": "kubernetes-zookeeper",
      "usage": {
        "cpu": "3m",
        "memory": "62968Ki"
      }
    }
  ]
}

```

***Test the deployed Kafka application***

Create a topic called "test":

```bash

$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
    createtopic --restart=Never --rm -- \
    kafka-topics.sh --create --topic test \
    --zookeeper zk-cs.default.svc.cluster.local:2181 \
    --partitions 1 \
    --replication-factor 1

Created topic "test".

```

Run kafka-console-consumer in one terminal:


```bash
$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
    consume --restart=Never --rm -- \
    kafka-console-consumer.sh --topic test \
    --bootstrap-server kafka-0.kafka-hs.default.svc.cluster.local:9093

```

Run kafka-console-producer in other terminal:

```bash
$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
    produce --restart=Never --rm -- \
    kafka-console-producer.sh --topic test \
    --broker-list kafka-0.kafka-hs.default.svc.cluster.local:9093

a
a
un mensaje
otro mensaje
```

We see in kafka-console-consumer terminal that it starts consuming the produced messages:


```bash
$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
    consume --restart=Never --rm -- \
    kafka-console-consumer.sh --topic test \
    --bootstrap-server kafka-0.kafka-hs.default.svc.cluster.local:9093

a
a
un mensaje
otro mensaje

```

![Alt text](images/testingkafka.png "Testing Kafka")


So the test is ok, we have Kafka up and running in Kubernetes!

### Deploying the microservice application

Deploy the application in Kubernetes with:

```bash
$ cat kube/goldcarmicroservice.yaml
---
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: goldcar-alpakka-kafka-microservice
spec:
  replicas: 3
  template:
    metadata:
      labels:
        app: goldcar-alpakka-kafka-microservice
    spec:
      containers:
      - name: goldcar-alpakka-kafka-microservice
        image: goldcar-alpakka-kafka-microservice
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: goldcar-alpakka-kafka-microservice-service
spec:
  type: NodePort
  selector:
    app: goldcar-alpakka-kafka-microservice
  ports:
  - protocol: TCP
    port: 8080
    nodePort: 32000

$ kubectl create -f kube/goldcarmicroservice.yaml
deployment "goldcar-alpakka-kafka-microservice" created
service "goldcar-alpakka-kafka-microservice-service" created

```

![Alt text](images/deployinkubernetes.png "Deploy the appication")


![Alt text](images/checkkubernetes.png "Check the status of the deployed appication")

![Alt text](images/goldcar-alpakka-kafka-microservice-deployed.png "Application deployed")

![Alt text](images/goldcar-alpakka-kafka-microservice-logs.png "Application logs")


You can visit the application at http://your-minkube-ip:32000

![Alt text](images/verifydeployedapplicationinkube.png "Check the appication")


The Producer microservice can be tested called like in this example:


```bash
curl http://192.168.99.100:32000/goldcar-alpakka-producer-microservice/test/30
{}
```

We can see from our kafka-console-consumer.sh window that the messages
have been added to the topic:

```bash
arturotarin@QOSMIO-X70B:~/Documents/Mistral/2018-08-13 OTD-129. PoC Reactive Microservices communication through Kafka topics using Alpakka/goldcar-alpakka-kafka-microservice
$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
     consume --restart=Never --rm -- \
     kafka-console-consumer.sh --topic test \
     --bootstrap-server kafka-0.kafka-hs.default.svc.cluster.local:9093 --from-beginning
                                                      a
a
un mensaje
otro mensaje
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
27
28
29
30
```

The application logs also is registering the log production:

![Alt text](images/kafkatestlogs.png "Check Kafka logs")

We also can test the Consumer microservice, calling it like in this example:


First, create a new topic called "test1":

```bash

$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
    createtopic --restart=Never --rm -- \
    kafka-topics.sh --create --topic test1 \
    --zookeeper zk-cs.default.svc.cluster.local:2181 \
    --partitions 1 \
    --replication-factor 1

Created topic "test1".

$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
    listtopics --restart=Never --rm -- \
    kafka-topics.sh --list \
    --zookeeper zk-cs.default.svc.cluster.local:2181

__consumer_offsets
test
test1
```

Next, issue our Consumer microservice url to replicate messages
from the "test" topic to the "test1" topic:

```bash
curl http://192.168.99.100:32000/goldcar-alpakka-consumer-microservice/test/test1
{}
```

Now we can see that our consumer Alpakka reactive microservice has replicated the messages:

```bash
arturotarin@QOSMIO-X70B:~/Documents/Mistral/2018-08-13 OTD-129. PoC Reactive Microservices communication through Kafka topics using Alpakka/goldcar-alpakka-kafka-microservice
$ kubectl run -ti --image=gcr.io/google_containers/kubernetes-kafka:1.0-10.2.1 \
     consumetest1 --restart=Never --rm -- \
     kafka-console-consumer.sh --topic test1 \
     --bootstrap-server kafka-0.kafka-hs.default.svc.cluster.local:9093 --from-beginning

```

WE SHOULD SEE MESSAGES, BUT THERE AREN'T ANY. CHECK THIS TO SEE WHAT IT HAPPENS !!!!!!!!!!!


ONCE SOLVED THIS ISSUE WE CAN KEEP GOING FROM HERE.
---------------------------------------------------
---------------------------------------------------
---------------------------------------------------
---------------------------------------------------
---------------------------------------------------
---------------------------------------------------
---------------------------------------------------










You should be able to see the number of pending messages from http://<minkube ip>:32000/metrics
and from the custom metrics endpoint:

```bash
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1/namespaces/default/pods/*/messages" | jq .
```

### Autoscaling workers

You can scale the application in proportion to the number of messages in the queue with the Horizontal Pod Autoscaler. You can deploy the HPA with:

```bash
kubectl create -f kube/hpa.yaml
```

You can send more traffic to the application with:

```bash
while true; do sleep 0.5; curl -s http://<minikube ip>:32000/submit; done
```

When the application can't cope with the number of icoming messages, the autoscaler increases the number of pods only every 3 minutes.

You may need to wait three minutes before you can see more pods joining the deployment with:

```bash
kubectl get pods
```

The autoscaler will remove pods from the deployment every 5 minutes.

You can inspect the event and triggers in the HPA with:

```bash
kubectl get hpa spring-boot-hpa
```

### Appendix

Using the secrets checked in the repository to deploy the Prometheus adapter is not recommended.

You should generate your own secrets.

But before you do so, make sure you install `cfssl` - a command line tool and an HTTP API server for signing, verifying, and bundling TLS certificates
                      
You can find more [info about `cfssl` on the official website](https://github.com/cloudflare/cfssl).

Once `cfssl` is installed you generate a new Kubernetes secret with:

```bash
make certs
```

You should redeploy the Prometheus adapter.


## IMPROVING OUR SPRING BOOT MICROSERVICES

### Adding new Consumer and Transactional microservices to our application

As we said before, We will also add rest endpoints for these Consumer functionalities:

    • ConsumerWithPerPartitionBackpressure
    • RebalanceListenerCallbacks
    • ConsumerAtLeastOnce
    • ConsumerAtMostOnce
    • [github/akka/alpakka/doc-examples/src/main/java/csvsamples/](https://github.com/akka/alpakka/blob/e8ac367cad298c9e866db09db96938f1638006ad/doc-examples/src/main/java/csvsamples/FetchHttpEvery30SecondsAndConvertCsvToJsonToKafkaInJava.java)

And we will implement also rest endpoints for these Transactions functionalitites:

    • TransactionsSink
    • TransactionsFailureRetry

### Build secured Docker container images for our microservices

New Poc collected in the ODT-123 list.

### Connecting our microservices to a Kafka secured with Kerberos

We will authenticate our microservices Kafka connections

### Use Kafka Connect JDBC and KSQL

New Poc collected in the ODT-123 list.

### Manage partial failures communication between microservices using the Netflix opensource platform in combination with Alpakka

New Poc collected in the ODT-123 list.

### NGINX Plus Pocs

New Poc collected in the ODT-123 list.

* Nginx service discovery and load balancing
* NGINX security layer: ModSecurity, OWASP core rule set, DDOS mitigation, Rate limiting, Secure Interporcesses Communication
  (TLS termination with SSL communication between services)


### Build a Machine Learning speculative Modl Serving algorithm

New Poc collected in the ODT-123 list.


## USING LAGOM: AN OPENSOURCE MICROSERVICES FRAMEWORK

[Lagom](https://www.lightbend.com/lagom-framework) is an *Opinionated microservice framework* built on Akka and Play.

This is an open source microservices framework that will help us to develop distributed systems faster and easier than
linking our microservices by ourselves, even if we use the Alpakka connectors in the way we said before, in combination with NGINX,
Netflix development tools and any other aid that we may want to use.

That's because as Lightbend experience arises, most microservices frameworks focus on helping you build fragile, single instance
microservices - which, by definition, aren’t scalable or resilient. In contrast, Lagom helps us build microservices as systems —
Reactive systems, to be precise — so that our microservices are elastic and resilient from within.

Building Reactive Systems can be hard, but Lagom abstracts the complexities away. Akka and Play do the heavy lifting so we can focus
on a simpler event-driven programming model on top, while benefitting from a message-driven system under the hood.

Lagom also provides a development environment that relieves us from tedious setup and scripting - using a single command to build
our project and start our services.

And the good news are that our Java team will not need to learn Play, Akka or any other new languages,
because [Lagom is also prepared to run Java microservices](https://developer.lightbend.com/start/?group=lagom&project=lagom-java-maven)

