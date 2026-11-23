package dev.giftshop;

final class ViewerText {
    private ViewerText() {}

    static String clean(String s, int limit) {
        var out = new StringBuilder();
        s.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != '§' && c != 0x2028 && c != 0x2029)
            .limit(limit).forEach(out::appendCodePoint);
        return out.toString().strip();
    }
}
