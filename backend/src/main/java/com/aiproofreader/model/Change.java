package com.aiproofreader.model;

public class Change {
    private String original;
    private String corrected;
    private String reason;

    public Change() {}

    public Change(String original, String corrected, String reason) {
        this.original = original;
        this.corrected = corrected;
        this.reason = reason;
    }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getCorrected() { return corrected; }
    public void setCorrected(String corrected) { this.corrected = corrected; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
