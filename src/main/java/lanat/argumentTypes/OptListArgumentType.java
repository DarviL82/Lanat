package lanat.argumentTypes;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An argument type that restricts a possible value out of a list of values.
 */
public class OptListArgumentType extends SingleValueListArgumentType<String> {
	/**
	 * Creates a new optional list argument type.
	 * @param values The list of values that the argument type will accept.
	 * @param initialValue The initial value of the argument type. This value must be one of the values.
	 */
	public OptListArgumentType(@NotNull List<String> values, @NotNull String initialValue) {
		super(values.toArray(String[]::new), initialValue);

		if (!values.contains(initialValue))
			throw new IllegalArgumentException("Initial value must be one of the values.");
	}

	/**
	 * Creates a new optional list argument type.
	 * @param values The list of values that the argument type will accept.
	 */
	public OptListArgumentType(@NotNull List<String> values) {
		super(values.toArray(String[]::new));
	}

	/**
	 * Creates a new optional list argument type.
	 * @param values The list of values that the argument type will accept.
	 */
	public OptListArgumentType(@NotNull String... values) {
		super(values);
	}
}