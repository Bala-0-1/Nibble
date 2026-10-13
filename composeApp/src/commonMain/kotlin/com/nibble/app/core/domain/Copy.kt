package com.nibble.app.core.domain

/**
 * Copy as typed data, not string literals scattered through composables (§11).
 * Tone.DRY lines punch at the behaviour, never the person. Tone.PLAIN never jokes
 * (errors, empty states, accessibility). [Voice.NEUTRAL] uses the neutral variant.
 */
enum class Tone { DRY, PLAIN }

data class Line(
    val key: String,
    val tone: Tone,
    val dry: String,
    val neutral: String,
) {
    fun resolve(voice: Voice): String =
        if (voice == Voice.NEUTRAL || tone == Tone.PLAIN) neutral else dry
}

object Copy {
    val onboardingTitle = "How cooked is your attention span?"
    val onboardingSub = Line(
        "onboarding.sub", Tone.DRY,
        dry = "Be honest. We can see your screen time anyway.",
        neutral = "A quick question to size your first reading session.",
    )
    val prescription = "Roughly one scroll of a feed you won't remember."

    val pickTitle = "Pick one. Just one."
    val pickSub = Line(
        "pick.sub", Tone.DRY,
        dry = "Choosing five books is how you ended up reading zero.",
        neutral = "Start with a single book. You can add more later.",
    )

    val rewardTitle = "Nibble down."
    val rewardPraise = Line(
        "reward.praise", Tone.DRY,
        dry = "%s of sustained attention. Genuinely impressive. Low bar, but impressive.",
        neutral = "%s of reading. Nicely done.",
    )
    val declineMore = Line(
        "reward.decline", Tone.DRY,
        dry = "No, I have important scrolling to do",
        neutral = "Not now",
    )
    val oneMore = "One more? It's 2 minutes"

    val statsTitle = "Damage report"
    val statsStreakBoast = Line(
        "stats.streak", Tone.DRY,
        dry = "Officially longer than your last gym membership.",
        neutral = "Your longest run so far.",
    )
    val screenTimeSaved = Line(
        "stats.saved", Tone.DRY,
        dry = "Same thing, really",
        neutral = "Time not spent on a screen",
    )

    val streakNudge = Line(
        "nudge.streak", Tone.DRY,
        dry = "No pressure. Well — some pressure.",
        neutral = "A short session keeps your streak alive.",
    )
    val letItGo = "Let it go"
    val catchMeUp = "Catch me up"

    // PLAIN — never funny at someone who is stuck.
    val importDrm = Line(
        "error.drm", Tone.PLAIN,
        dry = "This file is copy-protected and can't be opened.",
        neutral = "This file is copy-protected and can't be opened.",
    )

    /** A neutral, non-graphic stop-point summary for the reward screen. */
    val whereYouStopped =
        "A quiet lane, a courteous stranger, and a cane that answers for its owner. By morning the whole city will be talking."
    val nextTeaser = "Next: the morning after"
    val whoGloss = "Carew is the polite old gentleman from chapter two. You met him for one paragraph and forgot instantly. That's fine."
}
