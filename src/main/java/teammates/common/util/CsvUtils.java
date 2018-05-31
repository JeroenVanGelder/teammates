package teammates.common.util;

import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.FeedbackSessionResultsBundle;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseCommentAttributes;
import teammates.common.datatransfer.questions.FeedbackQuestionDetails;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsvUtils {

    private static final CsvUtils instance = new CsvUtils();

    private CsvUtils(){}

    public static CsvUtils getCsvUtils(){
        return instance;
    }

    public String getFeedbackSessionResultsSummaryInSectionAsCsv(FeedbackSessionResultsBundle resultsBundle){

        StringBuilder exportBuilder = createStringBuilderWithCourseSessionHeader(resultsBundle);

        String body = fillFeedbackResultsForQuestionsWithEntrySet(resultsBundle);

        exportBuilder.append(body);

        return exportBuilder.toString();
    }

    private StringBuilder createStringBuilderWithCourseSessionHeader(FeedbackSessionResultsBundle resultsBundle) {
        StringBuilder exportBuilder = new StringBuilder(100);
        exportBuilder.append(createHeader(resultsBundle));
        return exportBuilder;
    }

    private String createHeader(FeedbackSessionResultsBundle results) {
        return getCourseIdInSanitizedCsv(results) +
                getSessionNameInSanitizedCsv(results) +
                getSectionNameInSanitizedCsv(results) +
                System.lineSeparator() + System.lineSeparator();
    }

    private String getCourseIdInSanitizedCsv(FeedbackSessionResultsBundle results) {
        return String.format("Course,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getCourseId())) +
                System.lineSeparator();
    }

    private String getSessionNameInSanitizedCsv(FeedbackSessionResultsBundle results) {
        return String.format("Session Name,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getFeedbackSessionName())) +
                System.lineSeparator();
    }

    private String getSectionNameInSanitizedCsv(FeedbackSessionResultsBundle results) {
        StringBuilder exportBuilder = new StringBuilder(100);
        if (results.section != null) {
            exportBuilder.append(String.format("Section Name,%s", SanitizationHelper.sanitizeForCsv(results.section)))
                    .append(System.lineSeparator());
        }
        return exportBuilder.toString();
    }

    private String fillFeedbackResultsForQuestionsWithEntrySet(FeedbackSessionResultsBundle results) {
        StringBuilder exportBuilder =  new StringBuilder(100);
        Set<Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>>> entrySet =
                results.getQuestionResponseMap().entrySet();
        for (Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry : entrySet) {
            exportBuilder.append(getFeedbackSessionResultsForQuestionInCsvFormat(results, entry));
        }
        return exportBuilder.toString();
    }

    private String getFeedbackSessionResultsForQuestionInCsvFormat(
            FeedbackSessionResultsBundle fsrBundle,
            Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry){

        FeedbackSessionResultData feedbackSessionResultData = new FeedbackSessionResultData(fsrBundle, entry);

        StringBuilder exportBuilder = getPreparedStringBuilderForFeedbackSessionResult(feedbackSessionResultData);

        exportBuilder.append(getAllResponseDetailRowsInCsv(feedbackSessionResultData));

        return exportBuilder.toString();
    }

    private StringBuilder getPreparedStringBuilderForFeedbackSessionResult(FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();
        exportBuilder.append(createQuestionResultsHeader(feedbackSessionResultData));
        exportBuilder.append(feedbackSessionResultData.getDetailedResponsesHeaderInCsv());
        return exportBuilder;
    }

    private String createQuestionResultsHeader(FeedbackSessionResultData feedbackSessionResultData) {
        return getquestionHeader(feedbackSessionResultData) +
                getRequiredQuestionStatistics(feedbackSessionResultData);
    }

    private String getRequiredQuestionStatistics(FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();
        String statistics = feedbackSessionResultData.getQuestionStatisticsSummary();

        if (isRequiredToShowStatistics(feedbackSessionResultData, statistics)) {
            exportBuilder.append("Summary Statistics,").append(System.lineSeparator());
            exportBuilder.append(statistics).append(System.lineSeparator());
        }

        return exportBuilder.toString();
    }

    private boolean isRequiredToShowStatistics(FeedbackSessionResultData feedbackSessionResultData, String statistics) {
        return !statistics.isEmpty() && feedbackSessionResultData.resultsBundle.isStatsShown;
    }

    private String getquestionHeader(FeedbackSessionResultData feedbackSessionResultData) {
        return "Question " +
                Integer.toString(feedbackSessionResultData.question.questionNumber) +
                "," +
                SanitizationHelper.sanitizeForCsv(feedbackSessionResultData.getQuestionText()) +
                System.lineSeparator() +
                System.lineSeparator();
    }

    private String getAllResponseDetailRowsInCsv(FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();

        clearAllListDataWhenVisible(feedbackSessionResultData);

        for (FeedbackResponseAttributes response : feedbackSessionResultData.allResponses) {
            exportBuilder.append(getResponseDetailsInCsvForSingleResponse(feedbackSessionResultData, response));
        }

        if (feedbackSessionResultData.isMissingResponsesShown()) {
            exportBuilder.append(getRemainingRowsInCsvFormat(feedbackSessionResultData));
        }

        exportBuilder.append(System.lineSeparator()).append(System.lineSeparator());

        return exportBuilder.toString();
    }

    private void clearAllListDataWhenVisible(FeedbackSessionResultData feedbackSessionResultData) {
        if (feedbackSessionResultData.isResponseListVisible()) {
            feedbackSessionResultData.clearAllListData();
        }
    }


    private String getResponseDetailsInCsvForSingleResponse(FeedbackSessionResultData feedbackSessionResultData, FeedbackResponseAttributes response) {
        StringBuilder exportBuilder = new StringBuilder();

        if (!response.giver.equals(feedbackSessionResultData.prevGiver) && feedbackSessionResultData.isMissingResponsesShown()) {
            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(feedbackSessionResultData, feedbackSessionResultData.prevGiver));

            String giverIdentifier = getGiverIdentifier(feedbackSessionResultData, response);

            feedbackSessionResultData.updatePossibleRecipientsForGiverList(giverIdentifier);
        }

        feedbackSessionResultData.removeUsedResponseIdFromParticipantIdentifierList(response);

        exportBuilder.append(feedbackSessionResultData.getDetailedResponsesRowInCsv(response));

        return exportBuilder.toString();
    }

    private String getGiverIdentifier(FeedbackSessionResultData feedbackSessionResultData, FeedbackResponseAttributes response) {
        return feedbackSessionResultData.isQuestionGiverTypeTeams()
                        ? feedbackSessionResultData.getResultsBundleFullNameFromRoster(response)
                        : response.giver;
    }

    private String getRemainingRowsInCsvFormat(FeedbackSessionResultData feedbackSessionResultData) {

        StringBuilder exportBuilder = new StringBuilder();

        if (feedbackSessionResultData.possibleRecipientsForGiver != null) {
            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(feedbackSessionResultData, feedbackSessionResultData.prevGiver));
        }

        removeParticipantIdentifierFromList(feedbackSessionResultData.possibleGiversWithoutResponses, feedbackSessionResultData.prevGiver);

        for (String possibleGiverWithNoResponses : feedbackSessionResultData.possibleGiversWithoutResponses) {
            feedbackSessionResultData.updatePossibleRecipientsForGiverList(possibleGiverWithNoResponses);
            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(feedbackSessionResultData,
                    possibleGiverWithNoResponses));
        }

        return exportBuilder.toString();
    }

    private void removeParticipantIdentifierFromList(List<String> participantIdentifierList, String participantIdentifier) {
            participantIdentifierList.remove(participantIdentifier);
    }

    private StringBuilder getRowsOfPossibleRecipientsInCsvFormat(FeedbackSessionResultData feedbackSessionResultData, String giver) {

        StringBuilder exportBuilder = new StringBuilder();
        for (String possibleRecipient : feedbackSessionResultData.possibleRecipientsForGiver) {
            if (feedbackSessionResultData.questionDetails.shouldShowNoResponseText(feedbackSessionResultData.question)) {
                exportBuilder.append(sanitizeResultsForNoResponse(feedbackSessionResultData.resultsBundle, giver, possibleRecipient));
                exportBuilder.append("," + getNoResponseTextInCsv(feedbackSessionResultData.questionDetails)
                        + System.lineSeparator());
            }
        }
        return exportBuilder;
    }

    private String sanitizeResultsForNoResponse(FeedbackSessionResultsBundle results, String giver, String possibleRecipient) {
        return (SanitizationHelper.sanitizeForCsv(results.getTeamNameFromRoster(giver))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getFullNameFromRoster(giver)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getLastNameFromRoster(giver)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getDisplayableEmailFromRoster(giver)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getTeamNameFromRoster(possibleRecipient)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getFullNameFromRoster(possibleRecipient)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getLastNameFromRoster(possibleRecipient)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getDisplayableEmailFromRoster(possibleRecipient))));
    }

    private String getNoResponseTextInCsv(FeedbackQuestionDetails questionDetails) {
        return questionDetails.getNoResponseTextInCsv();
    }

    private class FeedbackSessionResultData {
        private FeedbackSessionResultsBundle resultsBundle;
        private FeedbackQuestionAttributes question;
        private FeedbackQuestionDetails questionDetails;
        private List<String> possibleGiversWithoutResponses;
        private List<String> possibleRecipientsForGiver;
        private String prevGiver;
        private List<FeedbackResponseAttributes> allResponses;
        private int maxNumOfResponseComments;

        private FeedbackSessionResultData(FeedbackSessionResultsBundle fsrBundle, Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry) {
            resultsBundle = fsrBundle;
            question = entry.getKey();
            this.possibleGiversWithoutResponses = fsrBundle.getPossibleGiversInSection(question, fsrBundle.section);
            this.possibleRecipientsForGiver = new ArrayList<>();
            prevGiver = "";
            allResponses = entry.getValue();
            maxNumOfResponseComments = getMaxNumberOfResponseComments(fsrBundle.getResponseComments());
            questionDetails = question.getQuestionDetails();
        }

        private int getMaxNumberOfResponseComments(Map<String, List<FeedbackResponseCommentAttributes>> responseComments) {
            int maxCommentsNum = 0;
            if (!checkIfResponseIsEmpty(allResponses)) {
                for (FeedbackResponseAttributes response : allResponses) {
                    maxCommentsNum = resizeMaxCommentsIfCommentsIsLarger(responseComments, maxCommentsNum, response);
                }
            }
            return maxCommentsNum;
        }

        private boolean checkIfResponseIsEmpty(List<FeedbackResponseAttributes> allResponses){
            if (allResponses == null || allResponses.isEmpty()) {
                return true;
            }
            return false;
        }

        private int resizeMaxCommentsIfCommentsIsLarger(Map<String, List<FeedbackResponseCommentAttributes>> responseComments, int maxCommentsNum, FeedbackResponseAttributes response) {
            List<FeedbackResponseCommentAttributes> commentAttributes = responseComments.get(response.getId());
            if (commentAttributes != null && maxCommentsNum < commentAttributes.size()) {
                maxCommentsNum = commentAttributes.size();
            }
            return maxCommentsNum;
        }

        private String getDetailedResponsesHeaderInCsv() {
            return questionDetails.getCsvDetailedResponsesHeader(maxNumOfResponseComments);
        }

        private boolean isMissingResponsesShown() {
            return resultsBundle.isMissingResponsesShown;
        }

        private boolean isResponseListVisible() {
            return resultsBundle.isResponseListVisible(allResponses);
        }

        private void clearAllListData() {
            possibleGiversWithoutResponses.clear();
            possibleRecipientsForGiver.clear();
        }

        private String getResultsBundleFullNameFromRoster(FeedbackResponseAttributes response) {
            return resultsBundle.getFullNameFromRoster(response.giver);
        }

        private boolean isQuestionGiverTypeTeams() {
            return question.giverType == FeedbackParticipantType.TEAMS;
        }

        private List<String> getPossibleRecipients(String giverIdentifier) {
            return resultsBundle.getPossibleRecipients(question, giverIdentifier);
        }

        private String getQuestionStatisticsSummary() {
            FeedbackQuestionDetails questionDetails = question.getQuestionDetails();
            return questionDetails.getQuestionResultStatisticsCsv(allResponses,question,resultsBundle);
        }

        private String getQuestionText() {
            return questionDetails.getQuestionText();
        }

        private void updatePossibleRecipientsForGiverList(String possibleGiverWithNoResponses) {
            possibleRecipientsForGiver = getPossibleRecipients(possibleGiverWithNoResponses);
        }

        private String getDetailedResponsesRowInCsv(FeedbackResponseAttributes response) {
            return questionDetails.getCsvDetailedResponsesRow(resultsBundle, response, question, containsKey(response));
        }

        private boolean containsKey(FeedbackResponseAttributes response) {
            return resultsBundle.responseComments.containsKey(response.getId());
        }

        private void removeUsedResponseIdFromParticipantIdentifierList(FeedbackResponseAttributes response) {
            prevGiver = response.giver;
            removeParticipantIdentifierFromList(possibleGiversWithoutResponses, response.giver);
            removeParticipantIdentifierFromList(possibleRecipientsForGiver, response.recipient);
        }
    }
}
