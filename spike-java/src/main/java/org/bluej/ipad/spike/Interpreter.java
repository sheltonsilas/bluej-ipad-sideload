package org.bluej.ipad.spike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * In-process bytecode interpreter for the subset a BlueJ introductory program uses.
 *
 * <p>Supported: integer and double arithmetic, comparisons and branches, loops, static and
 * instance fields, arrays, string constants and concatenation, a set of {@code java.lang}
 * and {@code java.io.PrintStream} intrinsics, object construction, method calls (including
 * recursion) and {@code try}/{@code catch} over the interpreter's own exception table.
 *
 * <p>Deliberately unsupported: {@code long}, {@code float}, threads, synchronization,
 * {@code invokedynamic} other than string concatenation, reflection, and native methods.
 * Unsupported bytecode fails explicitly with {@link UnsupportedOperationException} rather
 * than producing a wrong answer. This is not a JVM and does not claim JVM conformance.
 */
public final class Interpreter {
    private static final String PRINT_STREAM = "java.io.PrintStream";
    private static final int DEFAULT_INSTRUCTION_LIMIT = 1_000_000;

    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, Value> staticFields = new HashMap<>();
    private final Map<String, String> builtinSupertypes = new HashMap<>();
    private final java.util.Set<String> initialized = new java.util.HashSet<>();
    private final java.util.Set<String> initializing = new java.util.HashSet<>();
    private final StringBuilder output = new StringBuilder();
    private final InterpretedObject printStream = new InterpretedObject(PRINT_STREAM);
    private final int instructionLimit;
    private int executed;

    public Interpreter() { this(DEFAULT_INSTRUCTION_LIMIT); }

    public Interpreter(int instructionLimit) {
        if (instructionLimit < 1) throw new IllegalArgumentException("instructionLimit must be positive");
        this.instructionLimit = instructionLimit;
        registerBuiltinExceptions();
    }

    private void registerBuiltinExceptions() {
        builtinSupertypes.put("java.lang.ArithmeticException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.ArrayIndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException");
        builtinSupertypes.put("java.lang.StringIndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException");
        builtinSupertypes.put("java.lang.IndexOutOfBoundsException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.NullPointerException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.NegativeArraySizeException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.ClassCastException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.IllegalArgumentException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.NumberFormatException", "java.lang.IllegalArgumentException");
        builtinSupertypes.put("java.lang.IllegalStateException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.UnsupportedOperationException", "java.lang.RuntimeException");
        builtinSupertypes.put("java.lang.RuntimeException", "java.lang.Exception");
        builtinSupertypes.put("java.lang.Exception", "java.lang.Throwable");
        builtinSupertypes.put("java.lang.Error", "java.lang.Throwable");
        builtinSupertypes.put("java.lang.VirtualMachineError", "java.lang.Error");
        builtinSupertypes.put("java.lang.StackOverflowError", "java.lang.VirtualMachineError");
        builtinSupertypes.put("java.lang.OutOfMemoryError", "java.lang.VirtualMachineError");
    }

    // ---------------------------------------------------------------- loading

    public void load(Map<String, byte[]> bytecode) {
        bytecode.forEach(this::defineClass);
    }

