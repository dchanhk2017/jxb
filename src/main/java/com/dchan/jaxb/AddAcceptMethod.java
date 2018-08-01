package com.dchan.jaxb;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.outline.ClassOutline;

import java.util.Set;
import java.util.function.Function;

class AddAcceptMethod {

    private final Function<String,String> visitMethodNamer;

    AddAcceptMethod(Function<String,String> visitMethodNamer) {
        this.visitMethodNamer = visitMethodNamer;
    }

    public void run(Set<ClassOutline> sorted, JDefinedClass visitor) {
        sorted.stream().filter(classOutline -> !classOutline.target.isAbstract()).forEach(classOutline -> {
            JDefinedClass beanImpl = classOutline.implClass;
            final JMethod acceptMethod = beanImpl.method(JMod.PUBLIC, void.class, "accept");
            final JTypeVar returnType = acceptMethod.generify("R");
            final JTypeVar exceptionType = acceptMethod.generify("E", Throwable.class);
            acceptMethod.type(returnType);
            acceptMethod._throws(exceptionType);
            final JClass narrowedVisitor = visitor.narrow(returnType, exceptionType);
            JVar vizParam = acceptMethod.param(narrowedVisitor, "aVisitor");
            JBlock block = acceptMethod.body();
            String methodName = visitMethodNamer.apply(beanImpl.name());
            block._return(vizParam.invoke(methodName).arg(JExpr._this()));
        });
    }
}
