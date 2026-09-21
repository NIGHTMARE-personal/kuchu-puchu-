package com.example.executor

import java.text.Normalizer
import java.util.Locale

data class MatchedContact(
    val name: String,
    val number: String,
    val matchConfidence: Float = 1.0f
)

object ContactMatcher {

    // Common Devanagari <-> Latin Name Map
    private val DEVANAGARI_NAME_MAP = mapOf(
        "rahul" to "राहुल",
        "amit" to "अमित",
        "rohit" to "रोहित",
        "priya" to "प्रिया",
        "neha" to "नेहा",
        "pooja" to "पूजा",
        "puja" to "पूजा",
        "mummy" to "मम्मी",
        "mom" to "मम्मी",
        "mother" to "माताजी",
        "papa" to "पापा",
        "dad" to "पापा",
        "father" to "पिताजी",
        "bhai" to "भाई",
        "brother" to "भाई",
        "behen" to "बहन",
        "didi" to "दीदी",
        "bhaiya" to "भैया",
        "raj" to "राज",
        "vikram" to "विक्रम",
        "suresh" to "सुरेश",
        "ramesh" to "रमेश",
        "vijay" to "विजय",
        "ajay" to "अजय",
        "deepak" to "दीपक",
        "sunil" to "सुनील",
        "manoj" to "मनोज",
        "sanjay" to "संजय",
        "anjali" to "अंजलि",
        "anil" to "अनिल",
        "sunita" to "सुनीता",
        "kavita" to "कविता",
        "sharma" to "शर्मा",
        "verma" to "वर्मा",
        "singh" to "सिंह",
        "kumar" to "कुमार",
        "gupta" to "गुप्ता"
    )

    // Reverse map: Devanagari -> Latin
    private val LATIN_NAME_MAP: Map<String, String> = DEVANAGARI_NAME_MAP.entries.associate { (k, v) -> v to k }

    // Chinese Pinyin / Family Relations Map
    private val CHINESE_NAME_MAP = mapOf(
        "mama" to "妈妈",
        "baba" to "爸爸",
        "zhang" to "张",
        "wang" to "王",
        "li" to "李",
        "zhao" to "赵",
        "chen" to "陈",
        "liu" to "刘",
        "yang" to "杨",
        "huang" to "黄"
    )
    private val REVERSE_CHINESE_MAP = CHINESE_NAME_MAP.entries.associate { (k, v) -> v to k }

    // Japanese Romaji / Family Relations Map
    private val JAPANESE_NAME_MAP = mapOf(
        "okaasan" to "お母さん",
        "mama" to "ママ",
        "otousan" to "お父さん",
        "papa" to "パパ",
        "tanaka" to "田中",
        "sato" to "佐藤",
        "suzuki" to "鈴木",
        "takahashi" to "高橋",
        "watanabe" to "渡辺"
    )
    private val REVERSE_JAPANESE_MAP = JAPANESE_NAME_MAP.entries.associate { (k, v) -> v to k }

