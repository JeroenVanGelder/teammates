package teammates.common.datatransfer;

public class FeedbackSessionIdentification {
    public final String feedbackSessionName;
    public final String courseId;
    public final String userEmail;
    public final String section;
    public final String questionId;

    public FeedbackSessionIdentification(String feedbackSessionName, String courseId, String userEmail, String section, String questionId) {
        this.feedbackSessionName = feedbackSessionName;
        this.courseId = courseId;
        this.userEmail = userEmail;
        this.section = section;
        this.questionId = questionId;
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
