# Cluster Membership Demo App

This application emulates some of the behavior commonly found in the cluster membership components of a distributed
database. It uses an embedded instance of Hazelcast, that discovers peers via multicast, for distributed cluster state.
Once all nodes are joined to the cluster (and are all ONLINE) a "We are Started!" message will be printed to the console
on the leader node.

## Commands

### identify

**Description**

Outputs the name and UUID of this node.

**Example**

`> identify`

`member1: 18d714d6-eb63-458d-a536-22247ca72f1b`

### status

**Description**

Outputs the UUID, Name, Status, and Heartbeat for each member of cluster.

**Example**

`> status`

```
Leader: 18d714d6-eb63-458d-a536-22247ca72f1b

UUID                                  Name                Status  Heartbeat  
18d714d6-eb63-458d-a536-22247ca72f1b  member1             ONLINE  1533341430 
baa6fd65-ac55-4590-84cd-efaedcc878f3  member5             ONLINE  1533341430 
f6e61ec6-380c-4857-8f1a-22c7c22193d7  member3             ONLINE  1533341429 
4823cca9-54a8-44a3-8a7e-bbd7875037f2  member4             ONLINE  1533341425 
b85c3aca-28a3-42fb-b2f7-4ae9f55a63b0  member2             ONLINE  1533341424
```

### join

**Parameters**

-n (name). The name of this node.

**Description**

Join this node to the cluster with the provided node name. Although each node is assigned a UUID for identification,
node names must be unique.

**Example**

`> join -n member1`

`SUCCESS: member1, b97f465d-c621-4d50-a184-4f8be5716530 is ONLINE`

### leave

**Description**

Instruct the running node to leave the cluster. Must be run on the node leaving the cluster.

**Example**

`> leave`

`SUCCESS: member2 has left the cluster.`

### remove

**Parameters**

-n (name). The name of the node to be removed.

**Description**

Remove the specified node from the cluster. In order for a node to be removed it must be part of the cluster and must
be marked OFFLINE or JOINING (but not currently online) in the status output.

**Example**

`> remove -n member2`

`SUCCESS: member2 has been removed from the cluster.`

### recover

**Parameters**

-n (name). The name of the node to to attempt a recovery for.

**Description**

Recover from a shutdown or unexpected restart by attempted to load the on-disk metadata for the specified node. If
successful, the node will be marked ONLINE by the cluster leader.

**Example**

`> recover -n member1`

`SUCCESS: Successfully recovered UUID for member1`

### shutdown

**Description**

Shuts down the node. If the node was the leader, a new leader will be elected.

**Example**

`> shutdown`

`SUCCESS: Successfully shut down.`
