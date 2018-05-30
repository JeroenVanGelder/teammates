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

        StringBuilder exportBuilder = new StringBuilder();

        FeedbackSessionResultData feedbackSessionResultData = new FeedbackSessionResultData(fsrBundle, entry);

        exportBuilder.append(createQuestionResultsHeader(fsrBundle, feedbackSessionResultData));
        exportBuilder.append(feedbackSessionResultData.questionDetails.getCsvDetailedResponsesHeader(feedbackSessionResultData.maxNumOfResponseComments));

        if (fsrBundle.isResponseListVisible(feedbackSessionResultData.allResponses)) {
            feedbackSessionResultData.possibleGiversWithoutResponses.clear();
            feedbackSessionResultData.possibleRecipientsForGiver.clear();
        }

        for (FeedbackResponseAttributes response : feedbackSessionResultData.allResponses) {

            // print missing responses from the current giver
            if (!response.giver.equals(feedbackSessionResultData.prevGiver) && fsrBundle.isMissingResponsesShown) {
                exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(fsrBundle,
                        feedbackSessionResultData.question, feedbackSessionResultData.questionDetails,
                        feedbackSessionResultData.possibleRecipientsForGiver, feedbackSessionResultData.prevGiver));
                String giverIdentifier = feedbackSessionResultData.question.giverType == FeedbackParticipantType.TEAMS
                        ? fsrBundle.getFullNameFromRoster(response.giver)
                        : response.giver;

                feedbackSessionResultData.possibleRecipientsForGiver = fsrBundle.getPossibleRecipients(feedbackSessionResultData.question, giverIdentifier);
            }


            // do not show all possible givers and recipients if there are anonymous givers and recipients
            boolean hasCommentsForResponses = fsrBundle.responseComments.containsKey(response.getId());

            feedbackSessionResultData.prevGiver = response.giver;
            removeParticipantIdentifierFromList(feedbackSessionResultData.possibleGiversWithoutResponses,
                    response.giver);
            removeParticipantIdentifierFromList(feedbackSessionResultData.possibleRecipientsForGiver,
                    response.recipient);

            exportBuilder.append(feedbackSessionResultData.questionDetails.getCsvDetailedResponsesRow(fsrBundle, response, feedbackSessionResultData.question,
                    hasCommentsForResponses));

        }

        // add the rows for the possible givers and recipients who have missing responses
        if (fsrBundle.isMissingResponsesShown) {
            exportBuilder.append(
                    getRemainingRowsInCsvFormat(
                            fsrBundle, entry, feedbackSessionResultData.question, feedbackSessionResultData.questionDetails,
                            feedbackSessionResultData.possibleGiversWithoutResponses, feedbackSessionResultData.possibleRecipientsForGiver, feedbackSessionResultData.prevGiver));
        }

        exportBuilder.append(System.lineSeparator() + System.lineSeparator());

        return exportBuilder.toString();
    }

    private String createQuestionResultsHeader(FeedbackSessionResultsBundle fsrBundle, FeedbackSessionResultData feedbackSessionResultData) {
        StringBuilder exportBuilder = new StringBuilder();
        FeedbackQuestionDetails questionDetails = feedbackSessionResultData.question.getQuestionDetails();

        exportBuilder.append("Question " + Integer.toString(feedbackSessionResultData.question.questionNumber) + ","
                + SanitizationHelper.sanitizeForCsv(questionDetails.getQuestionText())
                + System.lineSeparator() + System.lineSeparator());

        String statistics = questionDetails.getQuestionResultStatisticsCsv(feedbackSessionResultData.allResponses,
                feedbackSessionResultData.question, fsrBundle);

        if (!statistics.isEmpty() && fsrBundle.isStatsShown) {
            exportBuilder.append("Summary Statistics,").append(System.lineSeparator());
            exportBuilder.append(statistics).append(System.lineSeparator());
        }

        return exportBuilder.toString();
    }

    /**
     * Generate rows of missing responses for the remaining possible givers and recipients.
     *
     * <p>If for the prevGiver, possibleRecipientsForGiver is not empty,
     * the remaining missing responses for the prevGiver will be generated first.
     * @return the remaining rows of missing responses in csv format
     * @param results
     * @param entry
     * @param question
     * @param questionDetails
     * @param remainingPossibleGivers
     * @param possibleRecipientsForGiver
     * @param prevGiver
     */
    private String getRemainingRowsInCsvFormat(
            FeedbackSessionResultsBundle results,
            Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry,
            FeedbackQuestionAttributes question,
            FeedbackQuestionDetails questionDetails,
            List<String> remainingPossibleGivers,
            List<String> possibleRecipientsForGiver, String prevGiver) {
        StringBuilder exportBuilder = new StringBuilder();

        if (possibleRecipientsForGiver != null) {
            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(results,
                    question, questionDetails, possibleRecipientsForGiver,
                    prevGiver));

        }

        removeParticipantIdentifierFromList(remainingPossibleGivers, prevGiver);

        for (String possibleGiverWithNoResponses : remainingPossibleGivers) {
            List<String> possibleRecipientsForRemainingGiver =
                    results.getPossibleRecipients(entry.getKey(), possibleGiverWithNoResponses);

            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(results,
                    question, questionDetails, possibleRecipientsForRemainingGiver,
                    possibleGiverWithNoResponses));
        }

        return exportBuilder.toString();
    }

    /**
     * Given a participantIdentifier, remove it from participantIdentifierList.
     *
     * <p>Before removal, {@link FeedbackSessionResultsBundle#getFullNameFromRoster} is used to
     * convert the identifier into a canonical form if the participantIdentifierType is TEAMS.
     * @param participantIdentifierList
     * @param participantIdentifier
     */
    private void removeParticipantIdentifierFromList(List<String> participantIdentifierList, String participantIdentifier) {
            participantIdentifierList.remove(participantIdentifier);
    }

    /**
     * For a giver and a list of possibleRecipientsForGiver, generate rows
     * of missing responses between the giver and the possible recipients.
     * @param results
     * @param question
     * @param questionDetails
     * @param possibleRecipientsForGiver
     * @param giver
     */
    private StringBuilder getRowsOfPossibleRecipientsInCsvFormat(
            FeedbackSessionResultsBundle results,
            FeedbackQuestionAttributes question,
            FeedbackQuestionDetails questionDetails,
            List<String> possibleRecipientsForGiver, String giver) {

        StringBuilder exportBuilder = new StringBuilder();
        for (String possibleRecipient : possibleRecipientsForGiver) {
            if (questionDetails.shouldShowNoResponseText(question)) {
                exportBuilder.append(sanitizeResultsForNoResponse(results, giver, possibleRecipient));
                exportBuilder.append("," + getNoResponseTextInCsv(questionDetails)
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
        FeedbackQuestionAttributes question;
        FeedbackQuestionDetails questionDetails;
        public List<String> possibleGiversWithoutResponses;
        public List<String> possibleRecipientsForGiver;
        public String prevGiver;
        List<FeedbackResponseAttributes> allResponses;
        int maxNumOfResponseComments;

        public FeedbackSessionResultData(FeedbackSessionResultsBundle fsrBundle, Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry) {
            question = entry.getKey();
            this.possibleGiversWithoutResponses = fsrBundle.getPossibleGiversInSection(question, fsrBundle.section);
            this.possibleRecipientsForGiver = new ArrayList<>();
            prevGiver = "";
            allResponses = entry.getValue();
            maxNumOfResponseComments = getMaxNumberOfResponseComments(allResponses, fsrBundle.getResponseComments());
            questionDetails = question.getQuestionDetails();
        }

        public List<String> getPossibleGiversWithoutResponses() {
            return possibleGiversWithoutResponses;
        }

        public List<String> getPossibleRecipientsForGiver() {
            return possibleRecipientsForGiver;
        }
    }
}
