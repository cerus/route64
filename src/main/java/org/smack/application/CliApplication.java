/* $Id: a97dccb1aabab51167fabcb0efd34281327561a8 $
 *
 * Copyright © 2013-2019 Michael G. Binz
 */
package org.smack.application;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.smack.util.JavaUtil;
import org.smack.util.ReflectionUtil;
import org.smack.util.ServiceManager;
import org.smack.util.StringUtil;
import org.smack.util.collections.MultiMap;
import org.smack.util.converters.StringConverter;
import org.smack.util.converters.StringConverter.Converter;

/**
 * A base class for console applications.
 *
 * @author MICBINZ
 */
abstract public class CliApplication {

    private static final Logger LOG =
            Logger.getLogger(CliApplication.class.getName());
    /**
     * A name to be used if the command should be callable without
     * a dedicated name.
     */
    private static final CaseIndependent UNNAMED =
            new CaseIndependent("*");
    private static final StringConverter _converters =
            ServiceManager.getApplicationService(StringConverter.class);

    static {
        addConverter(
                File.class,
                CliApplication::stringToFile);
    }

    /**
     * A map of all commands implemented by this cli. Keys are
     * command name and number of arguments, the value represents
     * the respective method.
     */
    private final MultiMap<CaseIndependent, Integer, CommandHolder> _commandMap =
            this.getCommandMap(this.getClass());
    private final Map<String, PropertyHolder> _propertyMap =
            this.getPropertyMap(this);
    private String _currentCommand =
            StringUtil.EMPTY_STRING;

    protected final static <T> void addConverter(final Class<T> cl, final Converter<String, T> c) {
        _converters.put(cl, c);
    }

    /**
     * Start execution of the console command. This implicitly parses
     * the parameters and dispatches the call to the matching operation.
     * <p>
     * The main operation of an application using {@link #CliApplication()}
     * usually looks like:
     * </p>
     *
     * <pre>
     * <code>
     * public class Foo extends CliApplication
     * {
     *     ...
     *
     *     public static void main( String[] argv )
     *     {
     *         execute( Foo.class, argv, true );
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param cl   The implementation class of the console command.
     * @param argv The unmodified parameter array.
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
     * public class Duck extends CliApplication
     * {
     *     ...
     *
     *     public static void main( String[] argv )
     *     {
     *         execute( Duck.class, argv, true );
     *     }
     * }
     * </code>
     * </pre>
     *
     * @param cl   The implementation class of the console command.
     * @param argv The unmodified parameter array.
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
     * Transform function for File. This ensures that the file exists.
     *
     * @param fileName The name of the file.
     *
     * @return A reference to a file instance if one exists.
     *
     * @throws Exception If the file does not exist.
     */
    private static File stringToFile(final String fileName) throws Exception {

        final File file = new File(fileName);

        if (!file.exists()) {
            throw new Exception("File not found: " + file);
        }

        return file;
    }

    /**
     * Convert an argument string to a typed object. Uses
     * a special mapping for enums and the type map
     * for all other types.
     */
    private static final Object transformArgument(
            Class<?> targetType,
            String argument)
            throws Exception {
        targetType =
                Objects.requireNonNull(targetType);
        argument =
                Objects.requireNonNull(argument);

        final var transformer =
                Objects.requireNonNull(
                        _converters.getConverter(targetType),
                        "No mapper for " + targetType.getSimpleName());

        return transformer.convert(argument);
    }

    protected final String currentCommand() {
        return this._currentCommand;
    }

    /**
     * A fallback called if no command was passed or the passed command was
     * unknown.
     *
     * @param argv The received command line.
     *
     * @throws Exception In case of errors.
     */
    protected void defaultCmd(final String[] argv)
            throws Exception {
        this.err(this.usage());
    }

