fun ansiTruecolor(r: Int, g: Int, b: Int, s: String): String {
    return "\u001b[38;2;$r;$g;${b}m$s\u001b[0m"
}