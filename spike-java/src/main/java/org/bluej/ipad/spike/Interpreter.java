package org.bluej.ipad.spike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Small interpreted-bytecode proof of concept. Supported instructions are constants, local loads/stores,
 * integer arithmetic, object allocation, field access, same-application method calls and returns.
 * Unsupported opcodes fail explicitly. This is not a general Java virtual machine.
 */
public final class Interpreter {
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final int instructionLimit;
    private int executed;

    public Interpreter() { this(100_000); }
    public Interpreter(int instructionLimit) {
        if (instructionLimit < 1) throw new IllegalArgumentException("instructionLimit must be positive");
        this.instructionLimit = instructionLimit;
    }

    public void load(Map<String, byte[]> bytecode) {
        bytecode.forEach((name, bytes) -> {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(node.name.replace('/', '.'), node);
        });
    }

    public void load(Path classFile) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(classFile)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        classes.put(node.name.replace('/', '.'), node);
    }

    public InterpretedObject newObject(String className, Value... arguments) {
        ClassNode type = requireClass(className);
        InterpretedObject object = new InterpretedObject(className);
        for (var field : type.fields) {
            if ((field.access & Opcodes.ACC_STATIC) == 0) object.setField(field.name, defaultValue(Type.getType(field.desc)));
        }
        MethodNode constructor = findMethod(type, "<init>", arguments);
        invokeMethod(object, type, constructor, arguments);
        return object;
    }

    public Value invoke(InterpretedObject target, String methodName, Value... arguments) {
        ClassNode type = requireClass(target.className());
        return invokeMethod(target, type, findMethod(type, methodName, arguments), arguments);
    }

    public Value invokeStatic(String className, String methodName, Value... arguments) {
        ClassNode type = requireClass(className);
        return invokeMethod(null, type, findMethod(type, methodName, arguments), arguments);
    }

    private Value invokeMethod(InterpretedObject receiver, ClassNode owner, MethodNode method, Value[] arguments) {
        Type[] parameterTypes = Type.getArgumentTypes(method.desc);
        Object[] locals = new Object[method.maxLocals];
        int local = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) locals[local++] = receiver;
        if (arguments.length != parameterTypes.length) throw new IllegalArgumentException("wrong argument count");
        for (int i = 0; i < arguments.length; i++) locals[local++] = arguments[i];
        List<Object> stack = new ArrayList<>();
        AbstractInsnNode[] instructions = method.instructions.toArray();
        Map<AbstractInsnNode, Integer> indexes = new HashMap<>();
        for (int i = 0; i < instructions.length; i++) indexes.put(instructions[i], i);
        for (int pc = 0; pc < instructions.length; pc++) {
            AbstractInsnNode insn = instructions[pc];
            if (insn.getOpcode() < 0) continue;
            if (++executed > instructionLimit) throw new IllegalStateException("interpreter instruction limit exceeded");
            int op = insn.getOpcode();
            switch (op) {
                case Opcodes.NOP -> { }
                case Opcodes.ACONST_NULL -> stack.add(Value.NullValue.INSTANCE);
                case Opcodes.ICONST_M1 -> stack.add(new Value.IntValue(-1));
                case Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                     Opcodes.ICONST_4, Opcodes.ICONST_5 -> stack.add(new Value.IntValue(op - Opcodes.ICONST_0));
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> stack.add(new Value.IntValue(((IntInsnNode) insn).operand));
                case Opcodes.LDC -> stack.add(constant(((LdcInsnNode) insn).cst));
                case Opcodes.ILOAD, Opcodes.ALOAD -> stack.add(locals[((VarInsnNode) insn).var]);
                case Opcodes.ISTORE, Opcodes.ASTORE -> locals[((VarInsnNode) insn).var] = pop(stack);
                case Opcodes.POP -> pop(stack);
                case Opcodes.DUP -> stack.add(peek(stack));
                case Opcodes.IADD -> binaryInt(stack, (a, b) -> a + b);
                case Opcodes.ISUB -> binaryInt(stack, (a, b) -> a - b);
                case Opcodes.IMUL -> binaryInt(stack, (a, b) -> a * b);
                case Opcodes.IDIV -> binaryInt(stack, (a, b) -> a / b);
                case Opcodes.IRETURN, Opcodes.ARETURN -> { return toValue(pop(stack)); }
                case Opcodes.RETURN -> { return Value.NullValue.INSTANCE; }
                case Opcodes.NEW -> {
                    String name = ((TypeInsnNode) insn).desc.replace('/', '.');
                    ClassNode nested = requireClass(name);
                    InterpretedObject instance = new InterpretedObject(name);
                    for (var field : nested.fields) if ((field.access & Opcodes.ACC_STATIC) == 0)
                        instance.setField(field.name, defaultValue(Type.getType(field.desc)));
                    stack.add(instance);
                }
                case Opcodes.GETFIELD -> {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    Object instance = pop(stack);
                    if (!(instance instanceof InterpretedObject obj)) throw unsupported(insn);
                    stack.add(obj.getField(field.name));
                }
                case Opcodes.PUTFIELD -> {
                    FieldInsnNode field = (FieldInsnNode) insn;
                    Value value = toValue(pop(stack));
                    Object instance = pop(stack);
                    if (!(instance instanceof InterpretedObject obj)) throw unsupported(insn);
                    obj.setField(field.name, value);
                }
                case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC -> {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    Type[] argTypes = Type.getArgumentTypes(call.desc);
                    Value[] args = new Value[argTypes.length];
                    for (int i = args.length - 1; i >= 0; i--) args[i] = toValue(pop(stack));
                    Object callReceiver = op == Opcodes.INVOKESTATIC ? null : pop(stack);
                    String targetClass = call.owner.replace('/', '.');
                    if (targetClass.equals("java.lang.Object") && call.name.equals("<init>")) continue;
                    ClassNode targetType = requireClass(targetClass);
                    MethodNode targetMethod = findMethodByDescriptor(targetType, call.name, call.desc);
                    if (!(callReceiver == null || callReceiver instanceof InterpretedObject)) throw unsupported(insn);
                    Value result = invokeMethod((InterpretedObject) callReceiver, targetType, targetMethod, args);
                    if (Type.getReturnType(call.desc).getSort() != Type.VOID) stack.add(result);
                }
                default -> throw unsupported(insn);
            }
        }
        return Value.NullValue.INSTANCE;
    }

    private ClassNode requireClass(String name) {
        ClassNode result = classes.get(name);
        if (result == null) throw new IllegalArgumentException("class not loaded: " + name);
        return result;
    }
    private static MethodNode findMethod(ClassNode type, String name, Value[] args) {
        for (MethodNode method : type.methods) {
            if (!method.name.equals(name) || Type.getArgumentTypes(method.desc).length != args.length) continue;
            return method;
        }
        throw new IllegalArgumentException("method not found: " + type.name + "." + name + "/" + args.length);
    }
    private static MethodNode findMethodByDescriptor(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("method not found: " + type.name + "." + name + descriptor));
    }
    private static Object pop(List<Object> stack) {
        if (stack.isEmpty()) throw new IllegalStateException("operand stack underflow");
        return stack.remove(stack.size() - 1);
    }
    private static Object peek(List<Object> stack) {
        if (stack.isEmpty()) throw new IllegalStateException("operand stack underflow");
        return stack.get(stack.size() - 1);
    }
    private static Value toValue(Object value) {
        if (value instanceof Value v) return v;
        if (value == null) return Value.NullValue.INSTANCE;
        if (value instanceof InterpretedObject object) return new Value.ObjectValue(object);
        throw new IllegalStateException("unsupported runtime value " + value);
    }
    private static Value constant(Object value) {
        if (value instanceof Integer i) return new Value.IntValue(i);
        if (value instanceof String s) return new Value.StringValue(s);
        throw new IllegalArgumentException("unsupported constant: " + value);
    }
    private static Value defaultValue(Type type) {
        return switch (type.getSort()) {
            case Type.INT, Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT -> new Value.IntValue(0);
            case Type.OBJECT -> type.getClassName().equals("java.lang.String") ? Value.NullValue.INSTANCE : Value.NullValue.INSTANCE;
            default -> Value.NullValue.INSTANCE;
        };
    }
    private static void binaryInt(List<Object> stack, java.util.function.IntBinaryOperator operator) {
        int right = asInt(pop(stack));
        int left = asInt(pop(stack));
        stack.add(new Value.IntValue(operator.applyAsInt(left, right)));
    }
    private static int asInt(Object value) {
        if (value instanceof Value.IntValue intValue) return intValue.value();
        throw new IllegalArgumentException("expected int but got " + value);
    }
    private static RuntimeException unsupported(AbstractInsnNode insn) {
        return new UnsupportedOperationException("unsupported bytecode opcode " + insn.getOpcode()
                + " (" + insn.getClass().getSimpleName() + ")");
    }
}
