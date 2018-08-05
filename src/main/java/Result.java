public class Result {

    private ResultStatus status;
    private String message;

    public Result(ResultStatus status, String message) {

        this.status = status;
        this.message = message;
    }

    public ResultStatus getStatus() {

        return this.status;
    }

    public String getMessage() {

        return this.message;
    }

    @Override
    public String toString() {

        StringBuilder resultString = new StringBuilder();
        resultString.append(this.status.toString()).append(": ").append(this.message);

        return resultString.toString();
    }
}
