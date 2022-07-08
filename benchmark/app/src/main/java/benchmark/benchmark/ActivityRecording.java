/*
 * Copyright 2021, Brown University, Providence, RI.
 * Rahul Shahi, Sherief Reda, Seif Abdelaziz
 *
 *                        All Rights Reserved
 *
 * Permission to use, copy, modify, and distribute this software and
 * its documentation for any purpose other than its incorporation into a
 * commercial product or service is hereby granted without fee, provided
 * that the above copyright notice appear in all copies and that both
 * that copyright notice and this permission notice appear in supporting
 * documentation, and that the name of Brown University not be used in
 * advertising or publicity pertaining to distribution of the software
 * without specific, written prior permission.
 *
 * BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR ANY
 * PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY BE LIABLE FOR
 * ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package benchmark.benchmark;

public class ActivityRecording {
    private Class<?> activity;
    private final String recordingFileName;
    private final String sectionName;
    private boolean enabled;
    private final boolean useCloud;
    private final boolean isConfigurable;
    private boolean requiresCredentialsFile;
    private boolean requiresGCPKeys;
    private int cloudPercentage;

    public ActivityRecording(Class<?> activity, String recordingFileName, String sectionName, Boolean useCloud, Boolean requiresGCPKeys, Boolean requiresCredentialsFile, Boolean isConfigurable) {
        this.activity = activity;
        this.recordingFileName = recordingFileName;
        this.sectionName = sectionName;
        this.enabled = true;
        this.useCloud = useCloud;
        this.requiresCredentialsFile = requiresCredentialsFile;
        this.requiresGCPKeys = requiresGCPKeys;
        this.isConfigurable = isConfigurable;
        cloudPercentage = this.useCloud ? 100 : 0;
    }

    public ActivityRecording(Class<?> activity, String recordingFileName, String sectionName, Boolean useCloud, Boolean requiresGCPKeys, Boolean requiresCredentialsFile) {
        this.activity = activity;
        this.recordingFileName = recordingFileName;
        this.sectionName = sectionName;
        this.enabled = true;
        this.useCloud = useCloud;
        this.requiresCredentialsFile = requiresCredentialsFile;
        this.requiresGCPKeys = requiresGCPKeys;
        this.isConfigurable = false;
        cloudPercentage = this.useCloud ? 100 : 0;

    }

    public ActivityRecording(Class<?> activity, String recordingFileName, String sectionName, Boolean useCloud) {
        this.activity = activity;
        this.recordingFileName = recordingFileName;
        this.sectionName = sectionName;
        this.enabled = true;
        this.useCloud = useCloud;
        this.isConfigurable = false;
        cloudPercentage = this.useCloud ? 100 : 0;
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

    public String getSectionName() {
        return sectionName;
    }

    public boolean isUsingCloud() {
        return useCloud;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigurable() {
        return isConfigurable;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean doesUseCloud() {
        return useCloud;
    }

    public boolean doesRequireCredentialsFile() {
        return requiresCredentialsFile;
    }

    public boolean doesRequireGCPKeys() {
        return requiresGCPKeys;
    }

    public void setCloudPercentage(int cloudPercentage) {
        this.cloudPercentage = cloudPercentage;
    }

    public int getCloudPercentage() {
        return this.getCloudPercentage();
    }
}
