import java.util.UUID;

public class Member {

    private String name;
    private UUID uuid;

    public Member(String name) {

        this.name = name;
        this.uuid = UUID.randomUUID();
    }

    public Member(String name, String uuid) {

        this.name = name;
        this.uuid = UUID.fromString(uuid);
    }

    public String getName() {

        return this.name;
    }

    public UUID getUUID() {

        return this.uuid;
    }

    @Override
    public String toString() {

        StringBuilder memberString = new StringBuilder();
        memberString.append(this.name).append(", ").append(this.uuid.toString());

        return memberString.toString();
    }
}
