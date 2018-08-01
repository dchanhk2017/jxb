package com.dchan.jaxb;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JTypeVar;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

import java.util.Set;
import java.util.function.Function;

import static com.dchan.jaxb.ClassDiscoverer.allConcreteClasses;

class CreateVisitorInterface extends CodeCreator {

    private final Function<String,String> visitMethodNamer;

    CreateVisitorInterface(Outline outline, JPackage jPackage, Function<String, String> visitMethodNamer) {
        super(outline, jPackage);
        this.visitMethodNamer = visitMethodNamer;
    }
    
    @Override
    protected void run(Set<ClassOutline> classes, Set<JClass> directClasses) {
        
        final JDefinedClass _interface = outline.getClassFactory().createInterface(jpackage, "Visitor", null);
        
        final JTypeVar returnType = _interface.generify("R");
        final JTypeVar exceptionType = _interface.generify("E", Throwable.class);

        setOutput( _interface );

        for(JClass jc : allConcreteClasses(classes, directClasses)) {
//            System.out.println("seeing class:" + jc.name());
            declareVisitMethod(returnType, exceptionType, jc);
        }
    }

    private void declareVisitMethod(JTypeVar returnType, JTypeVar exceptionType, JClass implClass) {
        JMethod vizMethod;
        String visitMethod = visitMethodNamer.apply(implClass.name());
        vizMethod = getOutput().method(JMod.NONE, returnType, visitMethod);
        vizMethod._throws(exceptionType);
        vizMethod.param(implClass, "aBean");
    }
}
