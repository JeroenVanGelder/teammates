package teammates.common.util;

import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.FeedbackSessionResultsBundle;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseCommentAttributes;
import teammates.common.datatransfer.FeedbackSessionIdentification;
import teammates.common.datatransfer.questions.FeedbackQuestionDetails;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.ExceedingRangeException;
import teammates.logic.core.FeedbackSessionsLogic;

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

    public String getFeedbackSessionResultsSummaryInSectionAsCsv(
            FeedbackSessionIdentification feedbackSessionIdentification, FeedbackSessionResultsBundle resultsBundle){

        StringBuilder exportBuilder = new StringBuilder(100);

        String header = createHeader(resultsBundle, feedbackSessionIdentification);
        exportBuilder.append(header);

        String body = fillFeedbackResultsForQuestionsWithEntrySet(feedbackSessionIdentification, resultsBundle);
        exportBuilder.append(body);

        return exportBuilder.toString();
    }

    private String createHeader(FeedbackSessionResultsBundle results, FeedbackSessionIdentification feedbackSessionIdentification) {
        StringBuilder exportBuilder = new StringBuilder(100);

        exportBuilder.append(String.format("Course,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getCourseId())))
                .append(System.lineSeparator());

        exportBuilder.append(String.format("Session Name,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getFeedbackSessionName())))
                .append(System.lineSeparator());

        if (feedbackSessionIdentification.getSection() != null) {
            exportBuilder.append(String.format("Section Name,%s", SanitizationHelper.sanitizeForCsv(feedbackSessionIdentification.getSection())))
                    .append(System.lineSeparator());
        }

        exportBuilder.append(System.lineSeparator()).append(System.lineSeparator());

        return exportBuilder.toString();
    }

    private String fillFeedbackResultsForQuestionsWithEntrySet(FeedbackSessionIdentification feedbackSessionIdentification, FeedbackSessionResultsBundle results) {
        StringBuilder exportBuilder =  new StringBuilder(100);

        Set<Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>>> entrySet =
                results.getQuestionResponseMap().entrySet();

        for (Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry : entrySet) {
            String feedbackSessionResultForQuestion = getFeedbackSessionResultsForQuestionInCsvFormat(results, entry, feedbackSessionIdentification.isMissingResponsesShown, feedbackSessionIdentification.isStatsShown, feedbackSessionIdentification.getSection());
            exportBuilder.append(feedbackSessionResultForQuestion);
        }
        return exportBuilder.toString();
    }

    private String getFeedbackSessionResultsForQuestionInCsvFormat(
            FeedbackSessionResultsBundle fsrBundle,
            Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry,
            boolean isMissingResponsesShown, boolean isStatsShown, String section) {

        FeedbackQuestionAttributes question = entry.getKey();
        FeedbackQuestionDetails questionDetails = question.getQuestionDetails();
        List<FeedbackResponseAttributes> allResponses = entry.getValue();

        StringBuilder exportBuilder = new StringBuilder();

        exportBuilder.append("Question " + Integer.toString(question.questionNumber) + ","
                + SanitizationHelper.sanitizeForCsv(questionDetails.getQuestionText())
                + System.lineSeparator() + System.lineSeparator());

        String statistics = questionDetails.getQuestionResultStatisticsCsv(allResponses,
                question, fsrBundle);
        if (!statistics.isEmpty() && isStatsShown) {
            exportBuilder.append("Summary Statistics,").append(System.lineSeparator());
            exportBuilder.append(statistics).append(System.lineSeparator());
        }

        List<String> possibleGiversWithoutResponses = fsrBundle.getPossibleGiversInSection(question, section);
        List<String> possibleRecipientsForGiver = new ArrayList<>();
        String prevGiver = "";

        int maxNumOfResponseComments = getMaxNumberOfResponseComments(allResponses, fsrBundle.getResponseComments());
        exportBuilder.append(questionDetails.getCsvDetailedResponsesHeader(maxNumOfResponseComments));

        for (FeedbackResponseAttributes response : allResponses) {

            if (!fsrBundle.isRecipientVisible(response) || !fsrBundle.isGiverVisible(response)) {
                possibleGiversWithoutResponses.clear();
                possibleRecipientsForGiver.clear();
            }

            // keep track of possible recipients with no responses
            removeParticipantIdentifierFromList(question.giverType,
                    possibleGiversWithoutResponses, response.giver, fsrBundle);

            boolean isNewGiver = !prevGiver.equals(response.giver);
            // print missing responses from the current giver
            if (isNewGiver && isMissingResponsesShown) {
                exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(fsrBundle,
                        question, questionDetails,
                        possibleRecipientsForGiver, prevGiver));
                String giverIdentifier = question.giverType == FeedbackParticipantType.TEAMS
                        ? fsrBundle.getFullNameFromRoster(response.giver)
                        : response.giver;

                possibleRecipientsForGiver = fsrBundle.getPossibleRecipients(question, giverIdentifier);
            }

            removeParticipantIdentifierFromList(question.recipientType, possibleRecipientsForGiver,
                    response.recipient, fsrBundle);
            prevGiver = response.giver;

            // do not show all possible givers and recipients if there are anonymous givers and recipients
            boolean hasCommentsForResponses = fsrBundle.responseComments.containsKey(response.getId());

            exportBuilder.append(questionDetails.getCsvDetailedResponsesRow(fsrBundle, response, question,
                    hasCommentsForResponses));
        }

        // add the rows for the possible givers and recipients who have missing responses
        if (isMissingResponsesShown) {
            exportBuilder.append(
                    getRemainingRowsInCsvFormat(
                            fsrBundle, entry, question, questionDetails,
                            possibleGiversWithoutResponses, possibleRecipientsForGiver, prevGiver));
        }

        exportBuilder.append(System.lineSeparator() + System.lineSeparator());
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
    private StringBuilder getRemainingRowsInCsvFormat(
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

        removeParticipantIdentifierFromList(question.giverType, remainingPossibleGivers, prevGiver, results);

        for (String possibleGiverWithNoResponses : remainingPossibleGivers) {
            List<String> possibleRecipientsForRemainingGiver =
                    results.getPossibleRecipients(entry.getKey(), possibleGiverWithNoResponses);

            exportBuilder.append(getRowsOfPossibleRecipientsInCsvFormat(results,
                    question, questionDetails, possibleRecipientsForRemainingGiver,
                    possibleGiverWithNoResponses));
        }

        return exportBuilder;
    }

    /**
     * Given a participantIdentifier, remove it from participantIdentifierList.
     *
     * <p>Before removal, {@link FeedbackSessionResultsBundle#getFullNameFromRoster} is used to
     * convert the identifier into a canonical form if the participantIdentifierType is TEAMS.
     * @param participantIdentifierType
     * @param participantIdentifierList
     * @param participantIdentifier
     * @param bundle
     */
    private void removeParticipantIdentifierFromList(
            FeedbackParticipantType participantIdentifierType,
            List<String> participantIdentifierList, String participantIdentifier,
            FeedbackSessionResultsBundle bundle) {
        if (participantIdentifierType == FeedbackParticipantType.TEAMS) {
            participantIdentifierList.remove(bundle.getFullNameFromRoster(participantIdentifier));
        } else {
            participantIdentifierList.remove(participantIdentifier);
        }
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


}
