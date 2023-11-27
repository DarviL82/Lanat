package lanat.helpRepresentation.descriptions;

import lanat.NamedWithDescription;
import lanat.helpRepresentation.descriptions.exceptions.UnknownTagException;
import lanat.helpRepresentation.descriptions.tags.ColorTag;
import lanat.helpRepresentation.descriptions.tags.DescTag;
import lanat.helpRepresentation.descriptions.tags.FormatTag;
import lanat.helpRepresentation.descriptions.tags.LinkTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.UtlReflection;
import utils.UtlString;

import java.util.Hashtable;
import java.util.Map;


/**
 * Class for handling parsing of the simple tags used in descriptions. (e.g. {@code <a-tag=the-value>}). Tags may
 * receive no value, in which case the value received by the {@link #parse(NamedWithDescription, String)} method will be
 * {@code null}.
 *
 * @see #parse(NamedWithDescription, String)
 */
public abstract class Tag {
	private static final Hashtable<String, Class<? extends Tag>> REGISTERED_TAGS = new Hashtable<>();


	/**
	 * This method will parse the tag value and return the parsed value.
	 *
	 * @param user user that is parsing the tag
	 * @param value value of the tag. May be {@code null} if the tag has no value specified. (e.g. {@code <a-tag>})
	 * @return parsed value of the tag
	 * @see Tag
	 */
	protected abstract @NotNull String parse(@NotNull NamedWithDescription user, @Nullable String value);


	/** Initialize the tags. This method will register the default tags that are used in descriptions. */
	public static void initTags() {
		Tag.register("link", LinkTag.class);
		Tag.register("desc", DescTag.class);
		Tag.register("color", ColorTag.class);
		Tag.register("format", FormatTag.class);
	}

	public static String getTagNameFromTagClass(Class<? extends Tag> tagClass) {
		return Tag.REGISTERED_TAGS.entrySet().stream()
			.filter(entry -> entry.getValue() == tagClass)
			.findFirst()
			.map(Map.Entry::getKey)
			.orElseThrow(() ->
				new IllegalStateException("Tag class '" + tagClass.getName() + "' is not registered")
			);
	}

	/**
	 * Register a tag class to be used in descriptions. This class will be instantiated to parse the tags encountered in
	 * the descriptions being parsed.
	 *
	 * @param name name of the tag (case-insensitive). Must only contain lowercase letters and dashes.
	 * @param tag tag object that will be used to parse the tag
	 */
	public static void register(@NotNull String name, @NotNull Class<? extends Tag> tag) {
		if (!Tag.isValidTagName(name))
			throw new IllegalArgumentException("Tag name must only contain lowercase letters and dashes");
		Tag.REGISTERED_TAGS.put(name.toLowerCase(), tag);
	}

	/**
	 * Parse a tag value. This method will parse the tag value using the tag registered with the given name.
	 *
	 * @param user user that is parsing the tag
	 * @param tagName name of the tag
	 * @param value value of the tag
	 * @return parsed value of the tag
	 */
	static @NotNull String parseTagValue(
		@NotNull NamedWithDescription user,
		@NotNull String tagName,
		@Nullable String value
	)
	{
		final var tagClass = Tag.REGISTERED_TAGS.get(tagName.toLowerCase());

		if (tagClass == null)
			throw new UnknownTagException(tagName);

		return UtlReflection.instantiate(tagClass).parse(user, value);
	}

	/**
	 * Returns {@code true} if the given name is a valid tag name. A valid tag name must:
	 * <ul>
	 * <li>Not be blank</li>
	 * <li>Start and end with a letter</li>
	 * <li>Only contain letters and dashes</li>
	 * </ul>
	 * @param name The name to check.
	 * @return {@code true} if the given name is a valid tag name.
	 */
	private static boolean isValidTagName(@NotNull String name) {
		return !name.isBlank()
			&& Character.isAlphabetic(name.charAt(0))
			&& Character.isAlphabetic(name.charAt(name.length() - 1))
			&& UtlString.allCharsMatch(name, c -> Character.isAlphabetic(c) || c == '-');
	}
}