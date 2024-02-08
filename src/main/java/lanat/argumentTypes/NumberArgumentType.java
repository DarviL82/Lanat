package lanat.argumentTypes;

import lanat.ArgumentType;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * An abstract class for argument types that are numbers. This class provides an implementation
 * of {@link #parseValues(String[])} that will parse the first argument as a number using the
 * function returned by {@link #getParseFunction()}.
 * @param <T> The type of number that this argument type is.
 * @see Number
 */
public abstract class NumberArgumentType<T extends Number> extends ArgumentType<T> {
	/**
	 * Returns the function that will parse a string as a number. e.g. {@link Integer#parseInt(String)}.
	 * @return The function that will parse a string as a number.
	 */
	protected abstract @NotNull Function<@NotNull String, @NotNull T> getParseFunction();

	@Override
	public T parseValues(@NotNull String @NotNull [] values) {
		try {
			return this.getParseFunction().apply(values[0]);
		} catch (NumberFormatException e) {
			this.addError("Invalid " + this.getName() + " value: '" + values[0] + "'.");
			return null;
		}
	}
}