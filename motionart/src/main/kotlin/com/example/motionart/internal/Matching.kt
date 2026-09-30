package com.example.motionart.internal

import java.text.Normalizer

/**
 * Title/artist comparison for matching a local track against a catalog release.
 *
 * Two services almost never spell a release the same way — one has `1989 (Taylor's Version)
 * [Deluxe]`, the other just `1989` — so a literal comparison would reject nearly every real match.
 * Everything here exists to strip the decoration that differs and compare what is left.
 */
internal object Matching {

    /** Bracketed decoration: `(Deluxe Edition)`, `[Remastered]`, `{2011}`. */
    private val BRACKETED = Regex("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]")

    /** Featured-artist credits, which one service lists and the other often does not. */
    private val FEATURING = Regex("\\b(feat|ft|featuring|with)\\.?\\s.*$")

    /** Trailing release-type and reissue markers hung off a dash. */
    private val DASH_SUFFIX = Regex(
        "\\s-\\s.*\\b(single|ep|deluxe|remaster(ed)?|edition|version|mono|stereo|" +
            "anniversary|expanded|bonus|soundtrack|ost|explicit|clean)\\b.*$",
    )

    /** The same markers when they appear bare rather than after a dash. */
    private val BARE_SUFFIX = Regex(
        "\\b(deluxe|remaster(ed)?|expanded|explicit|clean)\\b\\s*(edition|version)?\\s*$",
    )

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING = Regex("\\p{M}+")

    /** Separators services use to cram several artists into one string. */
    private val ARTIST_SPLIT = Regex("\\s*(,|&|;|/|\\bx\\b|\\bvs\\.?\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE)

    /**
     * Reduce a title to the part worth comparing: lowercase, unaccented, decoration removed.
     *
     * Order matters — brackets go first so a `(feat. …)` credit is taken out as a block before the
     * bare-suffix rules run on whatever is left.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(COMBINING, "")
            .lowercase()
        s = s.replace(BRACKETED, " ")
        s = s.replace(DASH_SUFFIX, " ")
        s = s.replace(FEATURING, " ")
        s = s.replace(BARE_SUFFIX, " ")
        s = s.replace(NON_ALNUM, " ")
        return s.trim()
    }

    /** The lead artist of a credit string, so `Drake & 21 Savage` is compared as `drake`. */
    fun primaryArtist(raw: String?): String {
        val whole = normalize(raw)
        if (whole.isEmpty()) return ""
        val lead = raw.orEmpty().split(ARTIST_SPLIT).firstOrNull().orEmpty()
        return normalize(lead).ifEmpty { whole }
    }

    /**
     * Whether two credits plausibly name the same act.
     *
     * A prefix relationship counts, because compilations and features routinely pad the credit with
     * collaborators the other service leaves off. Substring matching anywhere would be too loose —
     * it would pair `Sia` with `Anastasia`.
     */
    fun artistsMatch(mine: String?, theirs: String?): Boolean {
        val a = primaryArtist(mine)
        val b = primaryArtist(theirs)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        // Require the boundary so the shorter name has to be whole words of the longer one.
        return longer.startsWith("$shorter ")
    }

    /**
     * Whether two release or track names plausibly refer to the same thing.
     *
     * Containment is allowed in either direction: once the decoration is stripped, the leftover
     * difference is usually one service keeping a qualifier the other dropped.
     */
    fun titlesMatch(mine: String?, theirs: String?): Boolean {
        val a = normalize(mine)
        val b = normalize(theirs)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }
}
