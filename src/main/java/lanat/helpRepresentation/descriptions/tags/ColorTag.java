package lanat.helpRepresentation.descriptions.tags;

import lanat.NamedWithDescription;
import lanat.helpRepresentation.descriptions.Tag;
import lanat.helpRepresentation.descriptions.exceptions.MalformedTagException;
import lanat.utils.displayFormatter.Color;
import lanat.utils.displayFormatter.FormatOption;
import lanat.utils.displayFormatter.TextFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.UtlString;

/**
 * Changes the color of the text. (e.g. {@code <color=red>}). The available colors are the ones defined in
 * {@link Color}. The color name is case-insensitive.
 * <p>
 * The names that may be used are:
 * <ul>
 * <li>black / k</li>
 * <li>red / r</li>
 * <li>green / g</li>
 * <li>yellow / y</li>
 * <li>blue / b</li>
 * <li>magenta / m</li>
 * <li>cyan / c</li>
 * <li>white / w</li>
 * <li>gray / grey / gr</li>
 * <li>dark red / dr</li>
 * <li>dark green / dg</li>
 * <li>dark yellow / dy</li>
 * <li>dark blue / db</li>
 * <li>dark magenta / dm</li>
 * <li>dark cyan / dc</li>
 * <li>dark white / dw</li>
 * </ul>
 *
 * <p>
 * The tag may receive a single color name, in which case the color will be applied to the foreground.
 * If the tag receives two color names, separated by a colon, the first color will be applied to the foreground and the
 * second color will be applied to the background. (e.g. {@code <color=red:blue>}).
 * </p>
 * <p>
 * If the color name is invalid, a {@link MalformedTagException} is thrown.
 * If no color is specified, the reset sequence is returned. (e.g. {@code <color>}).
 * </p>
 */
public class ColorTag extends Tag {
	@Override
	protected @NotNull String parse(@NotNull NamedWithDescription user, @Nullable String value) {
		if (!TextFormatter.enableSequences) return "";
		if (value == null) return FormatOption.RESET_ALL.seq();

		if (!value.contains(":")) return ColorTag.getColor(value).fg();

		final String[] split = UtlString.split(value, ':');
		if (split.length != 2)
			throw new MalformedTagException(
				ColorTag.class, "invalid color format '" + value + "' (expected format: 'foreground:background')"
			);

		return ColorTag.getColor(split[0]).fg() + ColorTag.getColor(split[1]).bg();
	}

	private static Color getColor(@NotNull String colorName) {
		return switch (colorName.toLowerCase().strip().replaceAll("[_-]", " ")) {
			case "black", "k" -> Color.BLACK;
			case "red", "r" -> Color.BRIGHT_RED;
			case "green", "g" -> Color.BRIGHT_GREEN;
			case "yellow", "y" -> Color.BRIGHT_YELLOW;
			case "blue", "b" -> Color.BRIGHT_BLUE;
			case "magenta", "m" -> Color.BRIGHT_MAGENTA;
			case "cyan", "c" -> Color.BRIGHT_CYAN;
			case "white", "w" -> Color.BRIGHT_WHITE;
			case "gray", "grey", "gr" -> Color.GRAY;
			case "dark red", "dr" -> Color.RED;
			case "dark green", "dg" -> Color.GREEN;
			case "dark yellow", "dy" -> Color.YELLOW;
			case "dark blue", "db" -> Color.BLUE;
			case "dark magenta", "dm" -> Color.MAGENTA;
			case "dark cyan", "dc" -> Color.CYAN;
			case "dark white", "dw" -> Color.WHITE;
			default -> throw new MalformedTagException(ColorTag.class, "unknown color name '" + colorName + "'");
		};
	}
}
