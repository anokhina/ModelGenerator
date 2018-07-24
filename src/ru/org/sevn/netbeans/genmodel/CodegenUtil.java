/*
 * Copyright 2018 Veronica Anokhina.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.org.sevn.netbeans.genmodel;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class CodegenUtil {
    /*
    String getterPrefix () default "get";    
    String searchFieldName () default "";
    String operation () default "";
    String queryExpression () default "";    
    */
    
    public static Map<String, Object> getCodegen(final String className, final Field f) {
        Annotation[] annotations = f.getAnnotations();
        //System.out.println("====1="+f.getName()+":" + annotations.length);
        if (annotations != null) {
            for (final Annotation a : annotations) {
                if (a.annotationType().getName().equals(className)) { //TODO
                    HashMap<String, Object> res = new HashMap<>();
                    Method[] methods = a.annotationType().getDeclaredMethods();
                    if (methods != null) {
                        for (final Method m : methods) {
                            try {
                                Object v = m.invoke(a);
                                res.put(m.getName(), v);
                            } catch (Exception ex) {
                            }
                        }
                    }
                    return res;
                }
                //System.out.println("====2="+f.getName()+":" + a + ":" + a.annotationType().getDeclaredMethods().length);
            }
        }
        return null;
    }
    
    public static boolean isSerchable(Map<String, Object> codegen) {
        if (codegen != null) {
            Object v = codegen.get("searchable");
            if (v instanceof Boolean) {
                Boolean b = (Boolean)v;
                return b;
            }
        }
        return false;
    }
    public static boolean isSerchable(final Field f) {
        return isSerchable(getCodegen(App.instance().getCodeGenClassName(), f));
    }
    
}
