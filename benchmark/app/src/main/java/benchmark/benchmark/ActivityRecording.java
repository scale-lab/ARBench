package benchmark.benchmark;

public class ActivityRecording {
    private Class<?> activity;
    private String recordingFileName;

    public ActivityRecording(Class<?> activity, String recordingFileName) {
        this.activity = activity;
        this.recordingFileName = recordingFileName;
    }

    public Class<?> getActivity() {
        return activity;
    }

    public void setActivity(Class<?> activity) {
        this.activity = activity;
    }

    public String getRecordingFileName() {
        return recordingFileName;
    }

    public void setRecordingFileName(String recordingFileName) {
        this.recordingFileName = recordingFileName;
    }
}
