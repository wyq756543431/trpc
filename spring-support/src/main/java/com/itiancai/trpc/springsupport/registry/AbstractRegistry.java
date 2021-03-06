package com.itiancai.trpc.springsupport.registry;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.itiancai.trpc.core.registry.NotifyServiceListener;
import com.itiancai.trpc.core.registry.Registry;
import com.itiancai.trpc.core.registry.ServiceAddress;
import com.itiancai.trpc.core.utils.GrpcUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.HeartbeatMonitor;
import org.springframework.context.event.EventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractRegistry implements Registry {

  @Autowired
  protected DiscoveryClient discoveryClient;

  protected HeartbeatMonitor monitor = new HeartbeatMonitor();

  protected final ConcurrentMap<String, Set<NotifyServiceListener>> subscribed = Maps.newConcurrentMap();

  @Override
  public void subscribe(String group, NotifyServiceListener listener) {
    Set<NotifyServiceListener> listenerSet = subscribed.get(group);
    if(listenerSet == null) {
      listenerSet = new HashSet<>();
      subscribed.put(group, listenerSet);
    }
    listenerSet.add(listener);

  }

  @Override
  public void unsubscribe(String group, NotifyServiceListener listener) {
    Set<NotifyServiceListener> listenerSet = subscribed.get(group);
    if(listenerSet != null) {
      listenerSet.remove(listener);
    }

  }

  @Override
  public Set<ServiceAddress> discover(String group) {
    Set<ServiceAddress> addressSet = Sets.newHashSet();
    List<ServiceInstance> serviceInstanceList = discoveryClient.getInstances(group);
    for (ServiceInstance serviceInstance : serviceInstanceList) {
      Map<String, String> metadata = serviceInstance.getMetadata();
      if (metadata.get(REGISTRY_KEY) != null) {
        Integer port = Integer.valueOf(metadata.get(REGISTRY_KEY));
        addressSet.add(new ServiceAddress(serviceInstance.getHost(), port));
      }
    }
    return addressSet;
  }

  @EventListener(HeartbeatEvent.class)
  public void heartbeat(HeartbeatEvent event) {

    if(monitor.update(event.getValue())) {
      for(String group : subscribed.keySet()) {

        Set<ServiceAddress> addressSet = discover(group);
        for (NotifyServiceListener listener : subscribed.get(group)) {
          listener.notify(addressSet);
        }
      }
    }
  }
}
