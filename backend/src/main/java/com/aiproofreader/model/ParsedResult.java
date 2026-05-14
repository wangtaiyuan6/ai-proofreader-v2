package com.aiproofreader.model;

import java.util.List;

public class ParsedResult {
    private String thinking;
    private String correction;
    private List<Change> changes;
    private boolean thinkingDone;
    private boolean correctionDone;
    private boolean changesDone;

    public ParsedResult() {}

    public ParsedResult(String thinking, String correction, List<Change> changes,
                        boolean thinkingDone, boolean correctionDone, boolean changesDone) {
        this.thinking = thinking;
        this.correction = correction;
        this.changes = changes;
        this.thinkingDone = thinkingDone;
        this.correctionDone = correctionDone;
        this.changesDone = changesDone;
    }

    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }

    public String getCorrection() { return correction; }
    public void setCorrection(String correction) { this.correction = correction; }

    public List<Change> getChanges() { return changes; }
    public void setChanges(List<Change> changes) { this.changes = changes; }

    public boolean isThinkingDone() { return thinkingDone; }
    public void setThinkingDone(boolean thinkingDone) { this.thinkingDone = thinkingDone; }

    public boolean isCorrectionDone() { return correctionDone; }
    public void setCorrectionDone(boolean correctionDone) { this.correctionDone = correctionDone; }

    public boolean isChangesDone() { return changesDone; }
    public void setChangesDone(boolean changesDone) { this.changesDone = changesDone; }
}
