package teammates.common.datatransfer;

public class FeedbackSessionIdentification {
    public final String feedbackSessionName;
    public final String courseId;
    public final String userEmail;
    public final String section;
    public final String questionId;
    public boolean isMissingResponsesShown;
    public boolean isStatsShown;

    public FeedbackSessionIdentification(String feedbackSessionName, String courseId, String userEmail, String section, String questionId, boolean isMissingResponsesShown, boolean isStatsShown) {
        this.feedbackSessionName = feedbackSessionName;
        this.courseId = courseId;
        this.userEmail = userEmail;
        this.section = section;
        this.questionId = questionId;
        this.isMissingResponsesShown = isMissingResponsesShown;
        this.isStatsShown = isStatsShown;
    }

    public String getFeedbackSessionName() {
        return feedbackSessionName;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getSection() {
        return section;
    }

    public String getQuestionId() {
        return questionId;
    }
}
