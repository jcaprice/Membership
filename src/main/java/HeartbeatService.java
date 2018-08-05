import com.hazelcast.core.*;

public class HeartbeatService implements Runnable {

    private HazelcastInstance instance;
    private Member member;

    public HeartbeatService(HazelcastInstance instance, Member member) {

        this.instance = instance;
        this.member = member;
    }

    public void run() {

        this.sendHeartbeat();
    }

    private void sendHeartbeat() {

        String uuid = this.member.getUUID().toString();

        IMap<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        StateEntry state = mapClusterState.get(uuid);

        mapClusterState.set(uuid, state.updateHeartbeat(System.currentTimeMillis() / 1000L));
    }
}
