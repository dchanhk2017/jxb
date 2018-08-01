package com.dchan.jaxb;

import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationValue;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.generator.bean.field.UntypedListField;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElements;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class ClassDiscoverer {

    static Set<JClass> discoverDirectClasses(Outline outline, Set<ClassOutline> classes) throws IllegalAccessException {

        Set<String> directClassNames = new LinkedHashSet<>();
        for(ClassOutline classOutline : classes) {
            List<FieldOutline> fields = findAllDeclaredAndInheritedFields(classOutline);
            for(FieldOutline fieldOutline : fields) {
                JType rawType = fieldOutline.getRawType();
                CPropertyInfo propertyInfo = fieldOutline.getPropertyInfo();
                boolean isCollection = propertyInfo.isCollection();
                if (isCollection) {
                    JClass collClazz = (JClass) rawType;
                    JClass collType = collClazz.getTypeParameters().get(0);
                    addIfDirectClass(directClassNames, collType);
                } else {
                    addIfDirectClass(directClassNames, rawType);
                }
                parseXmlAnnotations(outline, fieldOutline, directClassNames);
            }
        }

        Set<JClass> direct = directClassNames
                .stream()
                .map(cn -> outline.getCodeModel().directClass(cn))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return direct;

    }

    private static void parseXmlAnnotations(Outline outline, FieldOutline field, Set<String> directClasses) throws IllegalAccessException {
        if (field instanceof UntypedListField) {
            JFieldVar jfv = (JFieldVar) FieldHack.listField.get(field);
            for(JAnnotationUse jau : jfv.annotations()) {
                JClass jc = jau.getAnnotationClass();
                if (jc.fullName().equals(XmlElements.class.getName())) {
                    JAnnotationArrayMember value = (JAnnotationArrayMember) jau.getAnnotationMembers().get("value");
                    for(JAnnotationUse anno : value.annotations()) {
                        handleXmlElement(outline, directClasses, anno.getAnnotationMembers().get("type"));
                    }
                }
            }
        }
    }

    private static void handleXmlElement(Outline outline, Set<String> directClasses, JAnnotationValue type) {
        StringWriter sw = new StringWriter();
        JFormatter jf = new JFormatter(new PrintWriter(sw));
        type.generate(jf);
        String s = sw.toString();
        s = s.substring(0, s.length()-".class".length());
        if (!s.startsWith("java") && outline.getCodeModel()._getClass(s) == null && !foundWithinOutline(s, outline)) {
            directClasses.add(s);
        }
    }

    private static boolean foundWithinOutline(String s, Outline outline) {
        return outline.getClasses()
                .stream().map(co->co.implClass.binaryName().replaceAll("\\$", "."))
                .anyMatch(name->name.equals(s));
    }

    private static void addIfDirectClass(Set<String> directClassNames, JType collType) {
        if (collType.getClass().getName().equals("com.sun.codemodel.JDirectClass")) {
        	//Skip if the `direct`class is also available as JDefinedClass (see ISSUE-12).
        	if(collType.owner()._getClass(collType.fullName()) == null){
                directClassNames.add(collType.fullName());
        	} 
        }
    }

    static List<FieldOutline> findAllDeclaredAndInheritedFields(ClassOutline classOutline) {
        List<FieldOutline> fields = new LinkedList<>();
        ClassOutline currentClassOutline = classOutline;
        while(currentClassOutline != null) {
            fields.addAll(Arrays.asList(currentClassOutline.getDeclaredFields()));
            currentClassOutline = currentClassOutline.getSuperClass();
        }
        return fields;
    }

    private static final JType[] NONE = new JType[0];

    static JMethod getter(FieldOutline fieldOutline) {
        final JDefinedClass theClass = fieldOutline.parent().implClass;
        final String publicName = fieldOutline.getPropertyInfo().getName(true);
        final JMethod getgetter = theClass.getMethod("get" + publicName, NONE);
        if (getgetter != null) {
            return getgetter;
        } else {
            final JMethod isgetter = theClass
                    .getMethod("is" + publicName, NONE);
            if (isgetter != null) {
                return isgetter;
            } else {
                return null;
            }
        }
    }

    static boolean isJAXBElement(JType type) {
        //noinspection RedundantIfStatement
        if (type.fullName().startsWith(JAXBElement.class.getName())) {
            return true;
        }
        return false;
    }

    static List<JClass> allConcreteClasses(Set<ClassOutline> classes) {
        return allConcreteClasses(classes, Collections.emptySet());
    }

    static List<JClass> allConcreteClasses(Set<ClassOutline> classes, Set<JClass> directClasses) {
        List<JClass> results = new ArrayList<>();
        classes.stream()
                .filter(classOutline -> !classOutline.target.isAbstract())
                .forEach(classOutline -> {
            JClass implClass = classOutline.implClass;
            results.add(implClass);
        });
        results.addAll(directClasses);

        return results;
    }
}
