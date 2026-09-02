package dbest.kernel.util

fun fileSafe(name: String): String {
    val out = StringBuilder()
    for (c in name) {
        out.append(if (c.isLetterOrDigit() || c == '.' || c == '_' || c == '-') c else '_')
    }
    return if (isEmpty(out)) "export" else out.toString()
}
