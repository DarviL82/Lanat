package lanat.parsing;

import lanat.Argument;
import lanat.ArgumentType;
import lanat.Command;
import lanat.parsing.errors.CustomError;
import lanat.parsing.errors.ParseError;
import lanat.utils.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Parser extends ParsingStateBase<ParseError> {
	/**
	 * List of all the custom errors that have been added to this parser. Custom errors are thrown by
	 * {@link ArgumentType}s
	 */
	private final @NotNull ArrayList<@NotNull CustomError> customErrors = new ArrayList<>();

	/**
	 * Array of all the tokens that we have tokenized from the CLI arguments.
	 */
	private List<@NotNull Token> tokens;

	/**
	 * The index of the current token that we are parsing.
	 */
	private short currentTokenIndex = 0;

	/**
	 * Whether we are currently parsing values in a tuple.
	 */
	private boolean isInTuple = false;

	/**
	 * The parsed arguments. This is a map of the argument to the value that it parsed. The reason this is saved is that
	 * we don't want to run {@link Parser#getParsedArgumentsHashMap()} multiple times because that can break stuff badly
	 * in relation to error handling.
	 */
	private HashMap<@NotNull Argument<?, ?>, @Nullable Object> parsedArguments;


	public Parser(@NotNull Command command) {
		super(command);
	}



	/** Returns the index of the current token that is being parsed. */
	public short getCurrentTokenIndex() {
		return this.currentTokenIndex;
	}

	/** Sets the tokens that this parser will parse. */
	public void setTokens(@NotNull List<@NotNull Token> tokens) {
		this.tokens = tokens;
	}

	/**
	 * Parses the tokens that have been set. Delegates parsing of argument values to the {@link ArgumentType} of the
	 * argument that is being parsed.
	 */
	public void parseTokens() {
		assert this.tokens != null : "Tokens have not been set yet.";
		assert !this.hasFinished : "This parser has already finished parsing.";

		// number of positional arguments that have been parsed.
		// if this becomes -1, then we know that we are no longer parsing positional arguments
		short positionalArgCount = 0;
		Argument<?, ?> lastPositionalArgument; // this will never be null when being used

		for (this.currentTokenIndex = 0; this.currentTokenIndex < this.tokens.size(); ) {
			final Token currentToken = this.tokens.get(this.currentTokenIndex);

			if (currentToken.type() == TokenType.ARGUMENT_NAME) {
				// encountered an argument name. first skip the token of the name.
				this.currentTokenIndex++;
				// find the argument that matches that name and let it parse the values
				this.runForArgument(currentToken.contents(), this::executeArgParse);
				// we encountered an argument name, so we know that we are no longer parsing positional arguments
				positionalArgCount = -1;
			} else if (currentToken.type() == TokenType.ARGUMENT_NAME_LIST) {
				// in a name list, skip the first character because it is the indicator that it is a name list
				this.parseArgNameList(currentToken.contents().substring(1));
				positionalArgCount = -1;
			} else if (
				(currentToken.type() == TokenType.ARGUMENT_VALUE || currentToken.type() == TokenType.ARGUMENT_VALUE_TUPLE_START)
					&& positionalArgCount != -1
					&& (lastPositionalArgument = this.getArgumentByPositionalIndex(positionalArgCount)) != null
			) {
				// if we are here we encountered an argument value with no prior argument name or name list,
				// so this must be a positional argument
				this.executeArgParse(lastPositionalArgument);
				positionalArgCount++;
			} else {
				// addError depends on the currentTokenIndex, so we need to increment it before calling it
				this.currentTokenIndex++;

				if (currentToken.type() != TokenType.FORWARD_VALUE)
					this.addError(ParseError.ParseErrorType.UNMATCHED_TOKEN, null, 0);
			}
		}

		this.hasFinished = true;

		// now parse the Sub-Commands
		this.getCommands().stream()
			.filter(sb -> sb.getTokenizer().isFinishedTokenizing()) // only get the commands that were actually tokenized
			.forEach(sb -> sb.getParser().parseTokens()); // now parse them
	}

	/**
	 * Reads the next tokens and parses them as values for the given argument.
	 * <p>
	 * This keeps in mind the type of the argument, and will stop reading tokens when it reaches the max number of
	 * values, or if the end of a tuple is reached.
	 * </p>
	 */
	private void executeArgParse(@NotNull Argument<?, ?> arg) {
		final Range argNumValuesRange = arg.argType.getRequiredArgValueCount();

		// just skip the whole thing if it doesn't need any values
		if (argNumValuesRange.isZero()) {
			arg.parseValues(this.currentTokenIndex);
			return;
		}

		this.isInTuple = (
			this.currentTokenIndex < this.tokens.size()
				&& this.tokens.get(this.currentTokenIndex).type() == TokenType.ARGUMENT_VALUE_TUPLE_START
		);

		final byte ifTupleOffset = (byte)(this.isInTuple ? 1 : 0);

		final ArrayList<Token> values = new ArrayList<>();
		short numValues = 0;

		// add more values until we get to the max of the type, or we encounter another argument specifier
		for (
			int tokenIndex = this.currentTokenIndex + ifTupleOffset;
			tokenIndex < this.tokens.size();
			numValues++, tokenIndex++
		) {
			final Token currentToken = this.tokens.get(tokenIndex);
			if (!this.isInTuple && (
				currentToken.type().isArgumentSpecifier() || numValues >= argNumValuesRange.end()
			)
				|| currentToken.type().isTuple()
			) break;
			values.add(currentToken);
		}

		// add 2 if we are in a tuple, because we need to skip the start and end tuple tokens
		final int skipIndexCount = numValues + ifTupleOffset*2;

		if (numValues > argNumValuesRange.end() || numValues < argNumValuesRange.start()) {
			this.addError(ParseError.ParseErrorType.ARG_INCORRECT_VALUE_NUMBER, arg, numValues);
			this.currentTokenIndex += skipIndexCount;
			return;
		}

		// pass the arg values to the argument sub parser
		arg.parseValues(
			(short)(this.currentTokenIndex + ifTupleOffset),
			values.stream().map(Token::contents).toArray(String[]::new)
		);

		this.currentTokenIndex += skipIndexCount;
	}

	/**
	 * Parses the given string as an argument value for the given argument.
	 * <p>
	 * If the value passed in is present (not empty or {@code null}), the argument should only require 0 or 1 values.
	 * </p>
	 */
	private void executeArgParse(@NotNull Argument<?, ?> arg, @Nullable String value) {
		final Range argumentValuesRange = arg.argType.getRequiredArgValueCount();

		// just skip the whole thing if it doesn't need any values
		if (argumentValuesRange.isZero()) {
			arg.parseValues(this.currentTokenIndex);
			return;
		}

		if (argumentValuesRange.start() > 1) {
			this.addError(
				new ParseError(
					ParseError.ParseErrorType.ARG_INCORRECT_VALUE_NUMBER,
					this.currentTokenIndex + 1,
					arg,
					1
				) {{
					this.setIsInArgNameList(true); // set that the error was caused by an argument name list
				}}
			);
			return;
		}

		if (value == null || value.isEmpty()) {
			this.executeArgParse(arg); // value is not present in the suffix of the argList. Continue parsing values.
			return;
		}
		
		// pass the arg values to the argument subParser
		arg.parseValues(this.currentTokenIndex, value);
	}

	/**
	 * Parses the given string as a list of single-char argument names.
	 */
	private void parseArgNameList(@NotNull String args) {
		var doSkipToken = true; // atomic because we need to modify it in the lambda
		Argument<?, ?> lastArgument = null;

		// its multiple of them. We can only do this with arguments that accept 0 values.
		for (short i = 0; i < args.length(); i++) {
			var argument = this.getArgument(args.charAt(i));

			if (argument == null) {
				this.addError(
					ParseError.ParseErrorType.UNMATCHED_IN_ARG_NAME_LIST,
					lastArgument,
					i + 1, // substr for the current token
					this.currentTokenIndex + 1 // the next token is the one that caused the error
				);
				break;
			}

			// if the argument accepts 0 values, then we can just parse it like normal
			if (argument.argType.getRequiredArgValueCount().isZero()) {
				this.executeArgParse(argument);

				// -- arguments now may accept 1 or more values from now on:

				// if this argument is the last one in the list, then we can parse the next values after it
			} else if (i == args.length() - 1) {
				this.currentTokenIndex++;
				this.executeArgParse(argument);
				doSkipToken = false; // we don't want to skip the next token because executeArgParse already did that

				// if this argument is not the last one in the list, then we can parse the rest of the chars as the value
			} else {
				this.executeArgParse(argument, args.substring(i + 1));
			}

			lastArgument = argument;
		}

		if (doSkipToken) this.currentTokenIndex++;
	}

	/** Returns the positional argument at the given index of declaration. */
	private @Nullable Argument<?, ?> getArgumentByPositionalIndex(short index) {
		var posArgs = this.command.getPositionalArguments();

		if (index >= posArgs.size())
			return null;

		return posArgs.get(index);
	}

	/**
	 * Returns a hashmap of Arguments and their corresponding parsed values.
	 * This function invokes the {@link Argument#finishParsing()} method on each argument the first time it is called.
	 * After that, it will return the same hashmap.
	 * */
	public @NotNull HashMap<@NotNull Argument<?, ?>, @Nullable Object> getParsedArgumentsHashMap() {
		if (this.parsedArguments == null) {
			this.parsedArguments = new HashMap<>() {{
				Parser.this.getArguments().forEach(arg -> this.put(arg, arg.finishParsing()));
			}};
		}
		return this.parsedArguments;
	}


	// ------------------------------------------------ Error Handling ------------------------------------------------
	@Override
	public boolean hasExitErrors() {
		return super.hasExitErrors() || this.anyErrorInMinimum(this.customErrors, false);
	}

	@Override
	public boolean hasDisplayErrors() {
		return super.hasDisplayErrors() || this.anyErrorInMinimum(this.customErrors, true);
	}

	public @NotNull List<@NotNull CustomError> getCustomErrors() {
		return this.getErrorsInLevelMinimum(this.customErrors, true);
	}

	@Override
	public void addError(@NotNull ParseError error) {
		error.setIsInTuple(this.isInTuple); // set whether the error was caused while parsing values in a tuple
		super.addError(error);
	}

	public void addError(@NotNull ParseError.ParseErrorType type, @Nullable Argument<?, ?> arg, int argValueCount, int currentIndex) {
		this.addError(new ParseError(type, currentIndex, arg, argValueCount));
	}

	public void addError(@NotNull ParseError.ParseErrorType type, @Nullable Argument<?, ?> arg, int argValueCount) {
		this.addError(type, arg, argValueCount, this.currentTokenIndex);
	}

	public void addError(@NotNull CustomError customError) {
		this.customErrors.add(customError);
	}
}
