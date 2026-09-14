package com.studysync.gamification

import com.studysync.data.DashboardSnapshot

data class StudyBadge(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val unlocked: Boolean
)

data class GamificationProgress(
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpPerLevel: Int,
    val streakDays: Int,
    val badges: List<StudyBadge>
) {
    val progressFraction: Float
        get() = if (xpPerLevel <= 0) 0f else (xpIntoLevel.toFloat() / xpPerLevel.toFloat()).coerceIn(0f, 1f)
}

object Gamification {
    const val XP_PER_LEVEL = 100

    fun from(snapshot: DashboardSnapshot, streakDays: Int): GamificationProgress {
        val xp = snapshot.totalClasses * 50 +
            snapshot.totalGroups * 40 +
            snapshot.totalFriends * 25 +
            snapshot.recentResources.size * 15 +
            snapshot.recentMessages.size * 5 +
            (streakDays.coerceAtLeast(0) * 8)
        val level = 1 + xp / XP_PER_LEVEL
        val into = xp % XP_PER_LEVEL
        return GamificationProgress(
            xp = xp,
            level = level,
            xpIntoLevel = into,
            xpPerLevel = XP_PER_LEVEL,
            streakDays = streakDays.coerceAtLeast(0),
            badges = badgesFor(snapshot, streakDays)
        )
    }

    private fun badgesFor(snapshot: DashboardSnapshot, streakDays: Int): List<StudyBadge> {
        return listOf(
            StudyBadge(
                id = "welcome",
                titleRes = com.studysync.R.string.badge_welcome_title,
                descriptionRes = com.studysync.R.string.badge_welcome_desc,
                unlocked = true
            ),
            StudyBadge(
                id = "first_class",
                titleRes = com.studysync.R.string.badge_first_class_title,
                descriptionRes = com.studysync.R.string.badge_first_class_desc,
                unlocked = snapshot.totalClasses >= 1
            ),
            StudyBadge(
                id = "first_group",
                titleRes = com.studysync.R.string.badge_first_group_title,
                descriptionRes = com.studysync.R.string.badge_first_group_desc,
                unlocked = snapshot.totalGroups >= 1
            ),
            StudyBadge(
                id = "connector",
                titleRes = com.studysync.R.string.badge_connector_title,
                descriptionRes = com.studysync.R.string.badge_connector_desc,
                unlocked = snapshot.totalFriends >= 3
            ),
            StudyBadge(
                id = "scholar",
                titleRes = com.studysync.R.string.badge_scholar_title,
                descriptionRes = com.studysync.R.string.badge_scholar_desc,
                unlocked = snapshot.totalClasses >= 5
            ),
            StudyBadge(
                id = "streak_3",
                titleRes = com.studysync.R.string.badge_streak3_title,
                descriptionRes = com.studysync.R.string.badge_streak3_desc,
                unlocked = streakDays >= 3
            ),
            StudyBadge(
                id = "streak_7",
                titleRes = com.studysync.R.string.badge_streak7_title,
                descriptionRes = com.studysync.R.string.badge_streak7_desc,
                unlocked = streakDays >= 7
            )
        )
    }
}
