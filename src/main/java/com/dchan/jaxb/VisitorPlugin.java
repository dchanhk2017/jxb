package com.dchan.jaxb;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JPackage;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;

import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.model.CClassInfoParent;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.PackageOutline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public class VisitorPlugin extends Plugin {
    
    private String packageName;
    private boolean includeType = false;

    private boolean generateClasses = true;

    @Override
    public String getOptionName() {
        return "Xvisitor";
    }

    @Override
    public String getUsage() {
        return null;
    }
    
    @Override
    public int parseArgument(Options opt, String[] args, int index) throws BadCommandLineException, IOException {
    	
    	// look for the visitor-package argument since we'll use this for package name for our generated code.
        String arg = args[index];
        if (arg.startsWith("-Xvisitor-package:")) {
            packageName = arg.split(":")[1];
            return 1;
        }
        if (arg.startsWith("-Xvisitor-includeType:")) {
            includeType = "true".equalsIgnoreCase(arg.split(":")[1]);
            return 1;
        }
        if (arg.equals("-Xvisitor-includeType")) {
            includeType = true;
            return 1;
        }
        if (arg.equals("-Xvisitor-noClasses")) {
            generateClasses = false;
            return 1;
        }
        return 0;
    }

    @Override
    public boolean run(Outline outline, Options options, ErrorHandler errorHandler)
            throws SAXException {
        try {

            JPackage vizPackage = getOrCreatePackageForVisitors(outline);

            Set<ClassOutline> sorted = sortClasses(outline);

            Set<JClass> directClasses = ClassDiscoverer.discoverDirectClasses(outline, sorted);

            CreateJAXBElementNameCallback cni =
                    new CreateJAXBElementNameCallback(outline, vizPackage);
            cni.run(sorted, directClasses);

            Function<String,String> visitMethodNamer;
            Function<String,String> traverseMethodNamer;
            if (includeType) {
                visitMethodNamer = s -> "visit" + s;
                traverseMethodNamer = s -> "traverse" + s;
            } else {
                visitMethodNamer = s -> "visit";
                traverseMethodNamer = s -> "traverse";
            }


            // create visitor interface
            CreateVisitorInterface createVisitorInterface =
                    new CreateVisitorInterface(outline, vizPackage, visitMethodNamer);
            createVisitorInterface.run(sorted, directClasses);
            JDefinedClass visitor = createVisitorInterface.getOutput();
            
            // create visitable interface and have all the beans implement it
            CreateVisitableInterface createVisitableInterface =
                    new CreateVisitableInterface(visitor, outline, vizPackage);
            createVisitableInterface.run(sorted, directClasses);
            JDefinedClass visitable = createVisitableInterface.getOutput();
            
            // add accept method to beans
            AddAcceptMethod addAcceptMethod = new AddAcceptMethod(visitMethodNamer);
            addAcceptMethod.run(sorted, visitor);
            
            // create traverser interface
            CreateTraverserInterface createTraverserInterface =
                    new CreateTraverserInterface(visitor, outline, vizPackage, traverseMethodNamer);
            createTraverserInterface.run(sorted, directClasses);
            JDefinedClass traverser = createTraverserInterface.getOutput();

            // create progress monitor for traversing visitor
            CreateTraversingVisitorProgressMonitorInterface progMon =
                    new CreateTraversingVisitorProgressMonitorInterface(
                            outline, vizPackage);
            progMon.run(sorted, directClasses);
            JDefinedClass progressMonitor = progMon.getOutput();

            if (generateClasses) {
                // create base visitor class
                CreateBaseVisitorClass createBaseVisitorClass =
                        new CreateBaseVisitorClass(visitor, outline, vizPackage, visitMethodNamer);
                createBaseVisitorClass.run(sorted, directClasses);

                // create default generic depth first traverser class
                CreateDepthFirstTraverserClass createDepthFirstTraverserClass =
                        new CreateDepthFirstTraverserClass(visitor, traverser,
                                visitable, outline, vizPackage, traverseMethodNamer);
                createDepthFirstTraverserClass.run(sorted, directClasses);

                // create traversing visitor class
                CreateTraversingVisitorClass createTraversingVisitorClass =
                        new CreateTraversingVisitorClass(visitor, progressMonitor,
                                traverser, outline, vizPackage, visitMethodNamer, traverseMethodNamer);
                createTraversingVisitorClass.run(sorted, directClasses);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }

    private Set<ClassOutline> sortClasses(Outline outline) {
        Set<ClassOutline> sorted = new TreeSet<>((aOne, aTwo) -> {
            String one = aOne.implClass.fullName();
            String two = aTwo.implClass.fullName();
            return one.compareTo(two);
        });
        sorted.addAll(outline.getClasses());
        return sorted;
    }

    private JPackage getOrCreatePackageForVisitors(Outline outline) {
        JPackage vizPackage = null;
        if (getPackageName() != null) {
            JPackage root = outline.getCodeModel().rootPackage();
            String[] packages = getPackageName().split("\\.");
            JPackage current = root;
            for(String p : packages) {
                current = current.subPackage(p);
            }
            vizPackage = current;
        }
        if (vizPackage == null) {
            PackageOutline packageOutline = outline.getAllPackageContexts().iterator().next();
            CClassInfoParent.Package pkage = new CClassInfoParent.Package(packageOutline._package());
            vizPackage = (JPackage) outline.getContainer(pkage, Aspect.IMPLEMENTATION);
        }
        return vizPackage;
    }
    
    @SuppressWarnings("WeakerAccess")
    public String getPackageName() {
        return packageName;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

}
