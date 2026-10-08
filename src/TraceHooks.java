import java.io.IOException;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;

/** Wrappers keep the original instruction streams and return values intact. */
final class TraceHooks {
    static final String SERVICE = "Lcom/google/android/apps/speech/tts/googletts/service/GoogleTtsService;";
    static final String TRACE = "Llocal/tts/MapsTrace;";
    static final String STRING = "Ljava/lang/String;";
    static final String CONTEXT = "Landroid/content/Context;";
    static final String CALLBACK = "Landroid/speech/tts/SynthesisCallback;";
    static final String REQUEST = "Landroid/speech/tts/SynthesisRequest;";
    static final String PREFIX = "mapsTraceOriginal$";
    static final Map<String, List<String>> READINESS = Map.of(
            "onIsLanguageAvailable", List.of(STRING, STRING, STRING),
            "onLoadLanguage", List.of(STRING, STRING, STRING),
            "onLoadVoice", List.of(STRING),
            "onIsValidVoiceName", List.of(STRING),
            "onGetDefaultVoiceNameFor", List.of(STRING, STRING, STRING));

    static boolean selected(Method m) {
        return m.getDefiningClass().equals("Ldib;") && m.getName().equals("b")
                && m.getParameterTypes().equals(List.of("Ldhs;", "I")) && m.getReturnType().equals("Leyx;");
    }
    static boolean synth(Method m) {
        return m.getDefiningClass().equals(SERVICE) && m.getName().equals("onSynthesizeText")
                && m.getParameterTypes().equals(List.of(REQUEST, CALLBACK)) && m.getReturnType().equals("V");
    }
    static boolean readiness(Method m) {
        return m.getDefiningClass().equals(SERVICE) && READINESS.containsKey(m.getName())
                && m.getParameterTypes().equals(READINESS.get(m.getName()))
                && m.getReturnType().equals(m.getName().equals("onGetDefaultVoiceNameFor") ? STRING : "I");
    }
    static boolean target(Method m) { return synth(m) || selected(m) || readiness(m); }
    static ImmutableMethod copy(Method m, String name, MethodImplementation body) {
        return new ImmutableMethod(m.getDefiningClass(), name, m.getParameters(), m.getReturnType(),
                m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), body);
    }
    static ImmutableInstruction35c call(Opcode op, String owner, String name, List<String> params, String result, int... regs) {
        int[] r = Arrays.copyOf(regs, 5);
        return new ImmutableInstruction35c(op, regs.length, r[0], r[1], r[2], r[3], r[4],
                new ImmutableMethodReference(owner, name, params, result));
    }
    static ImmutableInstruction35c original(Method m, int... regs) {
        return call(Opcode.INVOKE_VIRTUAL, m.getDefiningClass(), PREFIX + m.getName(),
                m.getParameterTypes().stream().map(Object::toString).toList(), m.getReturnType(), regs);
    }
    static ClassDef wrap(ClassDef c) throws IOException {
        List<Method> methods = new ArrayList<>();
        int found = 0;
        for (Method m : c.getMethods()) {
            if (m.getName().startsWith(PREFIX)) throw new IOException("Already instrumented");
            if (!target(m)) { methods.add(m); continue; }
            if (AccessFlags.STATIC.isSet(m.getAccessFlags()) || AccessFlags.PRIVATE.isSet(m.getAccessFlags())
                    || m.getImplementation() == null) throw new IOException("Unexpected hook access");
            found++;
            methods.add(copy(m, PREFIX + m.getName(), m.getImplementation()));
            MethodImplementation body;
            if (synth(m)) {
                body = new ImmutableMethodImplementation(5, List.of(
                    call(Opcode.INVOKE_STATIC, TRACE, "begin", List.of(CONTEXT, REQUEST, CALLBACK), CALLBACK, 2, 3, 4),
                    new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
                    original(m, 2, 3, 0),
                    call(Opcode.INVOKE_STATIC, TRACE, "end", List.of(CALLBACK), "V", 0),
                    new ImmutableInstruction10x(Opcode.RETURN_VOID),
                    new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 1),
                    call(Opcode.INVOKE_STATIC, TRACE, "failed", List.of(CALLBACK, "Ljava/lang/Throwable;"), "V", 0, 1),
                    new ImmutableInstruction11x(Opcode.THROW, 1)),
                    List.of(new ImmutableTryBlock(4, 3, List.of(new ImmutableExceptionHandler(null, 11)))), List.of());
            } else if (selected(m)) {
                body = new ImmutableMethodImplementation(4, List.of(
                    original(m, 1, 2, 3),
                    new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
                    call(Opcode.INVOKE_STATIC, TRACE, "selected", List.of("Ljava/lang/Object;", "Ljava/lang/Object;"), "V", 2, 0),
                    new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), List.of(), List.of());
            } else {
                // v0 token, v1 result, v2..v6 log args; parameters start at v7.
                var code = new ArrayList<com.android.tools.smali.dexlib2.iface.instruction.Instruction>();
                code.add(new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 2, 7));
                code.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 3, new ImmutableStringReference(m.getName())));
                code.add(new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 4, 8));
                if (m.getParameters().size() == 3) {
                    code.add(new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 5, 9));
                    code.add(new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 6, 10));
                } else {
                    code.add(new ImmutableInstruction11n(Opcode.CONST_4, 5, 0));
                    code.add(new ImmutableInstruction11n(Opcode.CONST_4, 6, 0));
                }
                code.add(call(Opcode.INVOKE_STATIC, TRACE, "readiness", List.of(CONTEXT, STRING, STRING, STRING, STRING), STRING, 2, 3, 4, 5, 6));
                code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
                code.add(m.getParameters().size() == 3 ? original(m, 7, 8, 9, 10) : original(m, 7, 8));
                boolean object = m.getReturnType().equals(STRING);
                code.add(new ImmutableInstruction11x(object ? Opcode.MOVE_RESULT_OBJECT : Opcode.MOVE_RESULT, 1));
                code.add(call(Opcode.INVOKE_STATIC, TRACE, "result", List.of(STRING, m.getReturnType()), "V", 0, 1));
                code.add(new ImmutableInstruction11x(object ? Opcode.RETURN_OBJECT : Opcode.RETURN, 1));
                body = new ImmutableMethodImplementation(8 + m.getParameters().size(), code, List.of(), List.of());
            }
            methods.add(copy(m, m.getName(), body));
        }
        int expected = c.getType().equals(SERVICE) ? 6 : 1;
        if (found != expected) throw new IOException("Trace hook count: " + c.getType() + " " + found);
        return new ImmutableClassDef(c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(),
                c.getSourceFile(), c.getAnnotations(), c.getFields(), methods);
    }
}
