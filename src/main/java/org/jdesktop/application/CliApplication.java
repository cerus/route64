/* $Id: 4265235497758aebd4020c764e9d3d37d5bd2915 $
 *
 * Released under Gnu Public License
 * Copyright © 2013-2019 Michael G. Binz
 */
package org.jdesktop.application;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdesktop.util.JavaUtil;
import org.smack.util.StringUtil;
import org.smack.util.collections.MultiMap;

/**
 * @author MICKARG
 * @author MICBINZ
 * @deprecated moved
 * A base class for console applications.
 */
@Deprecated
abstract public class CliApplication {

    private static final Logger LOG =
            Logger.getLogger(CliApplication.class.getName());
    private static final HashMap<Class<?>, StringConverter<?>> _converters =
            new HashMap<>();

    static {
        addConverter(
                String.class,
                (s) -> s);
        addConverter(
                Byte.TYPE,
                CliApplication::stringToByte);
        addConverter(
                Short.TYPE,
                CliApplication::stringToShort);
        addConverter(
                Integer.TYPE,
                CliApplication::stringToInt);
        addConverter(
                Long.TYPE,
                CliApplication::stringToLong);
        addConverter(
                File.class,
                CliApplication::stringToFile);
        addConverter(
                Boolean.TYPE,
                CliApplication::stringToBoolean);
        addConverter(
                Float.TYPE,
                CliApplication::stringToFloat);
        addConverter(
                Double.TYPE,
                CliApplication::stringToDouble);
    }

    /**
     * A map of all commands implemented by this cli. Keys are
     * command name and number of arguments, the value represents
     * the respective method.
     */
    private final MultiMap<String, Integer, Method> _commandMap =
            getCommandMap_(this.getClass());
    private String _currentCommand =
            StringUtil.EMPTY_STRING;

    protected final static void addConverter(final Class<?> cl, final StringConverter<?> c) {
        _converters.put(cl, c);
    }

    /**
     * Start execution of the console command. This implicitly parses
     * the parameters and dispatches the call to the matching operation.
     * <p>
     * The main operation of an application using {@link #CliApplication()} usually looks like:
     * </p>
     *
     * <pre>
     * <code>
     * public class Lin extends CliApplication implements ConsoleCommand
     * {
     *     ...
     *
     *     public static void main( String[] argv )
     *     {
     *         execute( Lin.class, argv, true );
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param cl           The implementation class of the console command.
     * @param argv         The unmodified parameter array.
     * @param explicitExit If {@code true} is passed then an explicit
     *                     {@code System.exit(0)} is performed after the application command
     *                     terminates.
     */
    static public void launch(final Class<? extends CliApplication> cl, final String[] argv) {
        launch(
                new DefaultCtorReflection<>(cl),
                argv);
    }

    /**
     * Start execution of the console command. This implicitly parses
     * the parameters and dispatches the call to the matching operation.
     * <p>
     * The main operation of an application using {@link #CliApplication()} usually looks like:
     * </p>
     *
     * <pre>
     * <code>
     * public class Lin extends CliApplication implements ConsoleCommand
     * {
     *     ...
     *
     *     public static void main( String[] argv )
     *     {
     *         execute( Lin.class, argv, true );
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param cl           The implementation class of the console command.
     * @param argv         The unmodified parameter array.
     * @param explicitExit If {@code true} is passed then an explicit
     *                     {@code System.exit(0)} is performed after the application command
     *                     terminates.
     */
    static public void launch(final Supplier<CliApplication> cl, final String[] argv) {
        try {
            cl.get().launchInstance(argv);
        } catch (final RuntimeException e) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.getClass().getName();
            }