    /**
     * Perform the launch of the cli instance.
     */
    private void launchInstance(String[] argv)
            throws Exception {
        if (argv.length == 0) {
            this.defaultCmd(argv);
            return;
        }

        if (argv.length == 1 && argv[0].equals("?")) {
            this.err(this.usage());
            return;
        }

        argv = this.processProperties(argv);

        final var ciName =
                new CaseIndependent(argv[0]);

        CommandHolder selectedCommand = this._commandMap.get(
                ciName,
                Integer.valueOf(argv.length - 1));

        if (selectedCommand != null) {
            // We found a matching command.
            this._currentCommand =
                    selectedCommand.getName();
            selectedCommand.execute(
                    Arrays.copyOfRange(argv, 1, argv.length));
            return;
        }

        // No command matched, so we check if there are commands
        // where at least the command name matches.
        final Map<Integer, CommandHolder> possibleCommands =
                this._commandMap.getAll(ciName);
        if (possibleCommands.size() > 0) {
            this.err("%s%n",
                    "Parameter count does not match. Available alternatives:");
            this.err("%s%n",
                    this.getCommandsUsage(possibleCommands, argv));
            return;
        }

        // Check if we got an unnamed command.
        selectedCommand = this._commandMap.get(
                UNNAMED,
                argv.length);
        if (selectedCommand != null) {
            this._currentCommand =
                    selectedCommand.getName();
            selectedCommand.execute(
                    argv);
            return;
        }

        // No match.
        this.err("Unknown command '%s'.%n", ciName);
    }

    private void processProperty(String property)
            throws Exception {
        String value = null;
        final var equals =
                property.indexOf("=");
        if (equals > 0) {
            value = property.substring(equals + 1);
            property = property.substring(0, equals);
        }

        final var setter = this._propertyMap.get(
                property);
        if (setter == null) {
            throw new Exception("Unknown property: " + property);
        }

        if (setter.isBooleanType() && value == null) {
            value = "true";
        }

        setter.set(value);
    }

    /**
     * Check if the argument is a possible property name.
     * This filters arguments like '-313' which is *not*
     * an allowed property name.
     *
     * @param candidate A property name candidate.
     *
     * @return A valid property name or null if the
     * passed candidate was not an allowed property name.
     */
    private String getNameIfProperty(final String candidate) {
        if (StringUtil.isEmpty(candidate)) {
            return null;
        }

        if (!candidate.startsWith("-")) {
            return null;
        }

        try {
            Double.parseDouble(candidate);
        } catch (final NumberFormatException e) {
            return StringUtil.trim(candidate, "-");
        }

        return null;
    }

    /**
     * Processes the properties in the passed command line arguments.
     * Properties are qualified by a '-' or '--' prefix.
     *
     * @param argv The command line arguments.
     *
     * @return A newly allocated set of command line arguments with
     * properties removed.
     *
     * @throws Exception In case the conversion fails.
     */
    private String[] processProperties(final String[] argv)
            throws Exception {
        final var result = new ArrayList<String>();

        for (final var c : argv) {
            final var propertyName = this.getNameIfProperty(c);

            if (propertyName != null) {
                this.processProperty(propertyName);
            } else {
                result.add(c);
            }
        }

        return result.toArray(new String[result.size()]);
    }

    /**
     * Get String for error handling with correct function calls.
     *
     * @param commands Map with all possible commands.
     * @param argv     Argument list as String.
     *
     * @return Usage message for error handling.
     */
    private String getCommandsUsage(final Map<Integer, CommandHolder> commands, final String[] argv) {
        final StringBuilder result = new StringBuilder();

        commands.values().forEach(
                c -> result.append(c.usage()));

        return result.toString();
    }

    /**
     * Usage function to get a dynamic help text with all available commands.
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
        result.append(StringUtil.EOL);
        result.append("The following commands are supported:");
        result.append(StringUtil.EOL);

        for (final CommandHolder command : this.sort(this._commandMap.getValues())) {
            result.append(command.usage());
        }

        if (!this._propertyMap.isEmpty()) {
            result.append(StringUtil.EOL);
            result.append("Properties:");
            result.append(StringUtil.EOL);
            this._propertyMap.values().stream().sorted().forEach(c ->
            {
                result.append(c.usage());
                result.append(StringUtil.EOL);
            });
        }

        return result.toString();
    }

    /**
     * Helper operation to sort a collection of methods.
     *
     * @return A newly allocated list.
     */
    private List<CommandHolder> sort(final Collection<CommandHolder> methods) {
        final var result = new ArrayList<>(methods);

        Collections.sort(result, null);

        return result;
    }

