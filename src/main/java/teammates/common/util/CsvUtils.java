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

        StringBuilder exportBuilder = new StringBuilder(100);

        String header = createHeader(resultsBundle);
        exportBuilder.append(header);

        String body = fillFeedbackResultsForQuestionsWithEntrySet(resultsBundle);
        exportBuilder.append(body);

        return exportBuilder.toString();
    }

    private String createHeader(FeedbackSessionResultsBundle results) {
        StringBuilder exportBuilder = new StringBuilder(100);

        exportBuilder.append(String.format("Course,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getCourseId())))
                .append(System.lineSeparator());

        exportBuilder.append(String.format("Session Name,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getFeedbackSessionName())))
                .append(System.lineSeparator());

        if (results.section != null) {
            exportBuilder.append(String.format("Section Name,%s", SanitizationHelper.sanitizeForCsv(results.section)))
                    .append(System.lineSeparator());
        }

        exportBuilder.append(System.lineSeparator()).append(System.lineSeparator());

        return exportBuilder.toString();
    }

    private String fillFeedbackResultsForQuestionsWithEntrySet(FeedbackSessionResultsBundle results) {
        StringBuilder exportBuilder =  new StringBuilder(100);

        Set<Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>>> entrySet =
                results.getQuestionResponseMap().entrySet();

        for (Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry : entrySet) {
            String feedbackSessionResultForQuestion = getFeedbackSessionResultsForQuestionInCsvFormat(results, entry);
            exportBuilder.append(feedbackSessionResultForQuestion);
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

    private String getAllResponseDetailRowsInCsv(FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();

        clearAllListDataWhenVisible(feedbackSessionResultData);

        for (FeedbackResponseAttributes response : feedbackSessionResultData.allResponses) {
            exportBuilder.append(getResponseDetailsInCsvForSingleResponse(feedbackSessionResultData, response));
        }

        if (feedbackSessionResultData.resultsBundle.isMissingResponsesShown) {
            exportBuilder.append(getRemainingRowsInCsvFormat(feedbackSessionResultData));
        }

        exportBuilder.append(System.lineSeparator() + System.lineSeparator());

        return exportBuilder.toString();
    }

    private StringBuilder getPreparedStringBuilderForFeedbackSessionResult(FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();
        exportBuilder.append(createQuestionResultsHeader(feedbackSessionResultData));
        exportBuilder.append(feedbackSessionResultData.getDetailedResponsesHeaderInCsv());
        return exportBuilder;
    }


    private String getResponseDetailsInCsvForSingleResponse(FeedbackSessionResultData feedbackSessionResultData, FeedbackResponseAttributes response) {
        StringBuilder exportBuilder = new StringBuilder();

        if (!response.giver.equals(feedbackSessionResultData.prevGiver) && feedbackSessionResultData.resultsBundle.isMissingResponsesShown) {
            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(feedbackSessionResultData, feedbackSessionResultData.prevGiver));

            String giverIdentifier = feedbackSessionResultData.question.giverType == FeedbackParticipantType.TEAMS
                    ? feedbackSessionResultData.resultsBundle.getFullNameFromRoster(response.giver)
                    : response.giver;

            feedbackSessionResultData.possibleRecipientsForGiver = feedbackSessionResultData.resultsBundle.getPossibleRecipients(feedbackSessionResultData.question, giverIdentifier);
        }

        boolean hasCommentsForResponses = feedbackSessionResultData.resultsBundle.responseComments.containsKey(response.getId());

        feedbackSessionResultData.prevGiver = response.giver;
        removeParticipantIdentifierFromList(feedbackSessionResultData.possibleGiversWithoutResponses,
                response.giver);
        removeParticipantIdentifierFromList(feedbackSessionResultData.possibleRecipientsForGiver,
                response.recipient);

        exportBuilder.append(feedbackSessionResultData.questionDetails.getCsvDetailedResponsesRow(feedbackSessionResultData.resultsBundle, response, feedbackSessionResultData.question,
                hasCommentsForResponses));
        return exportBuilder.toString();
    }

    private void clearAllListDataWhenVisible(FeedbackSessionResultData feedbackSessionResultData) {
        if (feedbackSessionResultData.resultsBundle.isResponseListVisible(feedbackSessionResultData.allResponses)) {
            feedbackSessionResultData.possibleGiversWithoutResponses.clear();
            feedbackSessionResultData.possibleRecipientsForGiver.clear();
        }
    }

    private String createQuestionResultsHeader(FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();
        FeedbackQuestionDetails questionDetails = feedbackSessionResultData.question.getQuestionDetails();

        exportBuilder.append("Question " + Integer.toString(feedbackSessionResultData.question.questionNumber) + ","
                + SanitizationHelper.sanitizeForCsv(questionDetails.getQuestionText())
                + System.lineSeparator() + System.lineSeparator());

        String statistics = questionDetails.getQuestionResultStatisticsCsv(feedbackSessionResultData.allResponses,
                feedbackSessionResultData.question, feedbackSessionResultData.resultsBundle);

        if (!statistics.isEmpty() && feedbackSessionResultData.resultsBundle.isStatsShown) {
            exportBuilder.append("Summary Statistics,").append(System.lineSeparator());
            exportBuilder.append(statistics).append(System.lineSeparator());
        }

        return exportBuilder.toString();
    }

    private String getRemainingRowsInCsvFormat(FeedbackSessionResultData feedbackSessionResultData) {

        StringBuilder exportBuilder = new StringBuilder();

        if (feedbackSessionResultData.possibleRecipientsForGiver != null) {
            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(feedbackSessionResultData, feedbackSessionResultData.prevGiver));

        }

        removeParticipantIdentifierFromList(feedbackSessionResultData.possibleGiversWithoutResponses, feedbackSessionResultData.prevGiver);

        for (String possibleGiverWithNoResponses : feedbackSessionResultData.possibleGiversWithoutResponses) {
            feedbackSessionResultData.possibleRecipientsForGiver=feedbackSessionResultData.resultsBundle.getPossibleRecipients(feedbackSessionResultData.question, possibleGiverWithNoResponses);
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
        StringBuilder exportBuilder = new StringBuilder();
        exportBuilder.append(SanitizationHelper.sanitizeForCsv(results.getTeamNameFromRoster(giver))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getFullNameFromRoster(giver)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getLastNameFromRoster(giver)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getDisplayableEmailFromRoster(giver)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getTeamNameFromRoster(possibleRecipient)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getFullNameFromRoster(possibleRecipient)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getLastNameFromRoster(possibleRecipient)))
                + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(results.getDisplayableEmailFromRoster(possibleRecipient))));

        return exportBuilder.toString();
    }

    private String getNoResponseTextInCsv(FeedbackQuestionDetails questionDetails) {
        return questionDetails.getNoResponseTextInCsv();
    }

    private int getMaxNumberOfResponseComments(List<FeedbackResponseAttributes> allResponses,
            Map<String, List<FeedbackResponseCommentAttributes>> responseComments) {
        int maxCommentsNum = 0;

        boolean isEmptyResponse = checkIfResponseIsEmpty(allResponses);
        if (!isEmptyResponse) {
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
            maxNumOfResponseComments = getMaxNumberOfResponseComments(allResponses, fsrBundle.getResponseComments());
            questionDetails = question.getQuestionDetails();
        }

        private String getDetailedResponsesHeaderInCsv() {
            return questionDetails.getCsvDetailedResponsesHeader(maxNumOfResponseComments);
        }
    }
}