            LOG.log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace();
        } catch (final Exception e) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.getClass().getName();
            }

            LOG.log(Level.FINE, msg, e);
            System.err.println("Failed: " + msg);
        }
    }

    /**
     * Get String for error handling with correct function calls.
     *
     * @param commands Map with all possible commands.
     * @param argv     Argument list as String.
     *
     * @return Usage message for error handling.
     */
    private static String getCommandsUsage(final Map<Integer, Method> commands, final String[] argv) {

        final Set<Integer> keys = commands.keySet();
        final StringBuilder result = new StringBuilder();

        result.append("Possible values:");

        for (final Iterator<Integer> it = keys.iterator(); it.hasNext(); ) {
            result.append("\n");
            final Integer paramCount = it.next();
            final Class<?>[] params = commands.get(paramCount)
                    .getParameterTypes();

            for (int i = 0; i < params.length; i++) {
                result.append(params[i].getSimpleName());
                result.append(" ");
            }
        }

        return result.toString();
    }

    /**
     * Get a map of all commands that allows to access a single command based on
     * its name and argument list.
     */
    private static MultiMap<String, Integer, Method> getCommandMap_(
            final Class<?> targetClass) {
        final MultiMap<String, Integer, Method> result = new MultiMap<>();

        for (final Method c : targetClass.getDeclaredMethods()) {
            final Command commandAnnotation =
                    c.getAnnotation(Command.class);
            if (commandAnnotation == null) {
                continue;
            }

            String name = commandAnnotation.name();
            if (StringUtil.isEmpty(name)) {
                name = c.getName();
            }

            name = name.toLowerCase();

            for (final Class<?> current : c.getParameterTypes()) {
                if (current.isEnum()) {
                    continue;
                }

                Objects.requireNonNull(
                        _converters.get(current),
                        "No mapper for " + current);
            }

            final Integer numberOfArgs =
                    Integer.valueOf(c.getParameterTypes().length);

            // Check if we already have this command with the same parameter
            // list length. This would be an implementation error.
            if (result.get(name, numberOfArgs) != null) {
                throw new InternalError(
                        "Implementation error. Operation " +
                                name +
                                " with " +
                                numberOfArgs +
                                " parameters is not unique.");
            }

            result.put(name, numberOfArgs, c);
        }

        return result;
    }

    /**
     * Transform function for a primitive byte.
     */
    private static byte stringToByte(final String arg) throws Exception {
        try {
            return Byte.decode(arg).byteValue();
        } catch (final NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive short.
     */
    private static short stringToShort(final String arg) throws Exception {
        try {
            return Short.decode(arg).shortValue();
        } catch (final NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive integer.
     */
    private static int stringToInt(final String arg) throws Exception {
        try {
            // The long conversion is deliberately used
            // to be able to convert 32bit unsigned integers
            // like 0xffffffe8.
            return Long.decode(arg).intValue();
        } catch (final NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive long.
     */
    private static long stringToLong(final String arg) throws Exception {
        try {
            return Long.decode(arg).longValue();
        } catch (final NumberFormatException e) {
            throw new Exception("Decimal: [0-9]..., Hexadecimal: 0x[0-F]...");
        }
    }

    /**
     * Transform function for a primitive long.
     */
    private static float stringToFloat(final String arg) throws Exception {
        try {
            return Float.parseFloat(arg);
        } catch (final NumberFormatException e) {
            throw new Exception("Not a float: " + arg);
        }
    }

    /**
     * Transform function for a primitive long.
     */
    private static double stringToDouble(final String arg) throws Exception {
        try {
            return Double.parseDouble(arg);
        } catch (final NumberFormatException e) {
            throw new Exception("Not a double: " + arg);
        }
    }

    /**
     * Transform function for File. This ensures that the file exists.
     *
     * @param fileName The name of the file.
     *
     * @return A reference to a file instance if one exists.
     *
     * @throws Exception If the file does not exist.
     */
    protected static File stringToFile(final String fileName) throws Exception {

        final File file = new File(fileName);

        if (!file.exists()) {
            throw new Exception("File not found: " + file);
        }

        return file;
    }

    /**
     * Transform a string to a boolean.
     *
     * @param arg Accepts case independent 'TRUE' and 'FALSE' as valid strings.
     *
     * @return The corresponding boolean.
     *
     * @throws Exception In case of a conversion error.
     */
    protected static boolean stringToBoolean(final String arg) throws Exception {
        if (Boolean.TRUE.toString().equalsIgnoreCase(arg)) {
            return true;
        } else if (Boolean.FALSE.toString().equalsIgnoreCase(arg)) {
            return false;
        }

        throw new Exception(
                "Expected boolean: true or false. Received '" + arg + "'.");
    }

    protected final String currentCommand() {
        return this._currentCommand;
    }

    /**
     * A fallback called if no command was passed or the passed command was
     * unknown. This can be overridden for very simple cli implementations
     * that do not want or need to support the CliApplication command dispatch.
     * The default implementation tries to match the passed arguments to a
     * more specific typed defaultCmd() operation provided by the implementer.
     *
     * @param argv The received command line.
     *
     * @throws Exception In case of errors.
     */
    protected void defaultCmd(final String[] argv) throws Exception {
        // Default behavior without any arguments is printing the
        // normal '?' help.
        if (argv.length == 0) {
            System.err.println(this.usage());
            return;
        }

        // Support only *public* methods for now.
        final Method[] methods = this.getClass().getMethods();
        Method candidate = null;

        for (int i = 0; i < methods.length; i++) {
            final Method c = methods[i];

            if (!"defaultCmd".equals(c.getName())) {
                continue;
            }
            // Ignore this method (the one we are in).
            if (!c.getDeclaringClass().equals(this.getClass())) {
                continue;
            }
            if (c.getParameterTypes().length != argv.length) {
                continue;
            }

            if (candidate != null) {
                throw new InternalError("defaultCmd options ambiguous.");
            }

            candidate = c;
        }

        if (candidate != null) {
            this.executeCommand(candidate, argv);
            return;
        }

        final String[] commands = this.listCommands();

        if (commands.length > 0) {
            this.err(
                    "Could not find subcommand '%s'. Allowed are: %s\n",
                    argv[0],
                    StringUtil.concatenate(", ", commands));

            return;
        }

        System.err.println(this.usage());
    }

    /**
     * Return the list of possible commands in plain text, e.g. "Jan", "Feb".
     */
    private String[] listCommands() {
        final Set<String> collector = this._commandMap.getPrimaryKeys();

        final String[] result = collector.toArray(
                new String[collector.size()]);

        Arrays.sort(result);
        return result;
    }

    /**
     * Perform the launch of the cli instance.
     */
    private void launchInstance(final String[] argv) throws Exception {
        if (argv.length == 0) {
            this.defaultCmd(argv);
            return;
        }

        if (argv.length == 1 && argv[0].equals("?")) {
            System.err.println(this.usage());
            return;
        }

        final Method selectedCommand = this._commandMap.get(
                argv[0].toLowerCase(),
                Integer.valueOf(argv.length - 1));

        if (selectedCommand != null) {
            // We found a matching command.
            this._currentCommand =
                    this.getCommandName(selectedCommand);
            this.executeCommand(
                    selectedCommand,
                    Arrays.copyOfRange(argv, 1, argv.length));
            return;
        }

        // No command matched, so we check if there are commands
        // where at least the command name matches.
        final Map<Integer, Method> possibleCommands =
                this._commandMap.getAll(argv[0]);
        if (possibleCommands.size() > 0) {
            System.err.println(
                    getCommandsUsage(possibleCommands, argv));
            return;
        }

        // Nothing matched, we forward this to default handling.
        this.defaultCmd(argv);
    }

    /**
     * Generate help text for a method.
     */
    private String usage(final Method command) {
        final StringBuilder info = new StringBuilder();

        info.append(this.getCommandName(command));

        String optional =
                this.getCommandParameterList(command);
        if (StringUtil.hasContent(optional)) {
            info.append(": ");
            info.append(optional);
        }
        info.append("\n");

        optional =
                this.getCommandDescription(command);
        if (StringUtil.hasContent(optional)) {
            info.append("    ");
            info.append(optional);
            info.append("\n");
        }

        return info.toString();
    }

    /**
     * Usage function to get a dynamic help text with all available commands.
     * Can be overridden by the user for more detailed usage. Command
     * descriptions can be added in subclass as static String fields with
     * following name: functionName + DESCRIPTION_MARKER
     *
     * @return Usage text.
     */
    protected String usage() {
        final StringBuilder result =
                new StringBuilder(this.getApplicationName());
        {
            final String desc =
                    this.getApplicationDescription();
            if (StringUtil.hasContent(desc)) {
                result.append(" -- ");
                result.append(desc);
            }
        }
        result.append("\n\nThe following commands are supported:\n\n");

        for (final Method command : this.sort(this._commandMap.getValues())) {
            result.append(this.usage(command));
        }

        return result.toString();
    }

    /**
     * Helper operation to sort a collection of methods.
     *
     * @return A newly allocated list.
     */
    private List<Method> sort(final Collection<Method> methods) {
        final List<Method> result = new ArrayList<>(methods);

        Collections.sort(result, new Comparator<>() {
            @Override
            public int compare(final Method o1, final Method o2) {
                final int result =
                        o1.getName().compareTo(o2.getName());

                if (result != 0) {
                    return result;
                }

                return
                        o1.getParameterTypes().length -
                                o2.getParameterTypes().length;
            }
        });

        return result;
    }

    /**
     * Handle an exception thrown from a command. This default implementation
     * prints the exception message or, if this is empty, the exception name.
     * <p>In addition it tries to differentiate between implementation errors
     * and logical errors. RuntimeExceptions and Errors are handled as
     * implementation errors and printed including their stack trace.</p>
     *
     * @param e           The exception to handle.
     * @param commandName The name of the failing command.
     */
    protected void processCommandException(final String commandName, final Throwable e) {
        String msg = e.getMessage();

        if (StringUtil.isEmpty(msg)) {
            msg = e.getClass().getName();
        }

        if (e instanceof RuntimeException || e instanceof Error) {
            // Special handling of implementation or VM errors.
            this.err("%s failed.\n",
                    commandName);
            e.printStackTrace();
        } else {
            this.err("%s failed: %s\n",
                    commandName,
                    msg);
        }
    }

    /**
     * Execute the passed command with the given passed arguments. Each parameter
     * is transformed to the expected type.
     *
     * @param command Command to execute.
     * @param argv    List of arguments.
     */
    private void executeCommand(final Method command, final String[] argv) {
        final Object[] arguments =
                new Object[argv.length];
        final Class<?>[] params =
                command.getParameterTypes();

        if (argv.length != params.length) {
            throw new AssertionError();
        }

        for (int j = 0; j < params.length; j++) {
            try {
                arguments[j] = this.transformArgument(
                        params[j],
                        argv[j]);
            } catch (final Exception e) {
                this.err("Parameter %s : ", argv[j]);

                final String msg = e.getMessage();

                if (StringUtil.isEmpty(msg)) {
                    this.err(e.getClass().getSimpleName());
                } else {
                    this.err(msg);
                }

                return;
            }
        }

        try {

            if (!command.isAccessible()) {
                command.setAccessible(true);
            }

            command.invoke(this, arguments);
        } catch (final InvocationTargetException e) {
            this.processCommandException(command.getName(), e.getCause());
        } catch (final Exception e) {
            // A raw exception must come from our implementation,
            // so we present a less user friendly stacktrace.
            e.printStackTrace();
        } finally {
            // In case a parameter conversion operation created
            // 'closeable' objects, ensure that these get freed.
            for (final Object c : arguments) {
                if (c instanceof AutoCloseable) {
                    JavaUtil.force(((AutoCloseable) c)::close);
                }
            }
        }
    }

    private String getCommandName(final Method method) {
        final Command command = method.getAnnotation(Command.class);

        if (command != null && StringUtil.hasContent(command.name())) {
            return command.name();
        }

        return method.getName();
    }

    private String getCommandDescription(final Method method) {
        final Command command = method.getAnnotation(Command.class);

        if (command != null) {
            return command.shortDescription();
        }

        return StringUtil.EMPTY_STRING;
    }

    private String getCommandParameterList(final Method method) {
        final String[] list = this.getCommandParameterListExt(method);

        if (list.length == 0) {
            return StringUtil.EMPTY_STRING;
        }

        return StringUtil.concatenate(", ", list);
    }

    private String[] getCommandParameterListExt(final Method method) {
        final Class<?>[] parameterTypes =
                method.getParameterTypes();

        final Command command = method.getAnnotation(Command.class);

        // The old-style command parameter documentation has priority.
        if (command != null && command.argumentNames().length > 0) {
            if (command.argumentNames().length != parameterTypes.length) {
                LOG.warning("Command.argumentNames in consistent with " + method);
            }

            return command.argumentNames();
        }

        // The strategic way of defining parameter documentation.
        final String[] result = new String[method.getParameterCount()];
        int idx = 0;
        for (final Parameter c : method.getParameters()) {
            final Named named = c.getDeclaredAnnotation(Named.class);

            if (named != null && StringUtil.hasContent(named.value())) {
                result[idx] = named.value();
            } else if (c.getType().isEnum()) {
                result[idx] = this.getEnumDocumentation(c.getType());
            } else {
                result[idx] = c.getType().getSimpleName();
            }

            idx++;
        }

        return result;
    }

    private String getEnumDocumentation(final Class<?> c) {
        final List<String> enumNames = new ArrayList<>();

        for (final Object o : c.getEnumConstants()) {
            enumNames.add(o.toString());
        }

        if (enumNames.isEmpty()) {
            return StringUtil.EMPTY_STRING;
        }

        Collections.sort(enumNames);

        return
                "[" +
                        StringUtil.concatenate(", ", enumNames) +
                        "]";
    }

    /**
     * Get the application name. That is the name printed in the headline
     * of generated documentation. If the application is running from
     * CliConsole this is the name that has to specified on the command
     * line.  If the application is run via java -jar application.jar
     * then the returned value has only impact on the generated docs.
     * Add this information by applying the Named annotation on your
     * implementation class.
     *
     * @return The application name.
     */
    public String getApplicationName() {
        final Named annotation =
                this.getClass().getAnnotation(Named.class);

        if (annotation != null && StringUtil.hasContent(annotation.value())) {
            return annotation.value();
        }

        return this.getClass().getSimpleName();
    }

    /**
     * Get textual information on the overall console application.
     * Add this information by applying the Named annotation on your
     * implementation class.
     *
     * @return Overall application documentation.
     *
     * @see #getApplicationName()
     */
    protected String getApplicationDescription() {
        final Named annotation =
                this.getClass().getAnnotation(Named.class);

        if (annotation != null && StringUtil.hasContent(annotation.description())) {
            return annotation.description();
        }

        return StringUtil.EMPTY_STRING;
    }

    /**
     * Convert an argument string to a typed object. Uses
     * a special mapping for enums and the type map
     * for all other types.
     */
    private final Object transformArgument(
            Class<?> targetType,
            String argument)
            throws Exception {
        targetType =
                Objects.requireNonNull(targetType);
        argument =
                Objects.requireNonNull(argument);

        // Special handling for enums.
        if (targetType.isEnum()) {
            return this.transformEnum(targetType, argument);
        }

        final StringConverter<?> transformer =
                Objects.requireNonNull(
                        _converters.get(targetType),
                        "No mapper for " + targetType.getSimpleName());

        return transformer.convert(argument);
    }

    /**
     * Convert an argument to an enum instance.
     */
    private final Object transformEnum(
            final Class<?> targetEnum,
            final String argument)
            throws IllegalArgumentException {
        if (targetEnum == null) {
            throw new NullPointerException();
        }
        if (argument == null) {
            throw new NullPointerException();
        }
        if (!targetEnum.isEnum()) {
            throw new AssertionError();
        }

        // Handle enums.
        for (final Object c : targetEnum.getEnumConstants()) {
            if (c.toString().equalsIgnoreCase(argument)) {
                return c;
            }
        }

        // Above went wrong, generate a good message.
        final List<String> allowed = new ArrayList<>();
        for (final Object c : targetEnum.getEnumConstants()) {
            allowed.add(c.toString());
        }

        final String message = String.format(
                "Unknown enum value: '%s'.  Allowed values are %s.",
                argument,
                StringUtil.concatenate(", ", allowed));

        throw new IllegalArgumentException(message);
    }

    /**
     * Format the parameters to the standard error stream.
     *
     * @param fmt  The format string.
     * @param argv Format parameters.
     */
    protected final void err(final String fmt, final Object... argv) {
        System.err.printf(fmt, argv);
    }

    /**
     * Print to the standard error stream. Note that no
     * line feed is added.
     *
     * @param msg The message to print.
     */
    protected final void err(final String msg) {
        System.err.print(msg);
    }

    /**
     * Format the parameters to the standard output stream.
     *
     * @param fmt  The format string.
     * @param argv Format parameters.
     */
    protected final void out(final String fmt, final Object... argv) {
        System.out.printf(fmt, argv);
    }

    /**
     * Print to the standard output stream. Note that no
     * line feed is added.
     *
     * @param msg The message to print.
     */
    protected final void out(final String msg) {
        System.out.print(msg);
    }

    /**
     * Shorthand for System.in.
     *
     * @return The standard input stream.
     */
    protected final InputStream in() {
        return System.in;
    }

    /**
     * Used to mark cli command operations.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface Command {

        String name() default StringUtil.EMPTY_STRING;

        String[] argumentNames() default {};

        String shortDescription() default StringUtil.EMPTY_STRING;
    }

    /**
     * Used to add information on the implementation class.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.PARAMETER})
    public @interface Named {

        String value()
                default StringUtil.EMPTY_STRING;

        String description()
                default StringUtil.EMPTY_STRING;
    }

    public interface StringConverter<T> {

        T convert(String s) throws Exception;
    }

    private static class DefaultCtorReflection<T extends CliApplication>
            implements Supplier<CliApplication> {

        private final Class<T> _class;

        public DefaultCtorReflection(final Class<T> claß) {
            this._class = claß;
        }

        @Override
        public CliApplication get() {
            try {
                final Constructor<T> c = this._class.getDeclaredConstructor();
                return c.newInstance();
            } catch (final Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
