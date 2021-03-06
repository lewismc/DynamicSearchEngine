package org.apache.solr.common.cloud;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterStateUtil {
  private static Logger log = LoggerFactory.getLogger(ClusterStateUtil.class);
  
  private static final int TIMEOUT_POLL_MS = 1000;
  
  /**
   * Wait to see *all* cores live and active.
   * 
   * @param zkStateReader
   *          to use for ClusterState
   * @param timeoutInMs
   *          how long to wait before giving up
   * @return false if timed out
   */
  public static boolean waitForAllActiveAndLive(ZkStateReader zkStateReader, int timeoutInMs) {
    return waitForAllActiveAndLive(zkStateReader, null, timeoutInMs);
  }
  
  /**
   * Wait to see *all* cores live and active.
   * 
   * @param zkStateReader
   *          to use for ClusterState
   * @param collection to look at
   * @param timeoutInMs
   *          how long to wait before giving up
   * @return false if timed out
   */
  public static boolean waitForAllActiveAndLive(ZkStateReader zkStateReader, String collection,
      int timeoutInMs) {
    long timeout = System.nanoTime()
        + TimeUnit.NANOSECONDS.convert(timeoutInMs, TimeUnit.MILLISECONDS);
    boolean success = false;
    while (System.nanoTime() < timeout) {
      success = true;
      ClusterState clusterState = zkStateReader.getClusterState();
      if (clusterState != null) {
        Set<String> collections;
        if (collection != null) {
          collections = Collections.singleton(collection);
        } else {
          collections = clusterState.getCollections();
        }
        for (String coll : collections) {
          DocCollection docCollection = clusterState.getCollection(coll);
          Collection<Slice> slices = docCollection.getSlices();
          for (Slice slice : slices) {
            // only look at active shards
            if (slice.getState().equals(Slice.ACTIVE)) {
              Collection<Replica> replicas = slice.getReplicas();
              for (Replica replica : replicas) {
                // on a live node?
                boolean live = clusterState.liveNodesContain(replica
                    .getNodeName());
                String state = replica.getStr(ZkStateReader.STATE_PROP);
                if (!live || !state.equals(ZkStateReader.ACTIVE)) {
                  // fail
                  success = false;
                }
              }
            }
          }
        }
        if (!success) {
          try {
            Thread.sleep(TIMEOUT_POLL_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolrException(ErrorCode.SERVER_ERROR, "Interrupted");
          }
        }
      }
    }
    
    return success;
  }
  
  /**
   * Wait to see an entry in the ClusterState with a specific coreNodeName and
   * baseUrl.
   * 
   * @param zkStateReader
   *          to use for ClusterState
   * @param collection
   *          to look in
   * @param coreNodeName
   *          to wait for
   * @param baseUrl
   *          to wait for
   * @param timeoutInMs
   *          how long to wait before giving up
   * @return false if timed out
   */
  public static boolean waitToSeeLive(ZkStateReader zkStateReader,
      String collection, String coreNodeName, String baseUrl,
      int timeoutInMs) {
    long timeout = System.nanoTime()
        + TimeUnit.NANOSECONDS.convert(timeoutInMs, TimeUnit.MILLISECONDS);
    
    while (System.nanoTime() < timeout) {
      log.debug("waiting to see replica just created live collection={} replica={} baseUrl={}",
          collection, coreNodeName, baseUrl);
      ClusterState clusterState = zkStateReader.getClusterState();
      if (clusterState != null) {
        DocCollection docCollection = clusterState.getCollection(collection);
        Collection<Slice> slices = docCollection.getSlices();
        for (Slice slice : slices) {
          // only look at active shards
          if (slice.getState().equals(Slice.ACTIVE)) {
            Collection<Replica> replicas = slice.getReplicas();
            for (Replica replica : replicas) {
              // on a live node?
              boolean live = clusterState.liveNodesContain(replica.getNodeName());
              String rcoreNodeName = replica.getName();
              String rbaseUrl = replica.getStr(ZkStateReader.BASE_URL_PROP);
              if (live && coreNodeName.equals(rcoreNodeName)
                  && baseUrl.equals(rbaseUrl)) {
                // found it
                return true;
              }
            }
          }
        }
        try {
          Thread.sleep(TIMEOUT_POLL_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new SolrException(ErrorCode.SERVER_ERROR, "Interrupted");
        }
      }
    }
    
    log.error("Timed out waiting to see replica just created in cluster state. Continuing...");
    return false;
  }
  
  public static boolean waitForAllNotLive(ZkStateReader zkStateReader, int timeoutInMs) {
    return waitForAllNotLive(zkStateReader, null, timeoutInMs);
  }
  

  public static boolean waitForAllNotLive(ZkStateReader zkStateReader,
      String collection, int timeoutInMs) {
    long timeout = System.nanoTime()
        + TimeUnit.NANOSECONDS.convert(timeoutInMs, TimeUnit.MILLISECONDS);
    boolean success = false;
    while (System.nanoTime() < timeout) {
      success = true;
      ClusterState clusterState = zkStateReader.getClusterState();
      if (clusterState != null) {
        Set<String> collections;
        if (collection == null) {
          collections = clusterState.getCollections();
        } else {
          collections = Collections.singleton(collection);
        }
        for (String coll : collections) {
          DocCollection docCollection = clusterState.getCollection(coll);
          Collection<Slice> slices = docCollection.getSlices();
          for (Slice slice : slices) {
            // only look at active shards
            if (slice.getState().equals(Slice.ACTIVE)) {
              Collection<Replica> replicas = slice.getReplicas();
              for (Replica replica : replicas) {
                // on a live node?
                boolean live = clusterState.liveNodesContain(replica
                    .getNodeName());
                if (live) {
                  // fail
                  success = false;
                }
              }
            }
          }
        }
        if (!success) {
          try {
            Thread.sleep(TIMEOUT_POLL_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolrException(ErrorCode.SERVER_ERROR, "Interrupted");
          }
        }
      }
    }
    
    return success;
  }
  
  public static boolean isAutoAddReplicas(ZkStateReader reader, String collection) {
    ClusterState clusterState = reader.getClusterState();
    if (clusterState != null) {
      DocCollection docCollection = clusterState.getCollectionOrNull(collection);
      if (docCollection != null) {
        return docCollection.getAutoAddReplicas();
      }
    }
    return false;
  }
  
}
