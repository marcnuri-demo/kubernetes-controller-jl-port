package com.marcnuri.demo.booternetes.port;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.annotations.QuarkusMain;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@QuarkusMain
public class KubernetesControllerApplication implements QuarkusApplication {

  @Inject
  SharedInformerFactory sharedInformerFactory;
  @Inject
  ResourceEventHandler<Node> nodeEventHandler;

  @Override
  public int run(String... args) throws Exception {
    final var startAsync = sharedInformerFactory.startAllRegisteredInformers();
    startAsync.get();
    final var nodeHandler = sharedInformerFactory.getExistingSharedIndexInformer(Node.class);
    nodeHandler.addEventHandler(nodeEventHandler);
    while (nodeHandler.isRunning()) {
      // Controller loop
      TimeUnit.MILLISECONDS.sleep(500L);
    }
    return 0;
  }

  void onShutDown(@Observes ShutdownEvent event) {
    sharedInformerFactory.stopAllRegisteredInformers(true);
  }

  public static void main(String... args) {
    Quarkus.run(KubernetesControllerApplication.class, args);
  }

  @ApplicationScoped
  static final class KubernetesControllerApplicationConfig {

    @Singleton
    SharedInformerFactory sharedInformerFactory(KubernetesClient client) {
      return client.informers();
    }

    @Singleton
    SharedIndexInformer<Node> nodeInformer(SharedInformerFactory factory) {
      return factory.sharedIndexInformerFor(Node.class, 0);
    }

    @Singleton
    SharedIndexInformer<Pod> podInformer(SharedInformerFactory factory) {
      return factory.sharedIndexInformerFor(Pod.class, 0);
    }

    @Singleton
    ResourceEventHandler<Node> nodeReconciler(SharedIndexInformer<Node> nodeInformer, SharedIndexInformer<Pod> podInformer) {
      return new ResourceEventHandler<Node>() {

        @Override
        public void onAdd(Node node) {
          System.out.printf("node: %s%n", Objects.requireNonNull(node.getMetadata()).getName());
          podInformer.getIndexer().list().stream()
            .map(pod -> Objects.requireNonNull(pod.getMetadata()).getName())
            .forEach(podName -> System.out.printf("pod name: %s%n", podName));
        }

        @Override
        public void onUpdate(Node oldObj, Node newObj) {}

        @Override
        public void onDelete(Node node, boolean deletedFinalStateUnknown) {}
      };
    }

  }
}
