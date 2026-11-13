package in.kvapps.wirelessstart.dto;

public class CmdExecResultData {
    private String executedCommand;
    private String result;
    private long timeTaken;

    // Default (no-args) constructor
    public CmdExecResultData() {
    }

    // All-args constructor
    public CmdExecResultData(String executedCommand, String result, long timeTaken) {
        this.executedCommand = executedCommand;
        this.result = result;
        this.timeTaken = timeTaken;
    }

    // Getters and Setters
    public String getExecutedCommand() {
        return executedCommand;
    }

    public void setExecutedCommand(String executedCommand) {
        this.executedCommand = executedCommand;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getTimeTaken() {
        return timeTaken;
    }

    public void setTimeTaken(long timeTaken) {
        this.timeTaken = timeTaken;
    }

    // toString method
    @Override
    public String toString() {
        return "CmdExecResultData{" +
                "executedCommand='" + executedCommand + '\'' +
                ", result='" + result + '\'' +
                ", timeTaken=" + timeTaken +
                '}';
    }
}