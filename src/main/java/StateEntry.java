import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.lang.StringBuilder;

public class StateEntry implements IdentifiedDataSerializable {

    private String name;
    private ClusterStatus status;
    private Long heartbeat;

    public StateEntry() { }

    StateEntry(String name, ClusterStatus status, Long heartbeat) {

        this.name = name;
        this.status = status;
        this.heartbeat = heartbeat;
    }

    public String getName() {

        return this.name;
    }

    public ClusterStatus getStatus() {

        return this.status;
    }

    public Long getHeartbeat() {

        return this.heartbeat;
    }

    public StateEntry updateStatus(ClusterStatus status) {

        this.status = status;

        return this;
    }

    public StateEntry updateHeartbeat(Long heartbeat) {

        this.heartbeat = heartbeat;

        return this;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeUTF(name);
        out.writeInt(status.ordinal());
        out.writeLong(heartbeat);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        name = in.readUTF();
        status = ClusterStatus.values()[in.readInt()];
        heartbeat = in.readLong();
    }

    @Override
    public int getFactoryId() {

        return StateEntryDataSerializableFactory.ID;
    }

    @Override
    public int getId() {

        return StateEntryDataSerializableFactory.STATE_TYPE;
    }

    @Override
    public String toString() {

        StringBuilder s = new StringBuilder();
        s.append("StateEntry( ");
        s.append("Name: ").append(String.format("%16$-" + name));
        s.append("Status: ").append(String.format("%10$-" + status.name()));
        s.append("Heartbeat: ").append(String.format("%10$-" + heartbeat));
        s.append(")");

        return s.toString();
    }
}
