package teammates.common.datatransfer;

public class FeedbackSessionResultParameters {
    public boolean isIncludeResposeStatus, inSection, fromSection, toSection;
    public String questionId, section, range, viewType;

    public FeedbackSessionResultParameters() {
        setAllBooleanParametersToFalse();
    }

    public FeedbackSessionResultParameters(String questionId) {
        this.questionId = questionId;
        setAllBooleanParametersToFalse();
    }

    public void setAllBooleanParametersToFalse(){
        isIncludeResposeStatus = false;
        inSection = false;
        fromSection = false;
        toSection = false;
    }

    public void setIncludeResposeStatusTrue(){
        isIncludeResposeStatus = true;
    }

    public void addInSection(String section){
        this.section = section;
        setInSectionTrue();
    }

    public void addFromSection(String section){
        this.section = section;
        setFromSectionTrue();
    }

    public void addToSection(String section){
        this.section = section;
        setToSectionTrue();
    }

    private void setInSectionTrue(){
        inSection = true;
    }

    private void setFromSectionTrue(){
        fromSection = true;
    }

    private void setToSectionTrue(){
        toSection = true;
    }

    public void setRange(int range){
        if (range > 0) {
            this.range = String.valueOf(range);
        }
    }

    public void setViewType(String viewType){
        this.viewType = viewType;
    }

}

