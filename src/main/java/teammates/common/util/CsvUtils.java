package teammates.common.util;

import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.FeedbackSessionResultsBundle;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseCommentAttributes;
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
    private static final FeedbackSessionsLogic feedbackSessionsLogic = FeedbackSessionsLogic.inst();
    private static final String ERROR_NUMBER_OF_RESPONSES_EXCEEDS_RANGE = "Number of responses exceeds the limited range";

    private CsvUtils(){}

    public static CsvUtils getCsvUtils(){
        return instance;
    }

    public String getFeedbackSessionResultsSummaryInSectionAsCsv(
            String feedbackSessionName, String courseId, String userEmail,
            String section, String questionId, boolean isMissingResponsesShown, boolean isStatsShown)
            throws EntityDoesNotExistException, ExceedingRangeException {

        FeedbackSessionResultsBundle results;
        int indicatedRange = section == null ? Const.INSTRUCTOR_VIEW_RESPONSE_LIMIT : -1;

        if (questionId == null) {
            results = feedbackSessionsLogic.getFeedbackSessionResultsForInstructorInSectionWithinRangeFromView(
                    feedbackSessionName, courseId, userEmail, section,
                    indicatedRange, Const.FeedbackSessionResults.GRQ_SORT_TYPE);
        } else if (section == null) {
            results = feedbackSessionsLogic.getFeedbackSessionResultsForInstructorFromQuestion(
                    feedbackSessionName, courseId, userEmail, questionId);
        } else {
            results = feedbackSessionsLogic.getFeedbackSessionResultsForInstructorFromQuestionInSection(
                    feedbackSessionName, courseId, userEmail, questionId, section);
        }

        if (!results.isComplete) {
            throw new ExceedingRangeException(ERROR_NUMBER_OF_RESPONSES_EXCEEDS_RANGE);
        }
        // sort responses by giver > recipient > qnNumber
        results.responses.sort(results.compareByGiverRecipientQuestion);

        StringBuilder exportBuilder = new StringBuilder(100);

        exportBuilder.append(String.format("Course,%s",
                SanitizationHelper.sanitizeForCsv(results.feedbackSession.getCourseId())))
                .append(System.lineSeparator())
                .append(String.format("Session Name,%s",
                        SanitizationHelper.sanitizeForCsv(results.feedbackSession.getFeedbackSessionName())))
                .append(System.lineSeparator());

        if (section != null) {
            exportBuilder.append(String.format("Section Name,%s", SanitizationHelper.sanitizeForCsv(section)))
                    .append(System.lineSeparator());
        }

        exportBuilder.append(System.lineSeparator()).append(System.lineSeparator());

        Set<Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>>> entrySet =
                results.getQuestionResponseMap().entrySet();

        for (Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry : entrySet) {
            exportBuilder.append(getFeedbackSessionResultsForQuestionInCsvFormat(
                    results, entry, isMissingResponsesShown, isStatsShown, section, feedbackSessionsLogic));
        }

        return exportBuilder.toString();
    }

    private StringBuilder getFeedbackSessionResultsForQuestionInCsvFormat(
            FeedbackSessionResultsBundle fsrBundle,
            Map.Entry<FeedbackQuestionAttributes, List<FeedbackResponseAttributes>> entry,
            boolean isMissingResponsesShown, boolean isStatsShown, String section, FeedbackSessionsLogic feedbackSessionsLogic) {

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
        return exportBuilder;
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
            String giverName = results.getFullNameFromRoster(giver);
            String giverLastName = results.getLastNameFromRoster(giver);
            String giverEmail = results.getDisplayableEmailFromRoster(giver);
            String possibleRecipientName = results.getFullNameFromRoster(possibleRecipient);
            String possibleRecipientLastName = results.getLastNameFromRoster(possibleRecipient);
            String possibleRecipientEmail = results.getDisplayableEmailFromRoster(possibleRecipient);

            if (questionDetails.shouldShowNoResponseText(question)) {
                exportBuilder.append(SanitizationHelper.sanitizeForCsv(results.getTeamNameFromRoster(giver))
                        + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(giverName))
                        + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(giverLastName))
                        + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(giverEmail))
                        + "," + SanitizationHelper.sanitizeForCsv(results.getTeamNameFromRoster(possibleRecipient))
                        + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(possibleRecipientName))
                        + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(possibleRecipientLastName))
                        + "," + SanitizationHelper.sanitizeForCsv(StringHelper.removeExtraSpace(possibleRecipientEmail))
                        + "," + questionDetails.getNoResponseTextInCsv(giver, possibleRecipient, results, question)
                        + System.lineSeparator());
            }
        }
        return exportBuilder;
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
