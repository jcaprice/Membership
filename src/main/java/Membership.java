import java.io.IOException;
import java.util.Scanner;

import com.beust.jcommander.*;

public class Membership {

    static class Args {

        @Parameter(description = "Command")
        public String command;

        @Parameter(names = {"-n", "--name"})
        public String name;

        @Parameter(names = {"-s", "--size"})
        public int size;
    }

    public static void main(String[] args) {

        MembershipManager membershipManager = MembershipManager.getManager();

        Scanner scanner = new Scanner(System.in);

        Args arguments = new Args();

        boolean next = true;

        while(next) {
            String line = scanner.nextLine();

            try {
                JCommander.newBuilder()
                        .addObject(arguments)
                        .build()
                        .parse(line.split(" "));
            } catch (ParameterException e) {

                System.out.println("Incorrect command structure: \n" + e.getMessage());
            }

            switch (arguments.command) {
                case "identify":
                    Result identifyResult = membershipManager.identify();
                    System.out.println(identifyResult.getMessage());
                    break;
                case "status":
                    Result statusResult = membershipManager.status();
                    System.out.println(statusResult.getMessage());
                    break;
                case "bootstrap":
                    if(arguments.size == 0) {
                        System.out.println("Please specify an initial cluster size using the '-s' parameter.");

                    } else {
                        Result bootstrapResult = membershipManager.bootstrap(arguments.size);
                        System.out.println(bootstrapResult.toString());
                    }
                    break;
                case "join":
                    if (arguments.name == null) {
                        System.out.println("Please specify a node name using the '-n' parameter.");
                    } else {
                        Result joinResult = membershipManager.join(new Member(arguments.name));
                        System.out.println(joinResult.toString());
                    }
                    break;
                case "leave":
                    Result leaveResult = membershipManager.leave();
                    System.out.println(leaveResult.toString());
                    break;
                case "remove":
                    if (arguments.name == null) {
                        System.out.println("Please specify a node name using the '-n' parameter.");
                    } else {
                        Result removeResult = membershipManager.remove(arguments.name);
                        System.out.println(removeResult.toString());
                    }
                    break;
                case "recover":
                    if (arguments.name == null) {
                        System.out.println("Please specify a node name using the '-n' parameter.");
                    } else {
                        try {
                            Result recoverResult = membershipManager.recover(arguments.name);
                            System.out.println(recoverResult.toString());
                        }
                        catch (IOException e) {

                            System.out.println("Error recovering from restart" + e.getMessage());
                        }
                    }
                    break;
                case "shutdown":
                    next = false;
                    Result shutdownResult = membershipManager.shutdown();
                    System.out.println(shutdownResult.toString());
                    scanner.close();
                    break;
                default:
                    System.out.println("No available command specified. The available commands are: status, bootstrap, join, leave, remove, and shutdown.");
            }
        }
    }
}