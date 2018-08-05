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

public class MembershipManager {

    private static final MembershipManager MANAGER = new MembershipManager();

    private static HazelcastInstance instance;
    private static Member member;

    private Runnable heartbeatService;
    private ScheduledExecutorService heartbeatExecutorService;

    private Runnable leaderService;
    private ScheduledExecutorService leaderExecutorService;

    private MembershipManager() {

        Config config = new Config();

        config.getSerializationConfig().addDataSerializableFactory(1, new StateEntryDataSerializableFactory());
        this.instance = Hazelcast.newHazelcastInstance(config);
    }

    public static MembershipManager getManager() {

        return MANAGER;
    }

    public Result identify() {

        if(this.member != null) {

            return new Result(ResultStatus.SUCCESS, member.getName() + ": " + member.getUUID().toString());
        } else {

            return new Result(ResultStatus.FAILED, "This node has no identity yet. Run the join command to give it one.");
        }
    }

    public Result status() {

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

    public synchronized Result bootstrap(int size) {

        Result result;

        Map<String, String> mapMetadata = instance.getMap("metadata");

        if(!mapMetadata.containsKey("size")) {

            mapMetadata.put("size", Integer.toString(size));

            result = new Result(ResultStatus.SUCCESS, "Cluster bootstrapped.");
        } else {

            result = new Result(ResultStatus.FAILED, "Cluster is already bootstrapped with a size of: " + mapMetadata.get("size"));
        }

        return result;
    }

    public synchronized Result join(Member member) {

        Map<String, String> mapRegistry = instance.getMap("registry");
        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        Result result;
        String name = member.getName();
        String uuidString = member.getUUID().toString();

        if(this.member == null) {

            if (!mapRegistry.containsKey(name)) {

                mapRegistry.put(name, uuidString);
                StateEntry state = new StateEntry(name, ClusterStatus.ONLINE, 0L);
                mapClusterState.put(uuidString, state);

                this.member = member;

                startServices();

                try {

                    this.persistUUID(this.member.getName());

                    if(this.shouldPrintMessage()) {

                        System.out.println("We are Started!");
                    }

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

    public synchronized Result leave() {

        String memberName = this.member.getName();

        Map<String, String> mapRegistry = instance.getMap("registry");

        String uuid = mapRegistry.get(memberName);

        this.removeMember(memberName, uuid);

        File file = new File(memberName);

        file.delete();

        this.member = null;

        return new Result(ResultStatus.SUCCESS, memberName + " has left the cluster.");
    }

    public synchronized Result remove(String memberName) {

        Result result;

        Map<String, String> mapRegistry = instance.getMap("registry");
        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        if(mapRegistry.containsKey(memberName)) {

            String uuid = mapRegistry.get(memberName);

            if (mapClusterState.get(uuid).getStatus() == ClusterStatus.OFFLINE) {

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

    public synchronized Result recover(String memberName) throws IOException {

        Result result;

        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        BufferedReader br = new BufferedReader(new FileReader(memberName));

        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        if (line != null) {
            sb.append(line);
        }

        String uuid = sb.toString();

        if (mapClusterState.containsKey(uuid)) {

            if (mapClusterState.get(uuid).getStatus() == ClusterStatus.OFFLINE) {

                this.member = new Member(memberName, sb.toString());

                StateEntry state = new StateEntry(member.getName(), ClusterStatus.ONLINE, 0L);
                mapClusterState.put(member.getUUID().toString(), state);

                this.startServices();
                result = new Result(ResultStatus.SUCCESS, "Successfully recovered UUID for " + memberName);
            } else {

                result = new Result(ResultStatus.FAILED, memberName + " is currently online.");
            }
        } else {

            result = new Result(ResultStatus.FAILED, memberName + " does not exist.");
        }

        if (this.shouldPrintMessage()) {

            System.out.println("We are Started!");
        }

        br.close();

        return result;
    }

    private void removeMember(String name, String uuid) {

        Map<String, String> mapRegistry = instance.getMap("registry");
        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        mapRegistry.remove(name);
        mapClusterState.remove(uuid);
    }

    public Result shutdown() {

        this.heartbeatExecutorService.shutdown();
        this.leaderExecutorService.shutdown();

        instance.shutdown();

        return new Result(ResultStatus.SUCCESS, "Successfully shut down.");
    }

    /******************************************************************************************************************/

    private void persistUUID(String memberName) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(memberName));
        writer.write(this.member.getUUID().toString());
        writer.close();
    }

    private void startServices() {

        this.heartbeatService = new HeartbeatService(this.instance, this.member);
        this.heartbeatExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.heartbeatExecutorService.scheduleAtFixedRate(heartbeatService, 0, 10, TimeUnit.SECONDS);

        this.leaderService = new LeaderService(this.instance, this.member);
        this.leaderExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.leaderExecutorService.scheduleAtFixedRate(leaderService, 0, 10, TimeUnit.SECONDS);
    }

    private boolean shouldPrintMessage() {

        Map<String, String> mapMetadata = instance.getMap("metadata");

        boolean anyOffline = false;
        boolean returnValue = false;
        boolean isPrinted = Boolean.TRUE.equals(mapMetadata.get("printed"));

        int expectedClusterSize = Integer.parseInt(mapMetadata.get("size"));

        if(!isPrinted) {

            Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

            if (mapClusterState.size() == expectedClusterSize) {

                for (Map.Entry<String, StateEntry> entry : mapClusterState.entrySet()) {

                    StateEntry state = entry.getValue();

                    if (state.getStatus() == ClusterStatus.OFFLINE) {

                        anyOffline = true;
                    }
                }

                if (!anyOffline) {

                    mapMetadata.put("printed", String.valueOf(true));
                    returnValue = true;
                }
            }
        }

        return returnValue;
    }
}
