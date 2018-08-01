package com.dchan.jaxb;


import java.lang.reflect.Field;

class FieldHack {
    static Field listField;
    static {
        try {
            Class<?> defaultAccessClass = Class.forName(
                    "com.sun.tools.xjc.generator.bean.field.AbstractListField");
            listField = defaultAccessClass.getDeclaredField("field");
            listField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