    // Korean Romanization / Family Relations Map
    private val KOREAN_NAME_MAP = mapOf(
        "eomma" to "엄마",
        "appa" to "아빠",
        "kim" to "김",
        "lee" to "이",
        "park" to "박",
        "choi" to "최",
        "jung" to "정",
        "kang" to "강",
        "minsu" to "민수",
        "jieun" to "지은"
    )
    private val REVERSE_KOREAN_MAP = KOREAN_NAME_MAP.entries.associate { (k, v) -> v to k }

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "")
            .trim()
    }

    /**
     * Transliterates a Devanagari string to phonetic Latin script.
     * E.g. "राहुल" -> "rahul", "अमित" -> "amit"
     */
    fun devanagariToLatin(input: String): String {
        // First check exact dictionary
        LATIN_NAME_MAP[input.trim()]?.let { return it }

        val sb = StringBuilder()
        var i = 0
        val len = input.length

        while (i < len) {
            val ch = input[i]
            when (ch) {
                // Independent vowels
                'अ' -> sb.append("a")
                'आ' -> sb.append("aa")
                'इ' -> sb.append("i")
                'ई' -> sb.append("ee")
                'उ' -> sb.append("u")
                'ऊ' -> sb.append("oo")
                'ऋ' -> sb.append("ri")
                'ए' -> sb.append("e")
                'ऐ' -> sb.append("ai")
                'ओ' -> sb.append("o")
                'औ' -> sb.append("au")
                'अ' -> sb.append("a")

                // Consonants
                'क' -> sb.append(handleConsonant(input, i, "k"))
                'ख' -> sb.append(handleConsonant(input, i, "kh"))
                'ग' -> sb.append(handleConsonant(input, i, "g"))
                'घ' -> sb.append(handleConsonant(input, i, "gh"))
                'ङ' -> sb.append(handleConsonant(input, i, "ng"))
                'च' -> sb.append(handleConsonant(input, i, "ch"))
                'छ' -> sb.append(handleConsonant(input, i, "chh"))
                'ज' -> sb.append(handleConsonant(input, i, "j"))
                'झ' -> sb.append(handleConsonant(input, i, "jh"))
                'ञ' -> sb.append(handleConsonant(input, i, "ny"))
                'ट' -> sb.append(handleConsonant(input, i, "t"))
                'ठ' -> sb.append(handleConsonant(input, i, "th"))
                'ड' -> sb.append(handleConsonant(input, i, "d"))
                'ढ' -> sb.append(handleConsonant(input, i, "dh"))
                'ण' -> sb.append(handleConsonant(input, i, "n"))
                'त' -> sb.append(handleConsonant(input, i, "t"))
                'थ' -> sb.append(handleConsonant(input, i, "th"))
                'द' -> sb.append(handleConsonant(input, i, "d"))
                'ध' -> sb.append(handleConsonant(input, i, "dh"))
                'न' -> sb.append(handleConsonant(input, i, "n"))
                'प' -> sb.append(handleConsonant(input, i, "p"))
                'फ' -> sb.append(handleConsonant(input, i, "ph"))
                'ब' -> sb.append(handleConsonant(input, i, "b"))
                'भ' -> sb.append(handleConsonant(input, i, "bh"))
                'म' -> sb.append(handleConsonant(input, i, "m"))
                'य' -> sb.append(handleConsonant(input, i, "y"))
                'र' -> sb.append(handleConsonant(input, i, "r"))
                'ल' -> sb.append(handleConsonant(input, i, "l"))
                'व' -> sb.append(handleConsonant(input, i, "v"))
                'श' -> sb.append(handleConsonant(input, i, "sh"))
                'ष' -> sb.append(handleConsonant(input, i, "sh"))
                'स' -> sb.append(handleConsonant(input, i, "s"))
                'ह' -> sb.append(handleConsonant(input, i, "h"))

                // Matras (vowel signs)
                'ा' -> sb.append("a")
                'ि' -> sb.append("i")
                'ी' -> sb.append("ee")
                'ु' -> sb.append("u")
                'ू' -> sb.append("oo")
                'ृ' -> sb.append("ri")
                'े' -> sb.append("e")
                'ै' -> sb.append("ai")
                'ो' -> sb.append("o")
                'ौ' -> sb.append("au")
                'ं', 'ँ' -> sb.append("n")
                'ः' -> sb.append("h")
                '्' -> { /* Virama handled in consonant check */ }

                else -> if (!ch.isWhitespace()) sb.append(ch) else sb.append(" ")
            }
            i++
        }

        return sb.toString().trim()
    }

    private fun handleConsonant(input: String, index: Int, latinConsonant: String): String {
        val nextIdx = index + 1
        if (nextIdx < input.length) {
            val nextChar = input[nextIdx]
            // If followed by halant / virama (्), don't append inherent vowel
            if (nextChar == '्') {
                return latinConsonant
            }
            // If followed by matra (ा, ि, etc.), matra will provide the vowel
            if (isMatra(nextChar)) {
                return latinConsonant
            }
        }
        // At end of word or followed by another consonant without matra
        // In modern Hindi, trailing consonants are usually schwa-deleted (e.g. 'ल' in राहुल is 'l', not 'la')
        val isTrailing = (nextIdx >= input.length || input[nextIdx].isWhitespace())
        return if (isTrailing) latinConsonant else "${latinConsonant}a"
    }

    private fun isMatra(ch: Char): Boolean {
        return ch in '\u093E'..'\u094C' || ch == '\u0962' || ch == '\u0963'
    }

    fun hasDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    fun findBestContact(query: String, contacts: List<Pair<String, String>>): MatchedContact? {
        if (contacts.isEmpty()) return null
        val cleanQuery = normalize(query)
        if (cleanQuery.isBlank()) return null

        val queryHasDevanagari = hasDevanagari(query)
        val latinQuery = if (queryHasDevanagari) devanagariToLatin(query) else cleanQuery
        val mappedDevanagariQuery = if (!queryHasDevanagari) DEVANAGARI_NAME_MAP[cleanQuery] else null

        val mappedChineseQuery = REVERSE_CHINESE_MAP[query.trim()] ?: CHINESE_NAME_MAP[cleanQuery]
        val mappedJapaneseQuery = REVERSE_JAPANESE_MAP[query.trim()] ?: JAPANESE_NAME_MAP[cleanQuery]
        val mappedKoreanQuery = REVERSE_KOREAN_MAP[query.trim()] ?: KOREAN_NAME_MAP[cleanQuery]

        // 1. Exact Match (Case & script insensitive)
        for ((name, number) in contacts) {
            val normName = normalize(name)
            if (normName == cleanQuery) {
                return MatchedContact(name, number, 1.0f)
            }
        }

        // 2. Transliteration Match (Devanagari <-> Latin)
        for ((name, number) in contacts) {
            val normName = normalize(name)
            val nameHasDevanagari = hasDevanagari(name)

            // Case A: Query in Hindi ("राहुल"), contact in English ("Rahul")
            if (queryHasDevanagari && !nameHasDevanagari) {
                if (normName == latinQuery || normName.contains(latinQuery)) {
                    return MatchedContact(name, number, 0.95f)
                }
            }

            // Case B: Query in English ("Rahul"), contact in Hindi ("राहुल")
            if (!queryHasDevanagari && nameHasDevanagari) {
                val nameInLatin = devanagariToLatin(name)
                if (nameInLatin == cleanQuery || nameInLatin.contains(cleanQuery)) {
                    return MatchedContact(name, number, 0.95f)
                }
                if (mappedDevanagariQuery != null && name.contains(mappedDevanagariQuery)) {
                    return MatchedContact(name, number, 0.95f)
                }
            }
        }

        // 3. CJK Transliteration / Alias Match
        if (mappedChineseQuery != null || mappedJapaneseQuery != null || mappedKoreanQuery != null) {
            for ((name, number) in contacts) {
                val normName = normalize(name)
                if (mappedChineseQuery != null && (name.contains(mappedChineseQuery) || normName.contains(mappedChineseQuery))) {
                    return MatchedContact(name, number, 0.9f)
                }
                if (mappedJapaneseQuery != null && (name.contains(mappedJapaneseQuery) || normName.contains(mappedJapaneseQuery))) {
                    return MatchedContact(name, number, 0.9f)
                }
                if (mappedKoreanQuery != null && (name.contains(mappedKoreanQuery) || normName.contains(mappedKoreanQuery))) {
                    return MatchedContact(name, number, 0.9f)
                }
            }
        }

        // 4. Token & Prefix Matches
        for ((name, number) in contacts) {
            val normName = normalize(name)
            val tokens = normName.split("\\s+".toRegex())
            if (tokens.any { it == cleanQuery || it == latinQuery }) {
                return MatchedContact(name, number, 0.85f)
            }
            if (normName.startsWith(cleanQuery) || normName.startsWith(latinQuery)) {
                return MatchedContact(name, number, 0.8f)
            }
        }

        // 5. Fuzzy Levenshtein Distance Match
        var bestContact: MatchedContact? = null
        var lowestDistance = Int.MAX_VALUE

        for ((name, number) in contacts) {
            val normName = normalize(name)
            val candidateLatin = if (hasDevanagari(name)) devanagariToLatin(name) else normName

            val dist = minOf(
                levenshtein(cleanQuery, normName),
                levenshtein(latinQuery, candidateLatin)
            )

            val maxLen = maxOf(latinQuery.length, candidateLatin.length)
            val allowedMaxDistance = when {
                maxLen <= 4 -> 1
                maxLen <= 7 -> 2
                else -> 3
            }

            if (dist <= allowedMaxDistance && dist < lowestDistance) {
                lowestDistance = dist
                bestContact = MatchedContact(name, number, 1f - (dist.toFloat() / maxLen))
            }
        }

        return bestContact
    }

    private fun levenshtein(s: String, t: String): Int {
        val dp = Array(s.length + 1) { IntArray(t.length + 1) }
        for (i in 0..s.length) dp[i][0] = i
        for (j in 0..t.length) dp[0][j] = j
        for (i in 1..s.length) {
            for (j in 1..t.length) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s.length][t.length]
    }
}
