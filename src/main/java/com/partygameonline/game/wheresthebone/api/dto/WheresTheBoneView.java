package com.partygameonline.game.wheresthebone.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A viewer-safe snapshot of a Where's the Bone game. */
public record WheresTheBoneView(
        String gameId,
        String roomId,
        String viewerPlayerId,
        String phase,
        int version,
        Instant serverTime,
        Instant phaseStartedAt,
        Instant deadline,
        boolean finished,
        int currentHour,
        boolean boneTaken,
        Integer boneTakenHour,
        String boneTakenBy,
        List<String> currentAwakePlayerIds,
        List<WheresTheBonePlayerView> players,
        String myRole,
        List<Integer> myDice,
        List<Integer> myWakeHours,
        List<Integer> mySelectedWakeHours,
        Map<String, List<Integer>> myPeekResults,
        List<String> myClues,
        List<WheresTheBoneCoAwakeRecord> myCoAwakeRecords,
        List<Integer> myWitnessedBoneTakenHours,
        List<Integer> myObservedBonePresentHours,
        List<Integer> myObservedBoneMissingHours,
        List<String> knownPackmateIds,
        String knownBoneThiefId,
        boolean myWhiteDogRecruited,
        List<String> packmateCandidateIds,
        int requiredPackmateCount,
        List<String> winnerPlayerIds,
        String winnerFaction,
        Map<String, String> votes,
        Map<String, Integer> voteCounts,
        List<WheresTheBoneEventView> events,
        Map<String, Integer> eloChanges,
        List<String> legalActions,
        boolean canAct
) {
}
