import com.hazelcast.config.Config;
import com.hazelcast.core.*;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MembershipManager {

    private static final int JOIN_DELAY = 10;

    private static final MembershipManager MANAGER = new MembershipManager();

    private static HazelcastInstance instance;
    private static Member member;

    private ScheduledExecutorService finalizeJoinService = Executors.newSingleThreadScheduledExecutor();

    private Runnable heartbeatService;
    private ScheduledExecutorService heartbeatExecutorService = Executors.newSingleThreadScheduledExecutor();

    private Runnable leaderService;
    private ScheduledExecutorService leaderExecutorService = Executors.newSingleThreadScheduledExecutor();

    private MembershipManager() {

        Config config = new Config();

        config.getSerializationConfig().addDataSerializableFactory(1, new StateEntryDataSerializableFactory());
        this.instance = Hazelcast.newHazelcastInstance(config);
    }

    static MembershipManager getManager() {

        return MANAGER;
    }

    Result identify() {

        if(this.member != null) {

            return new Result(ResultStatus.SUCCESS, member.getName() + ": " + member.getUUID().toString());

        } else {

            return new Result(ResultStatus.FAILED, "This node has no identity yet. Run the join command to give it one.");
        }
    }

    Result status() {

        Map<String, String> mapMetadata = instance.getMap("metadata");
        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        StringBuilder statusString = new StringBuilder();

        statusString.append("Leader: ")
                .append(mapMetadata.get("leader"))
                .append("\n\n");

        statusString.append(String.format("%-38s%-20s%-8s%-11s\n", "UUID", "Name", "Status", "Heartbeat"));

        for (Map.Entry<String, StateEntry> entry : mapClusterState.entrySet()) {

            statusString.append(String.format("%-38s%-20s%-8s%-11s\n", entry.getKey(),
                    entry.getValue().getName(),
                    entry.getValue().getStatus().toString(),
                    entry.getValue().getHeartbeat().toString()));
        }

        return new Result(ResultStatus.SUCCESS, statusString.toString());
    }

    synchronized Result stageJoin(Member member) {

        Map<String, String> mapRegistry = instance.getMap("registry");
        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        Result result;
        String name = member.getName();
        String uuidString = member.getUUID().toString();

        // Check to see if stageJoin has been run already
        if(this.member == null) {

            // Make sure a node with this name has not been added to the cluster.
            if (!mapRegistry.containsKey(name)) {

                // Add node to registry and add JOINING state to clusterState Map
                mapRegistry.put(name, uuidString);
                StateEntry state = new StateEntry(name, ClusterStatus.JOINING, 0L);
                mapClusterState.put(uuidString, state);

                this.member = member;

                // Persist node UUID to disk and schedule job to finalize join
                try {

                    this.persistUUID(this.member.getName());

                    Runnable task = () ->  finalizeJoin();

                    finalizeJoinService.schedule(task, JOIN_DELAY, TimeUnit.SECONDS);
                    finalizeJoinService.shutdown();

                    result = new Result(ResultStatus.SUCCESS, member.toString() + " is " + state.getStatus().toString());

                } catch (IOException e) {

                    result = new Result(ResultStatus.FAILED, "Error persisting UUID.");
                }

            } else {

                result = new Result(ResultStatus.FAILED, "This member name already exists in the cluster with a different UUID.");
            }

        } else {

            result = new Result(ResultStatus.FAILED, "This node, " + this.member.getName() + ", has already been joined to the cluster.");
        }

        return result;
    }

    synchronized Result recover(String memberName) throws IOException {

        Result result;

        IMap<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        BufferedReader br = new BufferedReader(new FileReader(memberName));

        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        if (line != null) {

            sb.append(line);
        }

        String uuid = sb.toString();

        //Check to see that recovered UUID for this node is part of the cluster
        if (mapClusterState.containsKey(uuid)) {

            this.member = new Member(memberName, sb.toString());

            // If the node is marked OFFLINE, bring it ONLINE and start services
            if (mapClusterState.get(uuid).getStatus() == ClusterStatus.OFFLINE) {

                StateEntry state = new StateEntry(member.getName(), ClusterStatus.ONLINE, 0L);
                mapClusterState.put(member.getUUID().toString(), state);

                this.startServices();

                result = new Result(ResultStatus.SUCCESS, "Successfully recovered UUID for " + memberName);

            }

            // If the node was JOINING, re-issue JOIN
            else if (mapClusterState.get(uuid).getStatus() == ClusterStatus.JOINING){

                Runnable task = () ->  finalizeJoin();

                finalizeJoinService.schedule(task, JOIN_DELAY, TimeUnit.SECONDS);
                finalizeJoinService.shutdown();

                result = new Result(ResultStatus.SUCCESS, "Successfully recovered UUID for " + memberName + " and rejoining cluster.");

            } else {

                result = new Result(ResultStatus.FAILED, memberName + " is currently online.");
            }
        } else {

            result = new Result(ResultStatus.FAILED, memberName + " does not exist.");
        }

        br.close();

        return result;
    }

    // Remove this node from the cluster
    synchronized Result leave() {

        String memberName = this.member.getName();

        Map<String, String> mapRegistry = instance.getMap("registry");

        String uuid = mapRegistry.get(memberName);

        this.removeMember(memberName, uuid);

        File file = new File(memberName);

        file.delete();

        this.member = null;

        return new Result(ResultStatus.SUCCESS, memberName + " has left the cluster.");
    }

    // Remove the specified node from the cluster
    synchronized Result remove(String memberName) {

        Result result;

        IMap<String, String> mapRegistry = instance.getMap("registry");
        IMap<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        // Check to see if the node being removed is a member of the cluster
        if(mapRegistry.containsKey(memberName)) {

            String uuid = mapRegistry.get(memberName);
            ClusterStatus status = mapClusterState.get(uuid).getStatus();

            // Make sure the node being removed has been marked OFFLINE or JOINING
            if ((status == ClusterStatus.OFFLINE) || (status == ClusterStatus.JOINING)) {

                this.removeMember(memberName, uuid);

                result = new Result(ResultStatus.SUCCESS, memberName + " has been removed from the cluster.");

            } else {

                result = new Result(ResultStatus.FAILED, "Member" + memberName + " is currently online.");
            }
        } else {

            result = new Result(ResultStatus.FAILED, "Member" + memberName + " is not a member of the cluster.");
        }

        return result;
    }

    // Stop services and shut down Hazelcast instance
    Result shutdown() {

        this.heartbeatExecutorService.shutdown();
        this.leaderExecutorService.shutdown();

        instance.shutdown();

        return new Result(ResultStatus.SUCCESS, "Successfully shut down.");
    }

    /******************************************************************************************************************/

    // Write UUID to disk so nodes that re-start can be recovered
    private void persistUUID(String memberName) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(memberName));
        writer.write(this.member.getUUID().toString());
        writer.close();
    }

    // Change node status to ONLINE and start services
    private void finalizeJoin() {

        String uuidString = this.member.getUUID().toString();

        IMap<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        StateEntry state = mapClusterState.get(uuidString);

        mapClusterState.set(uuidString, state.updateStatus(ClusterStatus.ONLINE));

        startServices();
    }

    // Start Heartbeat and Leader services
    private void startServices() {

        this.heartbeatService = new HeartbeatService(this.instance, this.member);
        this.heartbeatExecutorService.scheduleAtFixedRate(heartbeatService, 0, 10, TimeUnit.SECONDS);

        this.leaderService = new LeaderService(this.instance, this.member);
        this.leaderExecutorService.scheduleAtFixedRate(leaderService, 0, 10, TimeUnit.SECONDS);
    }

    // Remove node from registry and clusterState Hazelcast maps
    private void removeMember(String name, String uuid) {

        IMap<String, String> mapRegistry = instance.getMap("registry");
        IMap<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        mapRegistry.remove(name);
        mapClusterState.remove(uuid);
    }
}
