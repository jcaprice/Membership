import com.hazelcast.core.*;
import com.hazelcast.transaction.*;

import java.util.Map;

public class LeaderService implements Runnable {

    private static final int NODE_TIMEOUT_SECONDS = 10;
    private HazelcastInstance instance;
    private Member member;
    private boolean isLeader;

    public LeaderService(HazelcastInstance instance, Member member) {

        this.instance = instance;
        this.member = member;
        this.isLeader = false;
    }

    public void run() {

        this.maybeBecomeLeader();

        if(this.isLeader) {

            this.maybeChangeStatus();
        }
    }

    private void maybeBecomeLeader() {

        Map<String, String> mapMetadata = instance.getMap("metadata");
        Map<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        String currentLeader = mapMetadata.get("leader");

        Long currentTime = System.currentTimeMillis() / 1000L;

        if (currentLeader == null || !mapClusterState.containsKey(currentLeader)) {

            this.becomeLeader();
        } else {

            StateEntry leaderStateEntry = mapClusterState.get(currentLeader);

            if ((currentTime - leaderStateEntry.getHeartbeat()) > NODE_TIMEOUT_SECONDS) {

                this.becomeLeader();
            }
        }
    }

    private void becomeLeader() {

        TransactionOptions options = new TransactionOptions().setTransactionType(TransactionOptions.TransactionType.TWO_PHASE);

        TransactionContext context = instance.newTransactionContext(options);

        context.beginTransaction();

        TransactionalMap<String, String> map = context.getMap("metadata");

        try {

            map.put("leader", this.member.getUUID().toString());
            context.commitTransaction();
            this.isLeader = true;

        } catch ( Throwable t ) {

            context.rollbackTransaction();
            this.isLeader = false;
            throw t;
        }
    }

    private void maybeChangeStatus() {

        IMap<String, StateEntry> mapClusterState = instance.getMap("clusterState");

        Long currentTime = System.currentTimeMillis() / 1000L;

        for (Map.Entry<String, StateEntry> entry : mapClusterState.entrySet()) {

            StateEntry state = entry.getValue();

            if (((currentTime - state.getHeartbeat()) > NODE_TIMEOUT_SECONDS) && entry.getValue().getStatus() == ClusterStatus.ONLINE) {

                mapClusterState.set(entry.getKey(), state.updateStatus(ClusterStatus.OFFLINE));
            } else if (((currentTime - state.getHeartbeat()) <= NODE_TIMEOUT_SECONDS) && entry.getValue().getStatus() == ClusterStatus.OFFLINE) {

                mapClusterState.set(entry.getKey(), state.updateStatus(ClusterStatus.ONLINE));
            }
        }
    }
}





