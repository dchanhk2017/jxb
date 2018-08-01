package com.dchan.jaxb;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JTypeVar;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

import java.util.Set;
import java.util.function.Function;

import static com.dchan.jaxb.ClassDiscoverer.allConcreteClasses;

class CreateBaseVisitorClass extends CodeCreator {

    private final JDefinedClass visitor;
    private final Function<String,String> visitMethodNamer;

    CreateBaseVisitorClass(JDefinedClass visitor, Outline outline,
                           JPackage jPackage,
                           Function<String, String> visitMethodNamer) {
        super(outline, jPackage);
        this.visitor = visitor;
        this.visitMethodNamer = visitMethodNamer;
    }
    
    @Override
    protected void run(Set<ClassOutline> classes, Set<JClass> directClasses) {
        JDefinedClass _class = getOutline().getClassFactory().createClass(getPackage(), "BaseVisitor", null);
		setOutput(_class);
        final JTypeVar returnType = _class.generify("R");
        final JTypeVar exceptionType = _class.generify("E", Throwable.class);
		final JClass narrowedVisitor = visitor.narrow(returnType, exceptionType);
        getOutput()._implements(narrowedVisitor);

        for(JClass jc : allConcreteClasses(classes, directClasses)) {
            implementVisitMethod(returnType, exceptionType, jc);
        }
    }

    private void implementVisitMethod(JTypeVar returnType, JTypeVar exceptionType, JClass implClass) {
        JMethod _method;
        String methodName = visitMethodNamer.apply(implClass.name());
        _method = getOutput().method(JMod.PUBLIC, returnType, methodName);
        _method._throws(exceptionType);
        _method.param(implClass, "aBean");
        _method.body()._return(JExpr._null());
        _method.annotate(Override.class);
    }
}

