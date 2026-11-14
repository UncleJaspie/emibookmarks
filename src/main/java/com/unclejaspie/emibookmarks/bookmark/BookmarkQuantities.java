package com.unclejaspie.emibookmarks.bookmark;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Native values remain exact; only the visible label is abbreviated. Unknown units are explicit. */
public final class BookmarkQuantities {
    public static final BookmarkSlot.Unit BUCKET = new BookmarkSlot.Unit("forge:fluid", "B", 1, 1000);
    public static BookmarkSlot.Unit displayUnit(long amount, BookmarkSlot.Unit nativeUnit) {
        return nativeUnit.equals(BookmarkSlot.Unit.MILLIBUCKET) && amount >= 1000 ? BUCKET : nativeUnit;
    }
    public static String exact(long amount, BookmarkSlot.Unit unit) {
        var numerator = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(unit.numerator()));
        String value;
        try { value = numerator.divide(BigDecimal.valueOf(unit.denominator())).stripTrailingZeros().toPlainString(); }
        catch (ArithmeticException repeating) { value = numerator.toPlainString() + "/" + unit.denominator(); }
        return value + (unit.symbol().isEmpty() ? "" : " " + unit.symbol());
    }

    public static String compact(long amount, BookmarkSlot.Unit unit) {
        String exact = exact(amount, unit);
        if (exact.length() <= 9) return exact;
        var value = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(unit.numerator()))
                .divide(BigDecimal.valueOf(unit.denominator()), new MathContext(2, RoundingMode.DOWN));
        return "~" + value.stripTrailingZeros().toString().replace("E+", "e").replace("E", "e") +
                unit.symbol();
    }
    /** Short numeric notation, also used when constructing single-line quantity/unit labels. */
    public static String overlayNumber(long amount, BookmarkSlot.Unit unit) {
        var value = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(unit.numerator()))
                .divide(BigDecimal.valueOf(unit.denominator()), MathContext.DECIMAL128).stripTrailingZeros();
        String[] suffixes = {"", "K", "M", "G", "T", "P", "E"};
        int exponent = 0;
        while (exponent < 18 && value.movePointLeft(exponent).compareTo(BigDecimal.valueOf(1000)) >= 0) exponent += 3;
        if (value.movePointLeft(exponent).compareTo(BigDecimal.valueOf(1000)) < 0) {
            var scaled = value.movePointLeft(exponent);
            var rounded = scaled.setScale(3, RoundingMode.DOWN).stripTrailingZeros();
            String prefix = scaled.compareTo(rounded) == 0 ? "" : "~";
            if (rounded.signum() != 0) return prefix + rounded.toPlainString() + suffixes[exponent / 3];
        }
        return "~" + value.round(new MathContext(1, RoundingMode.DOWN)).stripTrailingZeros().toString().replace("E+", "e").replace("E", "e");
    }
    /** Prefer accurate notation; shorten decimal precision only when the actual font cannot fit it. */
    public static String overlayLabel(long amount, BookmarkSlot.Unit unit, java.util.function.Predicate<String> fits) {
        String number = overlayNumber(amount, unit);
        String joined = number + unit.symbol();
        if (fits.test(joined)) return joined;
        String fallback = joined;
        try {
            String unsigned = number.startsWith("~") ? number.substring(1) : number;
            String suffix = !unsigned.isEmpty() && "KMGTPE".indexOf(unsigned.charAt(unsigned.length() - 1)) >= 0 ?
                    unsigned.substring(unsigned.length() - 1) : "";
            String numeric = suffix.isEmpty() ? unsigned : unsigned.substring(0, unsigned.length() - 1);
            var value = new BigDecimal(numeric.replace(',', '.'));
            for (int decimals = Math.min(2, value.scale() - 1); decimals >= 0; decimals--) {
                var roundedValue = value.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros();
                if (roundedValue.signum() == 0 && value.signum() != 0) continue;
                String rounded = "~" + roundedValue.toPlainString() + suffix;
                joined = rounded + unit.symbol();
                if (fits.test(joined)) return joined;
                if (joined.length() < fallback.length()) fallback = joined;
            }
        } catch (NumberFormatException ignored) { /* Keep already compact scientific notation. */ }
        return fallback;
    }

}
