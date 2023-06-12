package lanat;

import lanat.exceptions.CommandAlreadyExistsException;
import lanat.exceptions.CommandTemplateException;
import lanat.helpRepresentation.HelpFormatter;
import lanat.parsing.Parser;
import lanat.parsing.Token;
import lanat.parsing.TokenType;
import lanat.parsing.Tokenizer;
import lanat.parsing.errors.CustomError;
import lanat.utils.*;
import lanat.utils.displayFormatter.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * <h2>Command</h2>
 * <p>
 * A command is a container for {@link Argument}s, other Sub{@link Command}s and {@link ArgumentGroup}s.
 *
 * @see ArgumentGroup
 * @see Argument
 */
public class Command
	extends ErrorsContainerImpl<CustomError>
	implements ErrorCallbacks<ParsedArguments, Command>,
	ArgumentAdder,
	ArgumentGroupAdder,
	CommandAdder,
	CommandUser,
	Resettable,
	MultipleNamesAndDescription,
	ParentElementGetter<Command>
{
	private final @NotNull List<@NotNull String> names = new ArrayList<>();
	public @Nullable String description;
	final @NotNull ArrayList<@NotNull Argument<?, ?>> arguments = new ArrayList<>();
	final @NotNull ArrayList<@NotNull Command> subCommands = new ArrayList<>();
	private Command parentCommand;
	final @NotNull ArrayList<@NotNull ArgumentGroup> argumentGroups = new ArrayList<>();
	private final @NotNull ModifyRecord<@NotNull TupleCharacter> tupleChars = new ModifyRecord<>(TupleCharacter.SQUARE_BRACKETS);
	private final @NotNull ModifyRecord<@NotNull Integer> errorCode = new ModifyRecord<>(1);

	// error handling callbacks
	private @Nullable Consumer<Command> onErrorCallback;
	private @Nullable Consumer<ParsedArguments> onCorrectCallback;

	private final @NotNull ModifyRecord<HelpFormatter> helpFormatter = new ModifyRecord<>(new HelpFormatter());
	private final @NotNull ModifyRecord<@NotNull CallbacksInvocationOption> callbackInvocationOption =
		new ModifyRecord<>(CallbacksInvocationOption.NO_ERROR_IN_ALL_COMMANDS);

	/** A pool of the colors that an argument may have when being represented on the help. */
	final @NotNull LoopPool<@NotNull Color> colorsPool = LoopPool.atRandomIndex(Color.getBrightColors());


	public Command(@NotNull String name, @Nullable String description) {
		this.addNames(name);
		this.description = description;
	}

	public Command(@NotNull String name) {
		this(name, null);
	}

	public Command(@NotNull Class<? extends CommandTemplate> templateClass) {
		this.from(templateClass);
	}

	@Override
	public <T extends ArgumentType<TInner>, TInner>
	void addArgument(@NotNull Argument<T, TInner> argument) {
		argument.registerToCommand(this);
		this.arguments.add(argument);
		this.checkUniqueArguments();
	}

	@Override
	public void addGroup(@NotNull ArgumentGroup group) {
		group.registerToCommand(this);
		this.argumentGroups.add(group);
		this.checkUniqueGroups();
	}

	@Override
	public @NotNull List<@NotNull ArgumentGroup> getGroups() {
		return Collections.unmodifiableList(this.argumentGroups);
	}

	@Override
	public void addCommand(@NotNull Command cmd) {
		if (cmd instanceof ArgumentParser) {
			throw new IllegalArgumentException("cannot add root command as Sub-Command");
		}

		if (cmd == this) {
			throw new IllegalArgumentException("cannot add command to itself");
		}

		cmd.registerToCommand(this);
		this.subCommands.add(cmd);
		this.checkUniqueSubCommands();
	}

	@Override
	public void registerToCommand(@NotNull Command parentCommand) {
		if (this.parentCommand != null) {
			throw new CommandAlreadyExistsException(this, this.parentCommand);
		}

		this.parentCommand = parentCommand;
	}

	/**
	 * Returns a list of all the Sub-Commands that belong to this command.
	 *
	 * @return a list of all the Sub-Commands in this command
	 */
	@Override
	public @NotNull List<@NotNull Command> getCommands() {
		return Collections.unmodifiableList(this.subCommands);
	}


	/**
	 * Specifies the error code that the program should return when this command failed to parse. When multiple commands
	 * fail, the program will return the result of the OR bit operation that will be applied to all other command
	 * results. For example:
	 * <ul>
	 *     <li>Command 'foo' has a return value of 2. <code>(0b010)</code></li>
	 *     <li>Command 'bar' has a return value of 5. <code>(0b101)</code></li>
	 * </ul>
	 * Both commands failed, so in this case the resultant return value would be 7 <code>(0b111)</code>.
	 */
	public void setErrorCode(int errorCode) {
		if (errorCode <= 0) throw new IllegalArgumentException("error code cannot be 0 or below");
		this.errorCode.set(errorCode);
	}

	public void setTupleChars(@NotNull TupleCharacter tupleChars) {
		this.tupleChars.set(tupleChars);
	}

	public @NotNull TupleCharacter getTupleChars() {
		return this.tupleChars.get();
	}

	@Override
	public void addNames(String... names) {
		Arrays.stream(names)
			.map(UtlString::requireValidName)
			.peek(newName -> {
				if (this.hasName(newName))
					throw new IllegalArgumentException("Name " + UtlString.surround(newName) + " is already used by this command.");
			})
			.forEach(this.names::add);

		// now let the parent command know that this command has been modified. This is necessary to check
		// for duplicate names
		if (this.parentCommand != null)
			this.parentCommand.checkUniqueSubCommands();
	}

	@Override
	public @NotNull List<String> getNames() {
		return this.names;
	}

	public void setDescription(@NotNull String description) {
		this.description = description;
	}

	@Override
	public @Nullable String getDescription() {
		return this.description;
	}

	public void setHelpFormatter(@NotNull HelpFormatter helpFormatter) {
		this.helpFormatter.set(helpFormatter);
	}

	public @NotNull HelpFormatter getHelpFormatter() {
		return this.helpFormatter.get();
	}

	/**
	 * Specifies in which cases the {@link Argument#setOnCorrectCallback(Consumer)} should be invoked.
	 * <p>By default, this is set to {@link CallbacksInvocationOption#NO_ERROR_IN_ALL_COMMANDS}.</p>
	 *
	 * @see CallbacksInvocationOption
	 */
	public void invokeCallbacksWhen(@NotNull CallbacksInvocationOption option) {
		this.callbackInvocationOption.set(option);
	}

	public @NotNull CallbacksInvocationOption getCallbackInvocationOption() {
		return this.callbackInvocationOption.get();
	}

	public void addError(@NotNull String message, @NotNull ErrorLevel level) {
		this.addError(new CustomError(message, level));
	}

	public @NotNull String getHelp() {
		return this.helpFormatter.get().generate(this);
	}

	@Override
	public @NotNull List<Argument<?, ?>> getArguments() {
		return Collections.unmodifiableList(this.arguments);
	}

	public @NotNull List<@NotNull Argument<?, ?>> getPositionalArguments() {
		return this.getArguments().stream().filter(Argument::isPositional).toList();
	}

	/**
	 * Returns {@code true} if an argument with {@link Argument#setAllowUnique(boolean)} in the command was used.
	 */
	public boolean uniqueArgumentReceivedValue() {
		return this.arguments.stream().anyMatch(a -> a.getUsageCount() >= 1 && a.isUniqueAllowed())
			|| this.subCommands.stream().anyMatch(Command::uniqueArgumentReceivedValue);
	}


	@Override
	public @NotNull String toString() {
		return "Command[name='%s', description='%s', arguments=%s, Sub-Commands=%s]"
			.formatted(
				this.getName(), this.description, this.arguments, this.subCommands
			);
	}

	@NotNull ParsedArguments getParsedArguments() {
		return new ParsedArguments(
			this,
			this.parser.getParsedArgumentsHashMap(),
			this.subCommands.stream().map(Command::getParsedArguments).toList()
		);
	}

	/**
	 * Get all the tokens of all Sub-Commands (the ones that we can get without errors) into one single list. This
	 * includes the {@link TokenType#COMMAND} tokens.
	 */
	public @NotNull ArrayList<@NotNull Token> getFullTokenList() {
		final ArrayList<Token> list = new ArrayList<>() {{
			this.add(new Token(TokenType.COMMAND, Command.this.getName()));
			this.addAll(Command.this.getTokenizer().getFinalTokens());
		}};

		final Command subCmd = this.getTokenizer().getTokenizedSubCommand();

		if (subCmd != null) {
			list.addAll(subCmd.getFullTokenList());
		}

		return list;
	}

	/**
	 * Inherits certain properties from another command, only if they are not already set to something.
	 */
	private void inheritProperties(@NotNull Command parent) {
		this.tupleChars.setIfNotModified(parent.tupleChars);
		this.getMinimumExitErrorLevel().setIfNotModified(parent.getMinimumExitErrorLevel());
		this.getMinimumDisplayErrorLevel().setIfNotModified(parent.getMinimumDisplayErrorLevel());
		this.errorCode.setIfNotModified(parent.errorCode);
		this.helpFormatter.setIfNotModified(parent.helpFormatter);
		this.callbackInvocationOption.setIfNotModified(parent.callbackInvocationOption);

		this.passPropertiesToChildren();
	}

	public void from(@NotNull Class<? extends CommandTemplate> cmdTemplate) {
		this.addNames(CommandTemplate.getTemplateNames(cmdTemplate));
		this.from$recursive(cmdTemplate);
	}

	private void from$recursive(@NotNull Class<?> cmdTemplate) {
		if (!CommandTemplate.class.isAssignableFrom(cmdTemplate)) return;

		// don't allow classes without the @Command.Define annotation
		if (!cmdTemplate.isAnnotationPresent(Command.Define.class)) {
			throw new CommandTemplateException("The class '" + cmdTemplate.getName()
				+ "' is not annotated with @Command.Define");
		}

		// get to the top of the hierarchy
		Optional.ofNullable(cmdTemplate.getSuperclass()).ifPresent(this::from$recursive);

		final var argumentBuilders = new ArrayList<ArgumentBuilder<?, ?>>();

		Stream.of(cmdTemplate.getDeclaredFields())
			.filter(f -> f.isAnnotationPresent(Argument.Define.class))
			.forEach(f -> {
				// if the argument is not already defined, add it
				argumentBuilders.add(ArgumentBuilder.fromField(f));
			});

		this.from$invokeBeforeInitMethod(cmdTemplate, argumentBuilders);

		// add the arguments to the command
		argumentBuilders.forEach(this::addArgument);

		this.from$invokeAfterInitMethod(cmdTemplate);
	}

	private void from$invokeBeforeInitMethod(
		@NotNull Class<?> cmdTemplate,
		@NotNull List<ArgumentBuilder<?, ?>> argumentBuilders
	) {
		Stream.of(cmdTemplate.getDeclaredMethods())
			.filter(m -> UtlReflection.hasParameters(m, CommandTemplate.CommandBuildHelper.class))
			.filter(m -> m.isAnnotationPresent(CommandTemplate.InitDef.class))
			.filter(m -> m.getName().equals("beforeInit"))
			.findFirst()
			.ifPresent(m -> {
				try {
					m.invoke(null, new CommandTemplate.CommandBuildHelper(
						this, Collections.unmodifiableList(argumentBuilders)
					));
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			});
	}

	private void from$invokeAfterInitMethod(@NotNull Class<?> cmdTemplate) {
		Stream.of(cmdTemplate.getDeclaredMethods())
			.filter(m -> UtlReflection.hasParameters(m, Command.class))
			.filter(m -> m.isAnnotationPresent(CommandTemplate.InitDef.class))
			.filter(m -> m.getName().equals("afterInit"))
			.findFirst()
			.ifPresent(m -> {
				try {
					m.invoke(null, this);
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			});
	}

	void passPropertiesToChildren() {
		this.subCommands.forEach(c -> c.inheritProperties(this));
	}

	/**
	 * Returns {@code true} if the argument specified by the given name is equal to this argument.
	 * <p>
	 * Equality is determined by the argument's name and the command it belongs to.
	 * </p>
	 *
	 * @param obj the argument to compare to
	 * @return {@code true} if the argument specified by the given name is equal to this argument
	 */
	@Override
	public boolean equals(@NotNull Object obj) {
		if (obj instanceof Command cmd)
			return UtlMisc.equalsByNamesAndParentCmd(this, cmd);
		return false;
	}

	void checkUniqueSubCommands() {
		UtlMisc.requireUniqueElements(this.subCommands, c -> new CommandAlreadyExistsException(c, this));
	}


	// ------------------------------------------------ Error Handling ------------------------------------------------

	@Override
	public void setOnErrorCallback(@Nullable Consumer<@NotNull Command> callback) {
		this.onErrorCallback = callback;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * By default this callback is called only if all commands succeed, but you can change this behavior with
	 * {@link Command#invokeCallbacksWhen(CallbacksInvocationOption)}
	 * </p>
	 */
	@Override
	public void setOnCorrectCallback(@Nullable Consumer<@NotNull ParsedArguments> callback) {
		this.onCorrectCallback = callback;
	}

	@Override
	public void invokeCallbacks() {
		this.subCommands.forEach(Command::invokeCallbacks);

		if (this.shouldExecuteCorrectCallback()) {
			if (this.onCorrectCallback != null) this.onCorrectCallback.accept(this.getParsedArguments());
		} else {
			if (this.onErrorCallback != null) this.onErrorCallback.accept(this);
		}

		this.parser.getParsedArgumentsHashMap()
			.entrySet()
			.stream()
			.sorted((x, y) -> Argument.compareByPriority(x.getKey(), y.getKey())) // sort by priority when invoking callbacks!
			.forEach(e -> e.getKey().invokeCallbacks(e.getValue()));
	}

	boolean shouldExecuteCorrectCallback() {
		return switch (this.getCallbackInvocationOption()) {
			case NO_ERROR_IN_COMMAND -> !this.hasExitErrorsNotIncludingSubCommands();
			case NO_ERROR_IN_COMMAND_AND_SUBCOMMANDS -> !this.hasExitErrors();
			case NO_ERROR_IN_ALL_COMMANDS -> !this.getRoot().hasExitErrors();
			case NO_ERROR_IN_ARGUMENT -> true;
		};
	}

	private boolean hasExitErrorsNotIncludingSubCommands() {
		return super.hasExitErrors()
			|| this.arguments.stream().anyMatch(Argument::hasExitErrors)
			|| this.parser.hasExitErrors()
			|| this.tokenizer.hasExitErrors();
	}

	@Override
	public boolean hasExitErrors() {
		var tokenizedSubCommand = this.getTokenizer().getTokenizedSubCommand();

		return this.hasExitErrorsNotIncludingSubCommands()
			|| tokenizedSubCommand != null && tokenizedSubCommand.hasExitErrors();
	}

	private boolean hasDisplayErrorsNotIncludingSubCommands() {
		return super.hasDisplayErrors()
			|| this.arguments.stream().anyMatch(Argument::hasDisplayErrors)
			|| this.parser.hasDisplayErrors()
			|| this.tokenizer.hasDisplayErrors();
	}

	@Override
	public boolean hasDisplayErrors() {
		var tokenizedSubCommand = this.getTokenizer().getTokenizedSubCommand();

		return this.hasDisplayErrorsNotIncludingSubCommands()
			|| tokenizedSubCommand != null && tokenizedSubCommand.hasDisplayErrors();
	}

	/**
	 * Get the error code of this Command. This is the OR of all the error codes of all the Sub-Commands that have
	 * failed.
	 *
	 * @return The error code of this command.
	 * @see #setErrorCode(int)
	 */
	public int getErrorCode() {
		int errCode = this.subCommands.stream()
			.filter(c -> c.tokenizer.isFinishedTokenizing())
			.map(sc ->
				sc.getMinimumExitErrorLevel().get().isInErrorMinimum(this.getMinimumExitErrorLevel().get())
					? sc.getErrorCode()
					: 0
			)
			.reduce(0, (a, b) -> a | b);

		/* If we have errors, or the Sub-Commands had errors, do OR with our own error level.
		 * By doing this, the error code of a Sub-Command will be OR'd with the error codes of all its parents. */
		if (this.hasExitErrors() || errCode != 0) {
			errCode |= this.errorCode.get();
		}

		return errCode;
	}


	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                         Argument tokenization and parsing    							      //
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private @NotNull Tokenizer tokenizer = new Tokenizer(this);
	private @NotNull Parser parser = new Parser(this);

	public @NotNull Tokenizer getTokenizer() {
		return this.tokenizer;
	}

	public @NotNull Parser getParser() {
		return this.parser;
	}

	void tokenize(@NotNull String input) {
		// this tokenizes recursively!
		this.tokenizer.tokenize(input);
	}

	void parseTokens() {
		// first we need to set the tokens of all tokenized subCommands
		Command cmd = this;
		do {
			cmd.parser.setTokens(cmd.tokenizer.getFinalTokens());
		} while ((cmd = cmd.getTokenizer().getTokenizedSubCommand()) != null);

		// this parses recursively!
		this.parser.parseTokens();
	}

	@Override
	public void resetState() {
		this.tokenizer = new Tokenizer(this);
		this.parser = new Parser(this);
		this.arguments.forEach(Argument::resetState);
		this.argumentGroups.forEach(ArgumentGroup::resetState);

		this.subCommands.forEach(Command::resetState);
	}

	@Override
	public @Nullable Command getParent() {
		return this.parentCommand;
	}

	@Override
	public @Nullable Command getParentCommand() {
		return this.getParent();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Define {
		String[] names() default {};

		String description() default "";
	}
}