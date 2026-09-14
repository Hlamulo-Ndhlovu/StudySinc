package com.studysync.gamification

import com.studysync.data.DashboardSnapshot

enum class AchievementTier(val displayName: String, val xpRequirement: Int, val color: String) {
    BRONZE("Bronze", 0, "#CD7F32"),
    SILVER("Silver", 500, "#C0C0C0"),
    GOLD("Gold", 1500, "#FFD700"),
    PLATINUM("Platinum", 3000, "#E5E4E2"),
    DIAMOND("Diamond", 5000, "#B9F2FF")
}

data class StudyBadge(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val unlocked: Boolean,
    val tier: AchievementTier = AchievementTier.BRONZE,
    val progress: Float = 0f,
    val maxProgress: Int = 1
)

data class GamificationProgress(
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpPerLevel: Int,
    val streakDays: Int,
    val badges: List<StudyBadge>,
    val currentTier: AchievementTier,
    val totalAchievementsUnlocked: Int,
    val totalAchievementsAvailable: Int
) {
    val progressFraction: Float
        get() = if (xpPerLevel <= 0) 0f else (xpIntoLevel.toFloat() / xpPerLevel.toFloat()).coerceIn(0f, 1f)
    
    val tierProgress: Float
        get() = calculateTierProgress()
    
    private fun calculateTierProgress(): Float {
        val tiers = AchievementTier.entries
        val currentIndex = tiers.indexOf(currentTier)
        if (currentIndex >= tiers.size - 1) return 1f
        
        val currentTierXP = currentTier.xpRequirement
        val nextTierXP = tiers[currentIndex + 1].xpRequirement
        val tierRange = nextTierXP - currentTierXP
        
        return if (tierRange > 0) {
            ((xp - currentTierXP).toFloat() / tierRange.toFloat()).coerceIn(0f, 1f)
        } else 1f
    }
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
        
        val badges = badgesFor(snapshot, streakDays, xp)
        val currentTier = calculateTier(xp)
        val unlockedCount = badges.count { it.unlocked }
        
        return GamificationProgress(
            xp = xp,
            level = level,
            xpIntoLevel = into,
            xpPerLevel = XP_PER_LEVEL,
            streakDays = streakDays.coerceAtLeast(0),
            badges = badges,
            currentTier = currentTier,
            totalAchievementsUnlocked = unlockedCount,
            totalAchievementsAvailable = badges.size
        )
    }

    private fun calculateTier(xp: Int): AchievementTier {
        return when {
            xp >= AchievementTier.DIAMOND.xpRequirement -> AchievementTier.DIAMOND
            xp >= AchievementTier.PLATINUM.xpRequirement -> AchievementTier.PLATINUM
            xp >= AchievementTier.GOLD.xpRequirement -> AchievementTier.GOLD
            xp >= AchievementTier.SILVER.xpRequirement -> AchievementTier.SILVER
            else -> AchievementTier.BRONZE
        }
    }

    private fun badgesFor(snapshot: DashboardSnapshot, streakDays: Int, xp: Int): List<StudyBadge> {
        return listOf(
            // Welcome Achievement (Single tier)
            StudyBadge(
                id = "welcome",
                titleRes = com.studysync.R.string.badge_welcome_title,
                descriptionRes = com.studysync.R.string.badge_welcome_desc,
                unlocked = true,
                tier = AchievementTier.BRONZE
            ),
            
            // Class Achievement (Multi-tier)
            StudyBadge(
                id = "class_bronze",
                titleRes = com.studysync.R.string.badge_class_bronze_title,
                descriptionRes = com.studysync.R.string.badge_class_bronze_desc,
                unlocked = snapshot.totalClasses >= 1,
                tier = AchievementTier.BRONZE,
                progress = snapshot.totalClasses.toFloat().coerceAtMost(1f),
                maxProgress = 1
            ),
            StudyBadge(
                id = "class_silver",
                titleRes = com.studysync.R.string.badge_class_silver_title,
                descriptionRes = com.studysync.R.string.badge_class_silver_desc,
                unlocked = snapshot.totalClasses >= 5,
                tier = AchievementTier.SILVER,
                progress = (snapshot.totalClasses.toFloat() / 5f).coerceIn(0f, 1f),
                maxProgress = 5
            ),
            StudyBadge(
                id = "class_gold",
                titleRes = com.studysync.R.string.badge_class_gold_title,
                descriptionRes = com.studysync.R.string.badge_class_gold_desc,
                unlocked = snapshot.totalClasses >= 10,
                tier = AchievementTier.GOLD,
                progress = (snapshot.totalClasses.toFloat() / 10f).coerceIn(0f, 1f),
                maxProgress = 10
            ),
            StudyBadge(
                id = "class_platinum",
                titleRes = com.studysync.R.string.badge_class_platinum_title,
                descriptionRes = com.studysync.R.string.badge_class_platinum_desc,
                unlocked = snapshot.totalClasses >= 25,
                tier = AchievementTier.PLATINUM,
                progress = (snapshot.totalClasses.toFloat() / 25f).coerceIn(0f, 1f),
                maxProgress = 25
            ),
            
            // Group Achievement (Multi-tier)
            StudyBadge(
                id = "group_bronze",
                titleRes = com.studysync.R.string.badge_group_bronze_title,
                descriptionRes = com.studysync.R.string.badge_group_bronze_desc,
                unlocked = snapshot.totalGroups >= 1,
                tier = AchievementTier.BRONZE,
                progress = snapshot.totalGroups.toFloat().coerceAtMost(1f),
                maxProgress = 1
            ),
            StudyBadge(
                id = "group_silver",
                titleRes = com.studysync.R.string.badge_group_silver_title,
                descriptionRes = com.studysync.R.string.badge_group_silver_desc,
                unlocked = snapshot.totalGroups >= 3,
                tier = AchievementTier.SILVER,
                progress = (snapshot.totalGroups.toFloat() / 3f).coerceIn(0f, 1f),
                maxProgress = 3
            ),
            StudyBadge(
                id = "group_gold",
                titleRes = com.studysync.R.string.badge_group_gold_title,
                descriptionRes = com.studysync.R.string.badge_group_gold_desc,
                unlocked = snapshot.totalGroups >= 5,
                tier = AchievementTier.GOLD,
                progress = (snapshot.totalGroups.toFloat() / 5f).coerceIn(0f, 1f),
                maxProgress = 5
            ),
            
            // Friend Achievement (Multi-tier)
            StudyBadge(
                id = "friend_bronze",
                titleRes = com.studysync.R.string.badge_friend_bronze_title,
                descriptionRes = com.studysync.R.string.badge_friend_bronze_desc,
                unlocked = snapshot.totalFriends >= 1,
                tier = AchievementTier.BRONZE,
                progress = snapshot.totalFriends.toFloat().coerceAtMost(1f),
                maxProgress = 1
            ),
            StudyBadge(
                id = "friend_silver",
                titleRes = com.studysync.R.string.badge_friend_silver_title,
                descriptionRes = com.studysync.R.string.badge_friend_silver_desc,
                unlocked = snapshot.totalFriends >= 5,
                tier = AchievementTier.SILVER,
                progress = (snapshot.totalFriends.toFloat() / 5f).coerceIn(0f, 1f),
                maxProgress = 5
            ),
            StudyBadge(
                id = "friend_gold",
                titleRes = com.studysync.R.string.badge_friend_gold_title,
                descriptionRes = com.studysync.R.string.badge_friend_gold_desc,
                unlocked = snapshot.totalFriends >= 10,
                tier = AchievementTier.GOLD,
                progress = (snapshot.totalFriends.toFloat() / 10f).coerceIn(0f, 1f),
                maxProgress = 10
            ),
            StudyBadge(
                id = "friend_platinum",
                titleRes = com.studysync.R.string.badge_friend_platinum_title,
                descriptionRes = com.studysync.R.string.badge_friend_platinum_desc,
                unlocked = snapshot.totalFriends >= 25,
                tier = AchievementTier.PLATINUM,
                progress = (snapshot.totalFriends.toFloat() / 25f).coerceIn(0f, 1f),
                maxProgress = 25
            ),
            
            // Streak Achievement (Multi-tier)
            StudyBadge(
                id = "streak_bronze",
                titleRes = com.studysync.R.string.badge_streak_bronze_title,
                descriptionRes = com.studysync.R.string.badge_streak_bronze_desc,
                unlocked = streakDays >= 3,
                tier = AchievementTier.BRONZE,
                progress = (streakDays.toFloat() / 3f).coerceIn(0f, 1f),
                maxProgress = 3
            ),
            StudyBadge(
                id = "streak_silver",
                titleRes = com.studysync.R.string.badge_streak_silver_title,
                descriptionRes = com.studysync.R.string.badge_streak_silver_desc,
                unlocked = streakDays >= 7,
                tier = AchievementTier.SILVER,
                progress = (streakDays.toFloat() / 7f).coerceIn(0f, 1f),
                maxProgress = 7
            ),
            StudyBadge(
                id = "streak_gold",
                titleRes = com.studysync.R.string.badge_streak_gold_title,
                descriptionRes = com.studysync.R.string.badge_streak_gold_desc,
                unlocked = streakDays >= 14,
                tier = AchievementTier.GOLD,
                progress = (streakDays.toFloat() / 14f).coerceIn(0f, 1f),
                maxProgress = 14
            ),
            StudyBadge(
                id = "streak_platinum",
                titleRes = com.studysync.R.string.badge_streak_platinum_title,
                descriptionRes = com.studysync.R.string.badge_streak_platinum_desc,
                unlocked = streakDays >= 30,
                tier = AchievementTier.PLATINUM,
                progress = (streakDays.toFloat() / 30f).coerceIn(0f, 1f),
                maxProgress = 30
            ),
            
            // Resource Achievement (Multi-tier)
            StudyBadge(
                id = "resource_bronze",
                titleRes = com.studysync.R.string.badge_resource_bronze_title,
                descriptionRes = com.studysync.R.string.badge_resource_bronze_desc,
                unlocked = snapshot.recentResources.size >= 1,
                tier = AchievementTier.BRONZE,
                progress = snapshot.recentResources.size.toFloat().coerceAtMost(1f),
                maxProgress = 1
            ),
            StudyBadge(
                id = "resource_silver",
                titleRes = com.studysync.R.string.badge_resource_silver_title,
                descriptionRes = com.studysync.R.string.badge_resource_silver_desc,
                unlocked = snapshot.recentResources.size >= 5,
                tier = AchievementTier.SILVER,
                progress = (snapshot.recentResources.size.toFloat() / 5f).coerceIn(0f, 1f),
                maxProgress = 5
            ),
            StudyBadge(
                id = "resource_gold",
                titleRes = com.studysync.R.string.badge_resource_gold_title,
                descriptionRes = com.studysync.R.string.badge_resource_gold_desc,
                unlocked = snapshot.recentResources.size >= 10,
                tier = AchievementTier.GOLD,
                progress = (snapshot.recentResources.size.toFloat() / 10f).coerceIn(0f, 1f),
                maxProgress = 10
            ),
            
            // Message Achievement (Multi-tier)
            StudyBadge(
                id = "message_bronze",
                titleRes = com.studysync.R.string.badge_message_bronze_title,
                descriptionRes = com.studysync.R.string.badge_message_bronze_desc,
                unlocked = snapshot.recentMessages.size >= 5,
                tier = AchievementTier.BRONZE,
                progress = (snapshot.recentMessages.size.toFloat() / 5f).coerceIn(0f, 1f),
                maxProgress = 5
            ),
            StudyBadge(
                id = "message_silver",
                titleRes = com.studysync.R.string.badge_message_silver_title,
                descriptionRes = com.studysync.R.string.badge_message_silver_desc,
                unlocked = snapshot.recentMessages.size >= 25,
                tier = AchievementTier.SILVER,
                progress = (snapshot.recentMessages.size.toFloat() / 25f).coerceIn(0f, 1f),
                maxProgress = 25
            ),
            StudyBadge(
                id = "message_gold",
                titleRes = com.studysync.R.string.badge_message_gold_title,
                descriptionRes = com.studysync.R.string.badge_message_gold_desc,
                unlocked = snapshot.recentMessages.size >= 50,
                tier = AchievementTier.GOLD,
                progress = (snapshot.recentMessages.size.toFloat() / 50f).coerceIn(0f, 1f),
                maxProgress = 50
            )
        )
    }
}
