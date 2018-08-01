package com.dchan.jaxb;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import static com.dchan.jaxb.ClassDiscoverer.allConcreteClasses;

class CreateTraversingVisitorClass extends CodeCreator {

    private final JDefinedClass progressMonitor;
    private final JDefinedClass visitor;
    private final JDefinedClass traverser;
    private final Function<String,String> visitMethodNamer;
    private final Function<String,String> traverseMethodNamer;

    CreateTraversingVisitorClass(JDefinedClass visitor, JDefinedClass progressMonitor,
                                 JDefinedClass traverser, Outline outline, JPackage jPackage,
                                 Function<String, String> visitMethodNamer,
                                 Function<String, String> traverseMethodNamer) {
        super(outline, jPackage);
        this.visitor = visitor;
        this.traverser = traverser;
        this.progressMonitor = progressMonitor;
        this.visitMethodNamer = visitMethodNamer;
        this.traverseMethodNamer = traverseMethodNamer;
    }

    @Override
    protected void run(Set<ClassOutline> classes, Set<JClass> directClasses) {

        JDefinedClass traversingVisitor = getOutline().getClassFactory().createClass(getPackage(), "TraversingVisitor", null);
        final JTypeVar returnType = traversingVisitor.generify("R");
        final JTypeVar exceptionType = traversingVisitor.generify("E", Throwable.class);
        final JClass narrowedVisitor = visitor.narrow(returnType).narrow(exceptionType);
        final JClass narrowedTraverser = traverser.narrow(exceptionType);
        traversingVisitor._implements(narrowedVisitor);
        JMethod ctor = traversingVisitor.constructor(JMod.PUBLIC);
        ctor.param(narrowedTraverser, "aTraverser");
        ctor.param(narrowedVisitor, "aVisitor");
        JFieldVar fieldTraverseFirst = traversingVisitor.field(JMod.PRIVATE, Boolean.TYPE, "traverseFirst");
        JFieldVar fieldVisitor = traversingVisitor.field(JMod.PRIVATE, narrowedVisitor, "visitor");
        JFieldVar fieldTraverser = traversingVisitor.field(JMod.PRIVATE, narrowedTraverser, "traverser");
        JFieldVar fieldMonitor = traversingVisitor.field(JMod.PRIVATE, progressMonitor, "progressMonitor");
        addGetterAndSetter(traversingVisitor, fieldTraverseFirst);
        addGetterAndSetter(traversingVisitor, fieldVisitor);
        addGetterAndSetter(traversingVisitor, fieldTraverser);
        addGetterAndSetter(traversingVisitor, fieldMonitor);
        ctor.body().assign(fieldTraverser, JExpr.ref("aTraverser"));
        ctor.body().assign(fieldVisitor, JExpr.ref("aVisitor"));

        setOutput(traversingVisitor);

        for(JClass jc : allConcreteClasses(classes, Collections.emptySet())) {
            generate(traversingVisitor, returnType, exceptionType, jc);
        }
        for(JClass jc : directClasses) {
            generateForDirectClass(traversingVisitor, returnType, exceptionType, jc);
        }
    }

    private void generateForDirectClass(JDefinedClass traversingVisitor, JTypeVar returnType, JTypeVar exceptionType, JClass implClass) {
        JMethod travViz;
        String visitMethodName = visitMethodNamer.apply(implClass.name());
        travViz = traversingVisitor.method(JMod.PUBLIC, returnType, visitMethodName);
        travViz._throws(exceptionType);
        JVar beanVar = travViz.param(implClass, "aBean");
        travViz.annotate(Override.class);
        JBlock travVizBloc = travViz.body();

        addTraverseBlock(travViz, beanVar, true);

        JVar retVal = travVizBloc.decl(returnType, "returnVal");

        travVizBloc.assign(retVal, JExpr.invoke(JExpr.invoke("getVisitor"), visitMethodName).arg(beanVar));

        travVizBloc._if(JExpr.ref("progressMonitor").ne(JExpr._null()))._then().invoke(JExpr.ref("progressMonitor"), "visited").arg(beanVar);

        addTraverseBlock(travViz, beanVar, false);

        travVizBloc._return(retVal);
    }

    private void generate(JDefinedClass traversingVisitor, JTypeVar returnType, JTypeVar exceptionType, JClass implClass) {
        // add method impl to traversing visitor
        JMethod travViz;
        travViz = traversingVisitor.method(JMod.PUBLIC, returnType, visitMethodNamer.apply(implClass.name()));
        travViz._throws(exceptionType);
        JVar beanVar = travViz.param(implClass, "aBean");
        travViz.annotate(Override.class);
        JBlock travVizBloc = travViz.body();

        addTraverseBlock(travViz, beanVar, true);

        JVar retVal = travVizBloc.decl(returnType, "returnVal");
        travVizBloc.assign(retVal,
                JExpr.invoke(beanVar, "accept").arg(JExpr.invoke("getVisitor")));
        travVizBloc._if(JExpr.ref("progressMonitor").ne(JExpr._null()))._then().invoke(JExpr.ref("progressMonitor"), "visited").arg(beanVar);

        addTraverseBlock(travViz, beanVar, false);
        travVizBloc._return(retVal);
    }

    private void addTraverseBlock(JMethod travViz, JVar beanVar, boolean flag) {
        JBlock travVizBloc = travViz.body();

        JBlock block = travVizBloc._if(JExpr.ref("traverseFirst").eq(JExpr.lit(flag)))._then();
        String traverseMethodName = traverseMethodNamer.apply(beanVar.type().name());
        block.invoke(JExpr.invoke("getTraverser"), traverseMethodName).arg(beanVar).arg(JExpr._this());
        block._if(JExpr.ref("progressMonitor").ne(JExpr._null()))._then().invoke(JExpr.ref("progressMonitor"), "traversed").arg(beanVar);
    }

    private void addGetterAndSetter(JDefinedClass traversingVisitor, JFieldVar field) {
        String propName = Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1);
        traversingVisitor.method(JMod.PUBLIC, field.type(), "get" + propName).body()._return(field);
        JMethod setVisitor = traversingVisitor.method(JMod.PUBLIC, void.class, "set" + propName);
        JVar visParam = setVisitor.param(field.type(), "aVisitor");
        setVisitor.body().assign(field, visParam);
    }
}
