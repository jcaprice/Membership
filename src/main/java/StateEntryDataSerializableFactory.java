import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class StateEntryDataSerializableFactory implements  DataSerializableFactory {

    static final int ID = 1;
    static final int STATE_TYPE = 1;

    @Override
    public IdentifiedDataSerializable create(int typeId) {

        if (typeId == STATE_TYPE) {

            return new StateEntry();

        } else {

            return null;
        }
    }
}
