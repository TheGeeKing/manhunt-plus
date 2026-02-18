package net.tutla.manhuntPlus.domain;

public final class MatchSettings {
    private int maxDurationMinutes;
    private int hunterReleaseSeconds;
    private boolean teamCompassEnabled;
    private final boolean autoCalibrationEnabled;
    private final int autoCalibrationIntervalSeconds;
    private final boolean broadcastTime;
    private final int broadcastTimeEverySeconds;
    private final double surroundRadius;

    public MatchSettings(
            int maxDurationMinutes,
            int hunterReleaseSeconds,
            boolean teamCompassEnabled,
            boolean autoCalibrationEnabled,
            int autoCalibrationIntervalSeconds,
            boolean broadcastTime,
            int broadcastTimeEverySeconds,
            double surroundRadius
    ) {
        this.maxDurationMinutes = Math.max(0, maxDurationMinutes);
        this.hunterReleaseSeconds = Math.max(0, hunterReleaseSeconds);
        this.teamCompassEnabled = teamCompassEnabled;
        this.autoCalibrationEnabled = autoCalibrationEnabled;
        this.autoCalibrationIntervalSeconds = Math.max(1, autoCalibrationIntervalSeconds);
        this.broadcastTime = broadcastTime;
        this.broadcastTimeEverySeconds = Math.max(1, broadcastTimeEverySeconds);
        this.surroundRadius = Math.max(0.1d, surroundRadius);
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(int maxDurationMinutes) {
        this.maxDurationMinutes = Math.max(0, maxDurationMinutes);
    }

    public int getHunterReleaseSeconds() {
        return hunterReleaseSeconds;
    }

    public void setHunterReleaseSeconds(int hunterReleaseSeconds) {
        this.hunterReleaseSeconds = Math.max(0, hunterReleaseSeconds);
    }

    public boolean isTeamCompassEnabled() {
        return teamCompassEnabled;
    }

    public void setTeamCompassEnabled(boolean teamCompassEnabled) {
        this.teamCompassEnabled = teamCompassEnabled;
    }

    public boolean isAutoCalibrationEnabled() {
        return autoCalibrationEnabled;
    }

    public int getAutoCalibrationIntervalSeconds() {
        return autoCalibrationIntervalSeconds;
    }

    public boolean isBroadcastTime() {
        return broadcastTime;
    }

    public int getBroadcastTimeEverySeconds() {
        return broadcastTimeEverySeconds;
    }

    public double getSurroundRadius() {
        return surroundRadius;
    }
}