    public void defineClass(String className, byte[] bytecode) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        classes.put(node.name.replace('/', '.'), node);
    }

    public void load(Path classFile) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(classFile)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        classes.put(node.name.replace('/', '.'), node);
    }

    public List<String> loadedClasses() { return List.copyOf(classes.keySet()); }

    /** Drops all loaded classes, static state and captured output. */
    public void reset() {
        classes.clear();
        staticFields.clear();
        initialized.clear();
        initializing.clear();
        output.setLength(0);
        executed = 0;
    }

    // ---------------------------------------------------------------- entry points

    public InterpretedObject newObject(String className, Value... arguments) {
        ClassNode type = requireClass(className);
        ensureInitialized(type);
        InterpretedObject object = instantiate(type);
        MethodNode constructor = findCompatibleMethod(type, "<init>", arguments);
        invokeMethod(object, type, constructor, arguments);
        return object;
    }

    public Value invoke(InterpretedObject target, String methodName, Value... arguments) {
        ClassNode type = requireClass(target.className());
        ensureInitialized(type);
        return invokeMethod(target, type, findCompatibleMethod(type, methodName, arguments), arguments);
    }

    public Value invokeStatic(String className, String methodName, Value... arguments) {
        ClassNode type = requireClass(className);
        ensureInitialized(type);
        return invokeMethod(null, type, findCompatibleMethod(type, methodName, arguments), arguments);
    }

    public Value staticField(String className, String fieldName) {
        ClassNode type = classes.get(className);
        if (type != null) ensureInitialized(type);
        return staticFields.getOrDefault(key(className, fieldName), Value.NullValue.INSTANCE);
    }

    public String output() { return output.toString(); }
    public void clearOutput() { output.setLength(0); }

    // ---------------------------------------------------------------- execution

    private InterpretedObject instantiate(ClassNode type) {
        InterpretedObject object = new InterpretedObject(type.name.replace('/', '.'));
        for (var field : type.fields) {
            if ((field.access & Opcodes.ACC_STATIC) == 0) {
                object.setField(field.name, defaultValue(Type.getType(field.desc)));
            }
        }
        return object;
    }

    private void ensureInitialized(ClassNode type) {
        String name = type.name.replace('/', '.');
        if (initialized.contains(name) || initializing.contains(name)) return;
        initializing.add(name);
        try {
            // Class preparation: every static field starts at its type's default value.
            for (var field : type.fields) {
                if ((field.access & Opcodes.ACC_STATIC) != 0) {
                    staticFields.putIfAbsent(key(name, field.name), defaultValue(Type.getType(field.desc)));
                }
            }
            type.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst()
                    .ifPresent(clinit -> invokeMethod(null, type, clinit, new Value[0]));
        } finally {
            initializing.remove(name);
            initialized.add(name);
        }
    }

    private Value invokeMethod(InterpretedObject receiver, ClassNode owner, MethodNode method, Value[] arguments) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw new UnsupportedOperationException("abstract or native method: " + owner.name + "." + method.name);
        }
        Type[] parameterTypes = Type.getArgumentTypes(method.desc);
        if (arguments.length != parameterTypes.length) {
            throw new IllegalArgumentException("wrong argument count for " + owner.name + "." + method.name
                    + ": expected " + parameterTypes.length + " but got " + arguments.length);
        }
        Value[] locals = new Value[Math.max(method.maxLocals, 1)];
        int local = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            locals[local] = new Value.ObjectValue(receiver);
            local += 1;
        }
        for (int i = 0; i < arguments.length; i++) {
            locals[local] = arguments[i];
            local += parameterTypes[i].getSize(); // double/long parameters occupy two slots
        }

        List<Value> stack = new ArrayList<>();
        AbstractInsnNode[] instructions = method.instructions.toArray();
        Map<LabelNode, Integer> labels = new HashMap<>();
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i] instanceof LabelNode label) labels.put(label, i);
        }

        int pc = 0;
        while (pc < instructions.length) {
            AbstractInsnNode insn = instructions[pc];
            int op = insn.getOpcode();
            if (op < 0) { pc++; continue; }
            if (++executed > instructionLimit) {
                throw new IllegalStateException("interpreter instruction limit exceeded (" + instructionLimit + ")");
            }
            try {
                switch (op) {
                    case Opcodes.NOP -> { }
                    case Opcodes.ACONST_NULL -> push(stack, Value.NullValue.INSTANCE);
                    case Opcodes.ICONST_M1 -> push(stack, new Value.IntValue(-1));
                    case Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                         Opcodes.ICONST_4, Opcodes.ICONST_5 -> push(stack, new Value.IntValue(op - Opcodes.ICONST_0));
                    case Opcodes.BIPUSH, Opcodes.SIPUSH -> push(stack, new Value.IntValue(((IntInsnNode) insn).operand));
                    case Opcodes.LDC -> push(stack, constant(((LdcInsnNode) insn).cst));
                    case Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD -> push(stack, locals[((VarInsnNode) insn).var]);
                    case Opcodes.DLOAD -> push(stack, locals[((VarInsnNode) insn).var]);
                    case Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> locals[((VarInsnNode) insn).var] = pop(stack);
                    case Opcodes.DSTORE -> locals[((VarInsnNode) insn).var] = pop(stack);
                    case Opcodes.IINC -> {
                        IincInsnNode inc = (IincInsnNode) insn;
                        locals[inc.var] = new Value.IntValue(asInt(locals[inc.var]) + inc.incr);
                    }
                    case Opcodes.POP -> pop(stack);
                    case Opcodes.POP2 -> { pop(stack); pop(stack); }
                    case Opcodes.DUP -> push(stack, peek(stack));
                    case Opcodes.DUP2 -> {
                        Value a = pop(stack);
                        Value b = pop(stack);
                        push(stack, b); push(stack, a); push(stack, b); push(stack, a);
                    }
                    case Opcodes.SWAP -> {
                        Value a = pop(stack); Value b = pop(stack);
                        push(stack, a); push(stack, b);
                    }
                    case Opcodes.IADD -> binaryInt(stack, (a, b) -> a + b);
                    case Opcodes.ISUB -> binaryInt(stack, (a, b) -> a - b);
                    case Opcodes.IMUL -> binaryInt(stack, (a, b) -> a * b);
                    case Opcodes.IDIV -> binaryInt(stack, Interpreter::divide);
                    case Opcodes.IREM -> binaryInt(stack, Interpreter::remainder);
                    case Opcodes.ISHL -> binaryInt(stack, (a, b) -> a << (b & 0x1f));
                    case Opcodes.ISHR -> binaryInt(stack, (a, b) -> a >> (b & 0x1f));
                    case Opcodes.IUSHR -> binaryInt(stack, (a, b) -> a >>> (b & 0x1f));
                    case Opcodes.IAND -> binaryInt(stack, (a, b) -> a & b);
                    case Opcodes.IOR -> binaryInt(stack, (a, b) -> a | b);
                    case Opcodes.IXOR -> binaryInt(stack, (a, b) -> a ^ b);
                    case Opcodes.INEG -> push(stack, new Value.IntValue(-asInt(pop(stack))));
                    case Opcodes.DADD -> binaryDouble(stack, (a, b) -> a + b);
                    case Opcodes.DSUB -> binaryDouble(stack, (a, b) -> a - b);
                    case Opcodes.DMUL -> binaryDouble(stack, (a, b) -> a * b);
                    case Opcodes.DDIV -> binaryDouble(stack, (a, b) -> a / b);
                    case Opcodes.DREM -> binaryDouble(stack, (a, b) -> a % b);
                    case Opcodes.DNEG -> push(stack, new Value.DoubleValue(-asDouble(pop(stack))));
                    case Opcodes.I2D -> push(stack, new Value.DoubleValue(asInt(pop(stack))));
                    case Opcodes.D2I -> push(stack, new Value.IntValue((int) asDouble(pop(stack))));
                    case Opcodes.I2B -> push(stack, new Value.IntValue((byte) asInt(pop(stack))));
                    case Opcodes.I2C -> push(stack, new Value.IntValue((char) asInt(pop(stack))));
                    case Opcodes.I2S -> push(stack, new Value.IntValue((short) asInt(pop(stack))));
                    case Opcodes.DCMPL, Opcodes.DCMPG -> {
                        double b = asDouble(pop(stack));
                        double a = asDouble(pop(stack));
                        int result = a > b ? 1 : a < b ? -1 : 0;
                        if (op == Opcodes.DCMPG && (Double.isNaN(a) || Double.isNaN(b))) result = 1;
                        if (op == Opcodes.DCMPL && (Double.isNaN(a) || Double.isNaN(b))) result = -1;
                        push(stack, new Value.IntValue(result));
                    }
                    case Opcodes.IFEQ -> { if (asInt(pop(stack)) == 0) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IFNE -> { if (asInt(pop(stack)) != 0) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IFLT -> { if (asInt(pop(stack)) < 0) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IFGE -> { if (asInt(pop(stack)) >= 0) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IFGT -> { if (asInt(pop(stack)) > 0) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IFLE -> { if (asInt(pop(stack)) <= 0) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT,
                         Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE -> {
                        int b = asInt(pop(stack));
                        int a = asInt(pop(stack));
                        boolean jump = switch (op) {
                            case Opcodes.IF_ICMPEQ -> a == b;
                            case Opcodes.IF_ICMPNE -> a != b;
                            case Opcodes.IF_ICMPLT -> a < b;
                            case Opcodes.IF_ICMPGE -> a >= b;
                            case Opcodes.IF_ICMPGT -> a > b;
                            default -> a <= b;
                        };
                        if (jump) { pc = labels.get(((JumpInsnNode) insn).label); continue; }
                    }
                    case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                        Value b = pop(stack);
                        Value a = pop(stack);
                        boolean equal = a.equals(b);
                        if ((op == Opcodes.IF_ACMPEQ) == equal) { pc = labels.get(((JumpInsnNode) insn).label); continue; }
                    }
                    case Opcodes.IFNULL -> { if (pop(stack) == Value.NullValue.INSTANCE) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.IFNONNULL -> { if (pop(stack) != Value.NullValue.INSTANCE) { pc = labels.get(((JumpInsnNode) insn).label); continue; } }
                    case Opcodes.GOTO -> { pc = labels.get(((JumpInsnNode) insn).label); continue; }
                    case Opcodes.TABLESWITCH -> {
                        TableSwitchInsnNode table = (TableSwitchInsnNode) insn;
                        int key = asInt(pop(stack));
                        int offset = key - table.min;
                        if (offset < 0 || offset >= table.labels.size()) pc = labels.get(table.dflt);
                        else pc = labels.get(table.labels.get(offset));
                        continue;
                    }
                    case Opcodes.LOOKUPSWITCH -> {
                        LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) insn;
                        int key = asInt(pop(stack));
                        int at = lookup.keys.indexOf(key);
                        pc = labels.get(at < 0 ? lookup.dflt : lookup.labels.get(at));
                        continue;
                    }
                    case Opcodes.IRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> { return pop(stack); }
                    case Opcodes.RETURN -> { return Value.NullValue.INSTANCE; }
                    case Opcodes.NEW -> push(stack, instantiateDynamic((TypeInsnNode) insn));
                    case Opcodes.NEWARRAY -> {
                        int length = asInt(pop(stack));
                        int typeCode = ((IntInsnNode) insn).operand;
                        push(stack, new Value.ArrayValue(new InterpretedArray(typeCode, primitiveName(typeCode), nonNegative(length))));
                    }
                    case Opcodes.ANEWARRAY -> {
                        int length = asInt(pop(stack));
                        String component = ((TypeInsnNode) insn).desc.replace('/', '.');
                        push(stack, new Value.ArrayValue(new InterpretedArray(0, component, nonNegative(length))));
                    }
                    case Opcodes.ARRAYLENGTH -> push(stack, new Value.IntValue(requireArray(pop(stack)).length()));
                    case Opcodes.IALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.AALOAD, Opcodes.FALOAD -> {
                        int index = asInt(pop(stack));
                        push(stack, requireArray(pop(stack)).get(index));
                    }
                    case Opcodes.DALOAD -> {
                        int index = asInt(pop(stack));
                        push(stack, requireArray(pop(stack)).get(index));
                    }
                    case Opcodes.IASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE, Opcodes.AASTORE, Opcodes.FASTORE, Opcodes.DASTORE -> {
                        Value value = pop(stack);
                        int index = asInt(pop(stack));
                        requireArray(pop(stack)).set(index, value);
                    }
                    case Opcodes.GETFIELD -> {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        push(stack, requireObject(pop(stack), field.name).getField(field.name));
                    }
                    case Opcodes.PUTFIELD -> {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        Value value = pop(stack);
                        requireObject(pop(stack), field.name).setField(field.name, value);
                    }
                    case Opcodes.GETSTATIC -> {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        push(stack, readStatic(field.owner.replace('/', '.'), field.name));
                    }
                    case Opcodes.PUTSTATIC -> {
                        FieldInsnNode field = (FieldInsnNode) insn;
                        writeStatic(field.owner.replace('/', '.'), field.name, pop(stack));
                    }
                    case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE -> {
                        MethodInsnNode call = (MethodInsnNode) insn;
                        Type[] argTypes = Type.getArgumentTypes(call.desc);
                        Value[] args = new Value[argTypes.length];
                        for (int i = args.length - 1; i >= 0; i--) args[i] = pop(stack);
                        Value callReceiver = op == Opcodes.INVOKESTATIC ? null : pop(stack);
                        Value result = callMethod(call, callReceiver, args);
                        if (Type.getReturnType(call.desc).getSort() != Type.VOID) push(stack, result);
                    }
                    case Opcodes.INVOKEDYNAMIC -> {
                        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                        Type[] argTypes = Type.getArgumentTypes(indy.desc);
                        Value[] indyArgs = new Value[argTypes.length];
                        for (int i = indyArgs.length - 1; i >= 0; i--) indyArgs[i] = pop(stack);
                        push(stack, concat(indy, indyArgs));
                    }
                    case Opcodes.ATHROW -> {
                        Value thrown = pop(stack);
                        if (thrown instanceof Value.ObjectValue object) {
                            throw new InterpretedThrowable(object.value().className(), messageOf(object.value()), object.value());
                        }
                        throw new InterpretedThrowable("java.lang.NullPointerException", "thrown a null exception reference");
                    }
                    case Opcodes.CHECKCAST -> { /* reference checks are structural; casts are not enforced yet */ }
                    case Opcodes.INSTANCEOF -> {
                        Value value = pop(stack);
                        String target = ((TypeInsnNode) insn).desc.replace('/', '.');
                        push(stack, new Value.IntValue(isInstanceOf(value, target) ? 1 : 0));
                    }
                    default -> throw unsupported(insn);
                }
            } catch (InterpretedThrowable thrown) {
                int handler = handlerFor(method, labels, pc, thrown.className());
                if (handler < 0) throw thrown;
                stack.clear();
                push(stack, thrown.object() == null
                        ? new Value.ObjectValue(syntheticException(thrown.className(), thrown.getMessage()))
                        : new Value.ObjectValue(thrown.object()));
                pc = handler;
                continue;
            }
            pc++;
        }
        return Value.NullValue.INSTANCE;
    }

    private Value instantiateDynamic(TypeInsnNode insn) {
        String name = insn.desc.replace('/', '.');
        if (isThrowable(name)) return new Value.ObjectValue(new InterpretedObject(name));
        if (name.equals("java.lang.StringBuilder") || name.equals("java.lang.StringBuffer")) {
            InterpretedObject builder = new InterpretedObject(name);
            builder.setField("value", new Value.StringValue(""));
            return new Value.ObjectValue(builder);
        }
        ClassNode type = requireClass(name);
        ensureInitialized(type);
        return new Value.ObjectValue(instantiate(type));
    }

    private Value callMethod(MethodInsnNode call, Value receiver, Value[] arguments) {
        String owner = call.owner.replace('/', '.');
        Value intrinsic = invokeIntrinsic(owner, call, receiver, arguments);
        if (intrinsic != NO_INTRINSIC) return intrinsic;
        if (owner.equals("java.lang.Object") && call.name.equals("<init>")) return Value.NullValue.INSTANCE;
        if (isThrowable(owner)) {
            if (call.name.equals("<init>")) {
                if (receiver instanceof Value.ObjectValue object && arguments.length == 1
                        && arguments[0] instanceof Value.StringValue message) {
                    object.value().setField("message", message);
                }
                return Value.NullValue.INSTANCE;
            }
            if (call.name.equals("getMessage") || call.name.equals("toString")) {
                if (receiver instanceof Value.ObjectValue object) {
                    return new Value.StringValue(messageOf(object.value()));
                }
                throw new InterpretedThrowable("java.lang.NullPointerException", null);
            }
        }
        ClassNode type = requireClass(owner);
        ensureInitialized(type);
        MethodNode method = findMethodByDescriptor(type, call.name, call.desc);
        InterpretedObject target = receiver == null ? null : requireObject(receiver, call.name);
        return invokeMethod(target, type, method, arguments);
    }

    // ---------------------------------------------------------------- intrinsics

    private static final Value NO_INTRINSIC = new Value.StringValue("\u0000no-intrinsic");

    private Value invokeIntrinsic(String owner, MethodInsnNode call, Value receiver, Value[] args) {
        String name = call.name;
        Value result = switch (owner) {
            case PRINT_STREAM -> {
                String text = args.length == 0 ? "" : stringify(args[0]);
                output.append(text);
                if (name.equals("println")) output.append('\n');
                yield Value.NullValue.INSTANCE;
            }
            case "java.lang.String" -> {
                String text = requireString(receiver, name);
                yield switch (name) {
                    case "length" -> new Value.IntValue(text.length());
                    case "isEmpty" -> new Value.IntValue(text.isEmpty() ? 1 : 0);
                    case "charAt" -> {
                        int index = asInt(args[0]);
                        if (index < 0 || index >= text.length()) {
                            throw new InterpretedThrowable("java.lang.StringIndexOutOfBoundsException",
                                    "index " + index + ", length " + text.length());
                        }
                        yield new Value.IntValue(text.charAt(index));
                    }
                    case "equals" -> new Value.IntValue(text.equals(stringValue(args[0])) ? 1 : 0);
                    case "equalsIgnoreCase" -> new Value.IntValue(text.equalsIgnoreCase(stringValue(args[0])) ? 1 : 0);
                    case "contains" -> new Value.IntValue(text.contains(stringValue(args[0])) ? 1 : 0);
                    case "startsWith" -> new Value.IntValue(text.startsWith(stringValue(args[0])) ? 1 : 0);
                    case "endsWith" -> new Value.IntValue(text.endsWith(stringValue(args[0])) ? 1 : 0);
                    case "indexOf" -> new Value.IntValue(text.indexOf(stringValue(args[0])));
                    case "substring" -> {
                        int begin = asInt(args[0]);
                        int end = args.length > 1 ? asInt(args[1]) : text.length();
                        if (begin < 0 || end > text.length() || begin > end) {
                            throw new InterpretedThrowable("java.lang.StringIndexOutOfBoundsException",
                                    "begin " + begin + ", end " + end + ", length " + text.length());
                        }
                        yield new Value.StringValue(text.substring(begin, end));
                    }
                    case "toUpperCase" -> new Value.StringValue(text.toUpperCase(java.util.Locale.ROOT));
                    case "toLowerCase" -> new Value.StringValue(text.toLowerCase(java.util.Locale.ROOT));
                    case "trim" -> new Value.StringValue(text.trim());
                    case "strip" -> new Value.StringValue(text.strip());
                    case "concat" -> new Value.StringValue(text + stringValue(args[0]));
                    case "toString" -> new Value.StringValue(text);
                    case "valueOf" -> new Value.StringValue(stringify(args[0]));
                    case "format" -> new Value.StringValue(String.format(stringValue(args[0]),
                            java.util.Arrays.stream(args, 1, args.length).map(Interpreter::stringify).toArray()));
                    default -> NO_INTRINSIC;
                };
            }
            case "java.lang.StringBuilder", "java.lang.StringBuffer" -> {
                if (name.equals("<init>")) {
                    if (receiver instanceof Value.ObjectValue builder) {
                        builder.value().setField("value", new Value.StringValue(args.length == 0 ? "" : stringify(args[0])));
                    }
                    yield Value.NullValue.INSTANCE;
                }
                if (!(receiver instanceof Value.ObjectValue builder)) {
                    throw new InterpretedThrowable("java.lang.NullPointerException", null);
                }
                String current = stringValue(builder.value().getField("value"));
                yield switch (name) {
                    case "append" -> {
                        Type parameter = Type.getArgumentTypes(call.desc)[0];
                        builder.value().setField("value", new Value.StringValue(current + concatForm(args[0], parameter)));
                        yield receiver;
                    }
                    case "toString" -> new Value.StringValue(current);
                    case "length" -> new Value.IntValue(current.length());
                    case "reverse" -> {
                        builder.value().setField("value", new Value.StringValue(new StringBuilder(current).reverse().toString()));
                        yield receiver;
                    }
                    default -> NO_INTRINSIC;
                };
            }
            case "java.lang.Math" -> {
                yield switch (name) {
                    case "abs" -> args[0] instanceof Value.DoubleValue d
                            ? new Value.DoubleValue(Math.abs(d.value())) : new Value.IntValue(Math.abs(asInt(args[0])));
                    case "sqrt" -> new Value.DoubleValue(Math.sqrt(asDouble(args[0])));
                    case "pow" -> new Value.DoubleValue(Math.pow(asDouble(args[0]), asDouble(args[1])));
                    case "min" -> args[0] instanceof Value.DoubleValue d
                            ? new Value.DoubleValue(Math.min(d.value(), asDouble(args[1])))
                            : new Value.IntValue(Math.min(asInt(args[0]), asInt(args[1])));
                    case "max" -> args[0] instanceof Value.DoubleValue d
                            ? new Value.DoubleValue(Math.max(d.value(), asDouble(args[1])))
                            : new Value.IntValue(Math.max(asInt(args[0]), asInt(args[1])));
                    case "round" -> new Value.IntValue((int) Math.round(asDouble(args[0])));
                    case "random" -> new Value.DoubleValue(Math.random());
                    default -> NO_INTRINSIC;
                };
            }
            case "java.lang.Integer" -> {
                yield switch (name) {
                    case "parseInt" -> {
                        try {
                            yield new Value.IntValue(Integer.parseInt(stringValue(args[0]).trim()));
                        } catch (NumberFormatException ex) {
                            throw new InterpretedThrowable("java.lang.NumberFormatException",
                                    "For input string: \"" + stringValue(args[0]) + "\"");
                        }
                    }
                    case "toString" -> new Value.StringValue(args[0] instanceof Value.IntValue i
                            ? Integer.toString(i.value()) : stringify(args[0]));
                    case "valueOf" -> new Value.IntValue(asInt(args[0]));
                    case "compare" -> new Value.IntValue(Integer.compare(asInt(args[0]), asInt(args[1])));
                    case "MAX_VALUE" -> new Value.IntValue(Integer.MAX_VALUE);
                    case "MIN_VALUE" -> new Value.IntValue(Integer.MIN_VALUE);
                    default -> NO_INTRINSIC;
                };
            }
            case "java.lang.Double" -> {
                yield switch (name) {
                    case "parseDouble" -> {
                        try {
                            yield new Value.DoubleValue(Double.parseDouble(stringValue(args[0]).trim()));
                        } catch (NumberFormatException ex) {
                            throw new InterpretedThrowable("java.lang.NumberFormatException",
                                    "For input string: \"" + stringValue(args[0]) + "\"");
                        }
                    }
                    case "toString" -> new Value.StringValue(args[0] instanceof Value.DoubleValue d
                            ? Double.toString(d.value()) : stringify(args[0]));
                    case "valueOf" -> new Value.DoubleValue(asDouble(args[0]));
                    default -> NO_INTRINSIC;
                };
            }
            case "java.lang.Character" -> {
                int c = asInt(args[0]);
                yield switch (name) {
                    case "toLowerCase" -> new Value.IntValue(Character.toLowerCase((char) c));
                    case "toUpperCase" -> new Value.IntValue(Character.toUpperCase((char) c));
                    case "isDigit" -> new Value.IntValue(Character.isDigit((char) c) ? 1 : 0);
                    case "isLetter" -> new Value.IntValue(Character.isLetter((char) c) ? 1 : 0);
                    case "isLetterOrDigit" -> new Value.IntValue(Character.isLetterOrDigit((char) c) ? 1 : 0);
                    case "isWhitespace" -> new Value.IntValue(Character.isWhitespace((char) c) ? 1 : 0);
                    case "isUpperCase" -> new Value.IntValue(Character.isUpperCase((char) c) ? 1 : 0);
                    case "isLowerCase" -> new Value.IntValue(Character.isLowerCase((char) c) ? 1 : 0);
                    default -> NO_INTRINSIC;
                };
            }
            case "java.lang.System" -> {
                if (name.equals("identityHashCode")) yield new Value.IntValue(System.identityHashCode(receiver));
                if (name.equals("currentTimeMillis") || name.equals("nanoTime")) {
                    yield new Value.IntValue((int) System.nanoTime());
                }
                yield NO_INTRINSIC;
            }
            default -> NO_INTRINSIC;
        };
        return result;
    }

    /**
     * String concatenation as compiled by javac/ECJ on Java 9+: one {@code invokedynamic}
     * with {@code StringConcatFactory} and either a recipe ({@code makeConcatWithConstants})
     * or a plain descriptor-based concatenation ({@code makeConcat}).
     */
    private static Value concat(InvokeDynamicInsnNode insn, Value[] args) {
        Handle bootstrap = insn.bsm;
        if (!bootstrap.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
            throw new UnsupportedOperationException("unsupported invokedynamic bootstrap: "
                    + bootstrap.getOwner() + "." + bootstrap.getName());
        }
        Type[] parameterTypes = Type.getArgumentTypes(insn.desc);
        if (bootstrap.getName().equals("makeConcat")) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < args.length; i++) text.append(concatForm(args[i], parameterTypes[i]));
            return new Value.StringValue(text.toString());
        }
        if (!bootstrap.getName().equals("makeConcatWithConstants") || insn.bsmArgs.length == 0
                || !(insn.bsmArgs[0] instanceof String recipe)) {
            throw new UnsupportedOperationException("unsupported string concatenation: " + bootstrap.getName());
        }
        StringBuilder text = new StringBuilder();
        int nextArgument = 0;
        int nextConstant = 1;
        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);
            if (c == '\u0001') {
                if (nextArgument >= args.length) throw new IllegalStateException("concat recipe out of arguments");
                text.append(concatForm(args[nextArgument], parameterTypes[nextArgument]));
                nextArgument++;
            } else if (c == '\u0002') {
                if (nextConstant >= insn.bsmArgs.length) throw new IllegalStateException("concat recipe out of constants");
                text.append(stringify(constant(insn.bsmArgs[nextConstant++])));
            } else {
                text.append(c);
            }
        }
        return new Value.StringValue(text.toString());
    }

    // ---------------------------------------------------------------- helpers

    private Value readStatic(String className, String fieldName) {
        if (className.equals("java.lang.System") && fieldName.equals("out")) {
            return new Value.ObjectValue(printStream);
        }
        if (className.equals("java.lang.System") && fieldName.equals("err")) {
            return new Value.ObjectValue(printStream);
        }
        ClassNode type = classes.get(className);
        if (type != null) ensureInitialized(type);
        return staticFields.getOrDefault(key(className, fieldName), Value.NullValue.INSTANCE);
    }

    private void writeStatic(String className, String fieldName, Value value) {
        ClassNode type = classes.get(className);
        if (type != null) ensureInitialized(type);
        staticFields.put(key(className, fieldName), value);
    }

    private int handlerFor(MethodNode method, Map<LabelNode, Integer> labels, int pc, String exceptionClass) {
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            Integer start = labels.get(block.start);
            Integer end = labels.get(block.end);
            Integer handler = labels.get(block.handler);
            if (start == null || end == null || handler == null) continue;
            if (pc < start || pc >= end) continue;
            if (block.type == null || isAssignable(exceptionClass, block.type.replace('/', '.'))) return handler;
        }
        return -1;
    }

    private boolean isAssignable(String className, String target) {
        if (className.equals(target) || target.equals("java.lang.Object")) return true;
        String current = className;
        for (int guard = 0; guard < 32 && current != null; guard++) {
            ClassNode node = classes.get(current);
            current = node != null ? (node.superName == null ? null : node.superName.replace('/', '.'))
                    : builtinSupertypes.get(current);
            if (current != null && current.equals(target)) return true;
        }
        return false;
    }

    private boolean isThrowable(String className) {
        return className.equals("java.lang.Throwable") || isAssignable(className, "java.lang.Throwable");
    }

    private boolean isInstanceOf(Value value, String target) {
        return switch (value) {
            case Value.StringValue s -> target.equals("java.lang.String");
            case Value.ObjectValue o -> isAssignable(o.value().className(), target);
            case Value.ArrayValue a -> target.equals("java.lang.Object") || target.equals(a.value().componentType() + "[]");
            case Value.NullValue n -> false;
            default -> false;
        };
    }

    private static InterpretedObject syntheticException(String className, String message) {
        InterpretedObject object = new InterpretedObject(className);
        object.setField("message", new Value.StringValue(message == null ? "" : message));
        return object;
    }

    private static String messageOf(InterpretedObject object) {
        for (String field : List.of("message", "detailMessage")) {
            Value value = object.getField(field);
            if (value instanceof Value.StringValue text) return text.value();
        }
        return object.className();
    }

    private ClassNode requireClass(String name) {
        ClassNode result = classes.get(name);
        if (result == null) throw new IllegalArgumentException("class not loaded: " + name);
        return result;
    }

    private static MethodNode findCompatibleMethod(ClassNode type, String name, Value[] args) {
        MethodNode fallback = null;
        for (MethodNode method : type.methods) {
            if (!method.name.equals(name)) continue;
            Type[] parameters = Type.getArgumentTypes(method.desc);
            if (parameters.length != args.length) continue;
            boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                if (!isCompatible(parameters[i], args[i])) { compatible = false; break; }
            }
            if (compatible) return method;
            if (fallback == null) fallback = method;
        }
        if (fallback != null) return fallback;
        throw new IllegalArgumentException("method not found: " + type.name + "." + name + "/" + args.length);
    }

    private static boolean isCompatible(Type parameter, Value value) {
        return switch (parameter.getSort()) {
            case Type.INT, Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT -> value instanceof Value.IntValue;
            case Type.DOUBLE, Type.FLOAT -> value instanceof Value.DoubleValue;
            case Type.OBJECT, Type.ARRAY -> !(value instanceof Value.IntValue) && !(value instanceof Value.DoubleValue);
            default -> false;
        };
    }

    private static MethodNode findMethodByDescriptor(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("method not found: " + type.name + "." + name + descriptor));
    }

    private static void push(List<Value> stack, Value value) {
        stack.add(value == null ? Value.NullValue.INSTANCE : value);
    }

    private static Value pop(List<Value> stack) {
        if (stack.isEmpty()) throw new IllegalStateException("operand stack underflow");
        return stack.remove(stack.size() - 1);
    }

    private static Value peek(List<Value> stack) {
        if (stack.isEmpty()) throw new IllegalStateException("operand stack underflow");
        return stack.get(stack.size() - 1);
    }

    private static Value constant(Object value) {
        if (value instanceof Integer i) return new Value.IntValue(i);
        if (value instanceof Double d) return new Value.DoubleValue(d);
        if (value instanceof Float f) return new Value.DoubleValue(f.doubleValue());
        if (value instanceof String s) return new Value.StringValue(s);
        if (value instanceof Type t && t.getSort() == Type.OBJECT) return new Value.StringValue(t.getClassName());
        throw new IllegalArgumentException("unsupported constant: " + value);
    }

    private static Value defaultValue(Type type) {
        return switch (type.getSort()) {
            case Type.INT, Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT -> new Value.IntValue(0);
            case Type.DOUBLE, Type.FLOAT -> new Value.DoubleValue(0.0);
            default -> Value.NullValue.INSTANCE;
        };
    }

    private static void binaryInt(List<Value> stack, java.util.function.IntBinaryOperator operator) {
        int right = asInt(pop(stack));
        int left = asInt(pop(stack));
        push(stack, new Value.IntValue(operator.applyAsInt(left, right)));
    }

    private static void binaryDouble(List<Value> stack, java.util.function.DoubleBinaryOperator operator) {
        double right = asDouble(pop(stack));
        double left = asDouble(pop(stack));
        push(stack, new Value.DoubleValue(operator.applyAsDouble(left, right)));
    }

    private static int divide(int left, int right) {
        if (right == 0) throw new InterpretedThrowable("java.lang.ArithmeticException", "/ by zero");
        return left / right;
    }

    private static int remainder(int left, int right) {
        if (right == 0) throw new InterpretedThrowable("java.lang.ArithmeticException", "/ by zero");
        return left % right;
    }

    private static int nonNegative(int length) {
        if (length < 0) throw new InterpretedThrowable("java.lang.NegativeArraySizeException", Integer.toString(length));
        return length;
    }

    private static String primitiveName(int typeCode) {
        return switch (typeCode) {
            case 4 -> "boolean";
            case 5 -> "double";
            case 8 -> "byte";
            case 9 -> "char";
            case 10 -> "short";
            case 11 -> "int";
            default -> "int";
        };
    }

    private static int asInt(Value value) {
        if (value instanceof Value.IntValue intValue) return intValue.value();
        throw new InterpretedThrowable("java.lang.ClassCastException", "expected an int but got " + value);
    }

    private static double asDouble(Value value) {
        if (value instanceof Value.DoubleValue d) return d.value();
        if (value instanceof Value.IntValue i) return i.value();
        throw new InterpretedThrowable("java.lang.ClassCastException", "expected a number but got " + value);
    }

    private static String requireString(Value value, String method) {
        if (value instanceof Value.StringValue string) return string.value();
        throw new InterpretedThrowable("java.lang.NullPointerException", "String." + method + " on null");
    }

    private static InterpretedObject requireObject(Value value, String what) {
        if (value instanceof Value.ObjectValue object) return object.value();
        if (value == Value.NullValue.INSTANCE) {
            throw new InterpretedThrowable("java.lang.NullPointerException", "cannot access " + what + " on null");
        }
        throw new InterpretedThrowable("java.lang.ClassCastException", "expected an object for " + what + " but got " + value);
    }

    private static InterpretedArray requireArray(Value value) {
        if (value instanceof Value.ArrayValue array) return array.value();
        if (value == Value.NullValue.INSTANCE) {
            throw new InterpretedThrowable("java.lang.NullPointerException", "array reference is null");
        }
        throw new InterpretedThrowable("java.lang.ClassCastException", "expected an array but got " + value);
    }

    /** Formats a concatenation operand the way Java prints its static type. */
    private static String concatForm(Value value, Type type) {
        if (type.getSort() == Type.CHAR && value instanceof Value.IntValue i) return String.valueOf((char) i.value());
        if (type.getSort() == Type.BOOLEAN && value instanceof Value.IntValue i) return i.value() != 0 ? "true" : "false";
        return stringify(value);
    }

    private static String stringValue(Value value) {
        if (value instanceof Value.StringValue string) return string.value();
        if (value == Value.NullValue.INSTANCE) return "null";
        return stringify(value);
    }

    /** Java-like textual form used by concatenation and {@code println}. */
    public static String stringify(Value value) {
        return switch (value) {
            case Value.IntValue i -> Integer.toString(i.value());
            case Value.DoubleValue d -> Double.toString(d.value());
            case Value.StringValue s -> s.value();
            case Value.ObjectValue o -> o.value().className() + "@" + Integer.toHexString(System.identityHashCode(o.value()));
            case Value.ArrayValue a -> a.value().toString();
            case Value.NullValue n -> "null";
        };
    }

    private static String key(String className, String fieldName) { return className + "." + fieldName; }

    private static RuntimeException unsupported(AbstractInsnNode insn) {
        return new UnsupportedOperationException("unsupported bytecode opcode " + insn.getOpcode()
                + " (" + insn.getClass().getSimpleName() + ")");
    }

}
