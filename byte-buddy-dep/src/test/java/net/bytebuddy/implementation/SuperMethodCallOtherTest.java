package net.bytebuddy.implementation;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.test.utility.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Method;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SuperMethodCallOtherTest extends AbstractImplementationTest {

    private static final String SINGLE_DEFAULT_METHOD = "net.bytebuddy.test.precompiled.SingleDefaultMethodInterface";

    private static final String SINGLE_DEFAULT_METHOD_CLASS = "net.bytebuddy.test.precompiled.SingleDefaultMethodClass";

    private static final String CONFLICTING_INTERFACE = "net.bytebuddy.test.precompiled.SingleDefaultMethodConflictingInterface";

    private static final String FOO = "foo";

    @Rule
    public TestRule mockitoRule = new MockitoRule(this);

    @Rule
    public MethodRule javaVersionRule = new JavaVersionRule();

    @Mock
    private InstrumentedType instrumentedType;

    @Mock
    private TypeDescription typeDescription, superType, returnType, declaringType;

    @Mock
    private Implementation.Target implementationTarget;

    @Mock
    private MethodVisitor methodVisitor;

    @Mock
    private Implementation.Context implementationContext;

    @Mock
    private MethodDescription methodDescription;

    @Mock
    private MethodList superTypeMethods;

    private ClassLoader classLoader;

    @Before
    public void setUp() throws Exception {
        when(implementationTarget.getTypeDescription()).thenReturn(typeDescription);
        classLoader = new PrecompiledTypeClassLoader(getClass().getClassLoader());
    }

    @Test
    public void testPreparation() throws Exception {
        assertThat(SuperMethodCall.INSTANCE.prepare(instrumentedType), is(instrumentedType));
        verifyZeroInteractions(instrumentedType);
    }

    @Test(expected = IllegalStateException.class)
    @SuppressWarnings("unchecked")
    public void testConstructor() throws Exception {
        when(typeDescription.getSupertype()).thenReturn(superType);
        when(methodDescription.isConstructor()).thenReturn(true);
        when(superType.getDeclaredMethods()).thenReturn(superTypeMethods);
        when(superTypeMethods.filter(any(ElementMatcher.class))).thenReturn(superTypeMethods);
        when(implementationTarget.invokeSuper(methodDescription, Implementation.Target.MethodLookup.Default.EXACT))
                .thenReturn(Implementation.SpecialMethodInvocation.Illegal.INSTANCE);
        SuperMethodCall.INSTANCE.appender(implementationTarget).apply(methodVisitor, implementationContext, methodDescription);
    }

    @Test(expected = IllegalStateException.class)
    @SuppressWarnings("unchecked")
    public void testStaticMethod() throws Exception {
        when(typeDescription.getSupertype()).thenReturn(superType);
        when(methodDescription.isStatic()).thenReturn(true);
        when(methodDescription.getParameters()).thenReturn(new ParameterList.Empty());
        when(methodDescription.getReturnType()).thenReturn(returnType);
        when(returnType.getStackSize()).thenReturn(StackSize.SINGLE);
        when(superType.getDeclaredMethods()).thenReturn(superTypeMethods);
        when(superTypeMethods.filter(any(ElementMatcher.class))).thenReturn(superTypeMethods);
        when(implementationTarget.invokeSuper(eq(methodDescription), any(Implementation.Target.MethodLookup.class)))
                .thenReturn(Implementation.SpecialMethodInvocation.Illegal.INSTANCE);
        SuperMethodCall.INSTANCE.appender(implementationTarget).apply(methodVisitor, implementationContext, methodDescription);
    }

    @Test(expected = IllegalStateException.class)
    @SuppressWarnings("unchecked")
    public void testNoSuper() throws Exception {
        when(typeDescription.getSupertype()).thenReturn(superType);
        when(methodDescription.getParameters()).thenReturn(new ParameterList.Empty());
        when(methodDescription.getReturnType()).thenReturn(returnType);
        when(methodDescription.getDeclaringType()).thenReturn(declaringType);
        when(declaringType.getStackSize()).thenReturn(StackSize.SINGLE);
        when(returnType.getStackSize()).thenReturn(StackSize.SINGLE);
        when(superType.getDeclaredMethods()).thenReturn(superTypeMethods);
        when(superTypeMethods.filter(any(ElementMatcher.class))).thenReturn(superTypeMethods);
        when(implementationTarget.invokeSuper(eq(methodDescription), any(Implementation.Target.MethodLookup.class)))
                .thenReturn(Implementation.SpecialMethodInvocation.Illegal.INSTANCE);
        SuperMethodCall.INSTANCE.appender(implementationTarget).apply(methodVisitor, implementationContext, methodDescription);
    }

    @Test
    public void testAndThen() throws Exception {
        DynamicType.Loaded<Foo> loaded = implement(Foo.class, SuperMethodCall.INSTANCE
                .andThen(new Implementation.Simple(new TextConstant(FOO), MethodReturn.REFERENCE)));
        Foo foo = loaded.getLoaded().newInstance();
        assertThat(foo.foo(), is(FOO));
        foo.assertOnlyCall(FOO);
    }

    @Test
    @JavaVersionRule.Enforce(8)
    public void testUnambiguousDirectDefaultMethod() throws Exception {
        DynamicType.Loaded<?> loaded = implement(Object.class,
                SuperMethodCall.INSTANCE,
                classLoader,
                isMethod().and(not(isDeclaredBy(Object.class))),
                classLoader.loadClass(SINGLE_DEFAULT_METHOD));
        assertThat(loaded.getLoaded().getDeclaredMethods().length, is(1));
        Method method = loaded.getLoaded().getDeclaredMethod(FOO);
        Object instance = loaded.getLoaded().newInstance();
        assertThat(method.invoke(instance), is((Object) FOO));
    }


    @Test(expected = IllegalStateException.class)
    @JavaVersionRule.Enforce(8)
    public void testAmbiguousDirectDefaultMethodThrowsException() throws Exception {
        implement(Object.class,
                SuperMethodCall.INSTANCE,
                classLoader,
                not(isDeclaredBy(Object.class)),
                classLoader.loadClass(SINGLE_DEFAULT_METHOD), classLoader.loadClass(CONFLICTING_INTERFACE));
    }


    @Test
    @JavaVersionRule.Enforce(8)
    public void testNonDeclaredDefaultMethodThrowsException() throws Exception {
        DynamicType.Loaded<?> loaded = implement(classLoader.loadClass(SINGLE_DEFAULT_METHOD_CLASS),
                SuperMethodCall.INSTANCE,
                classLoader,
                isMethod().and(not(isDeclaredBy(Object.class))));
        assertThat(loaded.getLoaded().getDeclaredMethods().length, is(1));
        Method method = loaded.getLoaded().getDeclaredMethod(FOO);
        Object instance = loaded.getLoaded().newInstance();
        assertThat(method.invoke(instance), is((Object) FOO));
    }

    @Test
    public void testObjectProperties() throws Exception {
        ObjectPropertyAssertion.of(SuperMethodCall.class).apply();
        ObjectPropertyAssertion.of(SuperMethodCall.WithoutReturn.class).apply();
        ObjectPropertyAssertion.of(SuperMethodCall.Appender.class).apply();
        ObjectPropertyAssertion.of(SuperMethodCall.Appender.TerminationHandler.class).apply();
    }

    public static class Foo extends CallTraceable {

        public String foo() {
            register(FOO);
            return null;
        }
    }
}
