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
import java.util.HashSet;
import java.util.Optional;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "File",
        id = "ru.org.sevn.netbeans.genmodel.ModifyModelAction"
)
@ActionRegistration(
        iconBase = "ru/org/sevn/netbeans/genmodel/edit.png",
        displayName = "#CTL_ModifyModelAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 1429)
    ,
  @ActionReference(path = "Toolbars/File", position = 500)
})
@Messages("CTL_ModifyModelAction=Create Modify Model")
public final class ModifyModelAction extends BaseAction {

    public static int useFieldModify(Field f, final String clsName) {
        if (!excludedTypes.contains(clsName)) {
            f.setAccessible(true);
            Annotation[] annotations = f.getAnnotations();
            if (annotations != null) {
                for (Annotation a : annotations) {
                    //TODO
                    final String astr = a.toString();
                    if (astr.contains("@javax.persistence.") && astr.contains("Column")) {
                        if (astr.contains("insertable=false")) {//updatable=false
                            return 0;
                        }
                        if (astr.contains("updatable=false")) {
                            return 0;
                        }
                    }
                }
            }
            return 1;
        }
        return 0;
    }
    
    @Override
    protected int useField(Field f, final String clsName) {
        return useFieldModify(f, clsName);
    }
    
    @Override
    protected boolean hasPart(final boolean hasPart) {
        return false;
    }
    
    @Override
    protected String getFieldType(final String cls) {
        return "Optional<" + Util.getClassNameShort(cls) + ">";
    }
    
    @Override
    protected String getSetterSet(final String o) {
        return "Optional.ofNullable (" + o + ")";
    }

    @Override
    protected HashSet<String> makeUsedClasses() {
        final HashSet<String> ret = new HashSet();
        ret.add(Optional.class.getName());
        return ret;
    }

    @Override
    protected void appendSetter(final String editedFileClassName, final StringBuilder sb, final String paramName, final String cls) {
        final String clsShort = Util.getClassNameShort(cls);
        appendSetterRaw(editedFileClassName, sb, paramName, clsShort, "<T extends " + editedFileClassName + "> T", getSetterSet("o"));
        appendSetterRaw(editedFileClassName, sb, paramName, "Optional<" + clsShort +">", "<T extends " + editedFileClassName + "> T", "o");
    }
}