    /**
     * Get a map of all commands that allows to access a single command based on
     * its name and argument list.
     */
    private MultiMap<CaseIndependent, Integer, CommandHolder> getCommandMap(
            final Class<?> targetClass) {
        final MultiMap<CaseIndependent, Integer, CommandHolder> result =
                new MultiMap<>();

        ReflectionUtil.processAnnotation(
                Command.class,
                targetClass::getDeclaredMethods,
                (c, a) -> {
                    String name = a.name();
                    if (StringUtil.isEmpty(name)) {
                        name = c.getName();
                    }

                    for (final Class<?> current : c.getParameterTypes()) {
                        Objects.requireNonNull(
                                _converters.getConverter(current),
                                "No mapper for " + current);
                    }

                    final Integer numberOfArgs =
                            Integer.valueOf(c.getParameterTypes().length);

                    final var currentName =
                            new CaseIndependent(name);
                    // Check if we already have this command with the same parameter
                    // list length. This is an implementation error.
                    if (result.get(currentName, numberOfArgs) != null) {
                        throw new InternalError(
                                "Implementation error. Operation " +
                                        name +
                                        " with " +
                                        numberOfArgs +
                                        " parameters is not unique.");
                    }

                    result.put(
                            currentName,
                            numberOfArgs,
                            new CommandHolder(c));
                });

        return result;
    }

