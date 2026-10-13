package com.nibble.app.core.domain

/**
 * The voice (§11) as typed data. DRY punches at the behaviour, never the person; PLAIN
 * never jokes (errors, empty, a11y). Variants rotate — a persisted per-key index cycles
 * so a user never sees the same roast twice running. NEUTRAL uses the neutral variant.
 */
data class WittyLine(
    val key: String,
    val tone: Tone,
    val dry: List<String>,
    val neutral: String,
) {
    fun resolve(voice: Voice, index: Int): String =
        if (voice == Voice.NEUTRAL || tone == Tone.PLAIN || dry.isEmpty()) neutral
        else dry[index.mod(dry.size)]
}

object Wit {

    val onboardingSub = WittyLine(
        "onboarding.sub", Tone.DRY,
        dry = listOf(
            "Be honest. We can see your screen time anyway.",
            "There are no wrong answers here. Only revealing ones.",
            "Don't overthink it — overthinking clearly isn't the problem lately.",
        ),
        neutral = "A quick question to size your first reading session.",
    )

    val pickSub = WittyLine(
        "pick.sub", Tone.DRY,
        dry = listOf(
            "Choosing five books is how you ended up reading zero.",
            "One book. You can barely finish a text message.",
            "Pick one. Commitment issues are not a personality.",
        ),
        neutral = "Start with a single book. You can add more later.",
    )

    // Reward praise takes a "%s" time slot.
    val rewardPraise = WittyLine(
        "reward.praise", Tone.DRY,
        dry = listOf(
            "%s of sustained attention. Genuinely impressive. Low bar, but impressive.",
            "%s. That's longer than most of your phone unlocks.",
            "%s of reading. Your brain briefly remembered what it's for.",
            "%s. More focus than your last three resolutions combined.",
            "%s of actual attention. We're as surprised as you are.",
        ),
        neutral = "%s of reading. Nicely done.",
    )

    val decline = WittyLine(
        "reward.decline", Tone.DRY,
        dry = listOf(
            "No, I have important scrolling to do",
            "Not now — someone is wrong on the internet",
            "Later, my feed won't refresh itself",
            "No, I've earned a nice long doomscroll",
        ),
        neutral = "Not now",
    )

    val statsBoast = WittyLine(
        "stats.boast", Tone.DRY,
        dry = listOf(
            "Officially longer than your last gym membership.",
            "Longer than most of your relationships, statistically.",
            "You've stuck with this longer than that podcast you swore by.",
            "A streak this size usually comes with a subscription you forgot to cancel.",
        ),
        neutral = "Your longest run so far.",
    )

    val screenTimeSaved = WittyLine(
        "stats.saved", Tone.DRY,
        dry = listOf("Same thing, really", "Time the algorithm didn't get", "A small act of rebellion"),
        neutral = "Time not spent on a screen",
    )

    val homeSubStreak = WittyLine(
        "home.streak", Tone.DRY,
        dry = listOf(
            "Day %d. Don't be the one to break it.",
            "Day %d. Momentum is a terrible thing to waste.",
            "%d days. The bookmark is getting cocky.",
        ),
        neutral = "Day %d of your streak.",
    )

    val homeSubZero = WittyLine(
        "home.zero", Tone.DRY,
        dry = listOf(
            "A two-minute nibble is a low bar. Clear it.",
            "Zero days. Everyone starts somewhere embarrassing.",
            "The book isn't going to read itself. Believe us, we asked.",
        ),
        neutral = "A two-minute session is a good place to start.",
    )

    /** Abandoned-shelf roast, scaled by how long it's been paused. Forgiving, never cruel. */
    fun abandonedRoast(monthsPaused: Int, index: Int): String {
        val lines = when {
            monthsPaused <= 0 -> listOf("Paused, but warm.", "Barely cold.")
            monthsPaused < 2 -> listOf("Paused a while ago. Still salvageable.", "Recently abandoned. We won't judge. Much.")
            monthsPaused < 12 -> listOf("Paused $monthsPaused months ago. It waits.", "$monthsPaused months. The plot has not moved without you.")
            else -> listOf("Paused a very long time ago. It's fine. It's fine.", "Ancient history. The characters have moved on. Have you?")
        }
        return lines[index.mod(lines.size)]
    }

    /**
     * Streak / dormancy nudge (§11), escalating and capped. Roast-safe: these are the
     * authored templates the model picks among and fills — it never free-writes an insult.
     */
    fun nudge(daysDormant: Int, index: Int): WittyLine = when {
        daysDormant <= 0 -> WittyLine(
            "nudge.today", Tone.DRY,
            listOf(
                "Your streak dies tonight. No pressure. Well — some pressure.",
                "Two minutes. You've wasted more than that reading this notification.",
            ),
            "A short session keeps your streak alive today.",
        )
        daysDormant == 1 -> WittyLine(
            "nudge.d1", Tone.DRY,
            listOf(
                "Your book is where you left it. Unlike your gym shoes.",
                "One day off. Rookie numbers. Let's not make it a habit.",
            ),
            "You read yesterday — keep it going today.",
        )
        daysDormant in 2..6 -> WittyLine(
            "nudge.d3", Tone.DRY,
            listOf(
                "The chapter has been waiting $daysDormant days. It's fine. It's fine.",
                "$daysDormant days. The bookmark is starting to take it personally.",
            ),
            "It's been $daysDormant days since you read.",
        )
        daysDormant == 7 -> WittyLine(
            "nudge.d7", Tone.DRY,
            listOf("We'll stop mentioning it after this one."),
            "It's been a week since you read.",
        )
        else -> WittyLine(
            // day 8+: silence, one plain reminder. Never funny at someone who's given up.
            "nudge.silent", Tone.PLAIN,
            listOf("Your book is still here whenever you want it."),
            "Your book is still here whenever you want it.",
        )
    }
}
