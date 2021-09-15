import java.util.concurrent.ThreadLocalRandom

private const val ALPHABET = "ACEFHKLMNPRTXYacefhklmnoprtxyz134789"
const val DEFAULT_ID_LENGTH = 32

fun randomString(length: Int): String {
    return buildString {
        repeat(length) {
            append(ALPHABET[ThreadLocalRandom.current().nextInt(ALPHABET.length)])
        }
    }
}