    /**
     * Get a map of all commands that allows to access a single command based on
     * its name and argument list.
     */
    private Map<String, PropertyHolder> getPropertyMap(
            final Object targetInstance) {
        final var targetClass =
                targetInstance.getClass();
        final var result =
                new HashMap<String, PropertyHolder>();

        ReflectionUtil.processAnnotation(
                Property.class,
                targetClass::getDeclaredFields,
                (f, a) -> {
                    final var p = new PropertyHolder(f);
                    result.put(
                            p.getName(),
                            p);
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
            this.err("%s failed.%n",
                    commandName);
            e.printStackTrace();
        } else {
            this.err("%s failed: %s%n",
                    commandName,
                    msg);
        }
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

        /**
         * Use Named.
         */
        @Deprecated
        String[] argumentNames() default {};

        String shortDescription() default StringUtil.EMPTY_STRING;
    }

    /**
     * Used to mark cli properties.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    protected @interface Property {

        /**
         * Single dash arg.
         */
        String name() default StringUtil.EMPTY_STRING;

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

    private static class CaseIndependent {

        private final String _name;

        CaseIndependent(final String name) {
            this._name =
                    Objects.requireNonNull(name);
        }

        @Override
        public boolean equals(final Object obj) {
            if (null == obj) {
                return false;
            }
            if (obj == this) {
                return true;
            }

            return this._name.equalsIgnoreCase(obj.toString());
        }

        @Override
        public int hashCode() {
            return this._name.toLowerCase().hashCode();
        }

        @Override
        public String toString() {
            return this._name;
        }
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

    /**
     * Encapsulates a property.
     */
    private class PropertyHolder implements Comparable<PropertyHolder> {

        private final Field _field;
        private final Property _property;

        PropertyHolder(final Field field) {
            this._field = field;
            this._property = Objects.requireNonNull(
                    field.getAnnotation(Property.class));
        }

        String getName() {
            final String name = this._property.name();

            if (StringUtil.hasContent(name)) {
                return name;
            }

            return this._field.getName();
        }

        void set(final String value)
                throws Exception {
            final var self = CliApplication.this;

            this._field.set(
                    self,
                    transformArgument(
                            this._field.getType(),
                            value));
        }

        boolean isBooleanType() {
            return
                    ReflectionUtil.normalizePrimitives(this._field.getType()) == Boolean.class;
        }

        String usage() {
            final var type =
                    this._field.getType();
            final var typeDoc = type.isEnum() ?
                    CliApplication.this.getEnumDocumentation(type) :
                    type.getSimpleName();

            final var result = String.format(
                    "-%s=(%s)",
                    this.getName(),
                    typeDoc);

            final var description =
                    this._property.shortDescription();
            if (StringUtil.isEmpty(description)) {
                return result;
            }

            return result + " : " + description;
        }

        @Override
        public int compareTo(final PropertyHolder o) {
            final int result =
                    this.getName().compareTo(o.getName());

            return result;
        }
    }

    /**
     * Encapsulates a command.
     */
    private class CommandHolder implements Comparable<CommandHolder> {

        private final Method _op;
        private final Command _commandAnnotation;

        CommandHolder(final Method operation) {
            this._op =
                    operation;
            this._commandAnnotation =
                    Objects.requireNonNull(
                            this._op.getAnnotation(Command.class),
                            "@Command missing.");
        }

        String getName() {
            final var result = this._commandAnnotation.name();

            if (StringUtil.hasContent(result)) {
                return result;
            }

            return this._op.getName();
        }

        int getParameterCount() {
            return this._op.getParameterCount();
        }

        private String getDescription() {
            return this._commandAnnotation.shortDescription();
        }

        /**
         * Execute the passed command with the given passed arguments. Each parameter
         * is transformed to the expected type.
         *
         * @param command Command to execute.
         * @param argv    List of arguments.
         */
        private void execute(final String... argv) {
            final Object[] arguments =
                    new Object[argv.length];
            final Class<?>[] params =
                    this._op.getParameterTypes();

            if (argv.length != params.length) {
                throw new AssertionError();
            }

            for (int j = 0; j < params.length; j++) {
                try {
                    arguments[j] = transformArgument(
                            params[j],
                            argv[j]);
                } catch (final Exception e) {
                    CliApplication.this.err("Parameter %s : ", argv[j]);

                    String msg = e.getMessage();

                    if (StringUtil.isEmpty(msg)) {
                        msg = e.getClass().getSimpleName();
                    }

                    CliApplication.this.err("%s%n", msg);

                    return;
                }
            }

            try {
                final var self = CliApplication.this;

                if (!this._op.canAccess(self)) {
                    this._op.setAccessible(true);
                }

                this._op.invoke(
                        self,
                        arguments);
            } catch (final InvocationTargetException e) {
                CliApplication.this.processCommandException(this._op.getName(), e.getCause());
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

        private String getParameterList() {
            final String[] list = this.getCommandParameterListExt();

            if (list.length == 0) {
                return StringUtil.EMPTY_STRING;
            }

            return StringUtil.concatenate(", ", list);
        }

        private String[] getCommandParameterListExt() {
            final Class<?>[] parameterTypes =
                    this._op.getParameterTypes();

            // The old-style command parameter documentation has priority.
            if (this._commandAnnotation.argumentNames().length > 0) {
                if (this._commandAnnotation.argumentNames().length != parameterTypes.length) {
                    LOG.warning("Command.argumentNames inconsistent with " + this._op);
                }

                return this._commandAnnotation.argumentNames();
            }

            // The strategic way of defining parameter documentation.
            final String[] result = new String[this.getParameterCount()];
            int idx = 0;
            for (final Parameter c : this._op.getParameters()) {
                final Named named = c.getDeclaredAnnotation(Named.class);

                if (named != null && StringUtil.hasContent(named.value())) {
                    result[idx] = named.value();
                } else if (c.getType().isEnum()) {
                    result[idx] = CliApplication.this.getEnumDocumentation(c.getType());
                } else {
                    result[idx] = c.getType().getSimpleName();
                }

                idx++;
            }

            return result;
        }

        /**
         * Generate help text for a method.
         */
        private String usage() {
            final StringBuilder info = new StringBuilder();

            info.append(this.getName());

            String optional =
                    this.getParameterList();
            if (StringUtil.hasContent(optional)) {
                info.append(": ");
                info.append(optional);
            }
            info.append(StringUtil.EOL);

            optional =
                    this.getDescription();
            if (StringUtil.hasContent(optional)) {
                info.append("    ");
                info.append(optional);
                info.append(StringUtil.EOL);
            }

            return info.toString();
        }

        @Override
        public int compareTo(final CommandHolder o) {
            final int result =
                    this.getName().compareTo(o.getName());

            if (result != 0) {
                return result;
            }

            return
                    this.getParameterCount() -
                            o.getParameterCount();
        }
    }
}
