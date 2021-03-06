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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.SourceGroup;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;

public abstract class BaseAction implements ActionListener {
    
    protected static HashSet<String> excludedFields = new HashSet<>();
    protected static HashSet<String> excludedTypes = new HashSet<>();
    static {
        excludedTypes.add("org.eclipse.persistence.indirection.WeavedAttributeValueHolderInterface");
        excludedFields.add("serialVersionUID");
        excludedFields.add("recordId");
    }
    
    protected String getFormatted(final FileObject editedFile, final String selectedString, final String content) {
        if (editedFile != null) {
            if (editedFile.getExt() != null) {
                switch(editedFile.getExt().toLowerCase()) {
                    case ".java":
                    case "java":
                        return formatJava(editedFile, selectedString, content);
                }
            }
        }
        return selectedString;
    }

    protected void format(final FileObject editedFile) {
        this.caretPos = EditorRegistry.lastFocusedComponent().getCaretPosition();
        String selectedStr = EditorRegistry.lastFocusedComponent().getSelectedText();
        if (selectedStr == null || selectedStr.trim().length() == 0) {
            selectedStr = Util.getClipboardString();
        }
        final int selectionStart = EditorRegistry.lastFocusedComponent().getSelectionStart();
        final int selectionEnd = EditorRegistry.lastFocusedComponent().getSelectionEnd();
                
        final String res = getFormatted(editedFile, selectedStr, Util.selectAll());
        if (res != null) {
            Util.setSelectedString(res);
        } else {
            EditorRegistry.lastFocusedComponent().setSelectionStart(selectionStart);
            EditorRegistry.lastFocusedComponent().setSelectionEnd(selectionEnd);
        }
        try {
            EditorRegistry.lastFocusedComponent().setCaretPosition(this.caretPos);
        } catch (Exception e) {}
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        Lookup.Provider provider = Util.getCurrentEditor();
        DataObject dataObject = provider.getLookup().lookup(DataObject.class);
        FileObject fileObject = provider.getLookup().lookup(FileObject.class);
        if (null != dataObject || null != fileObject) {
            final FileObject editedFile;
            if (null != dataObject) {
                editedFile = Util.getFileObjectWithShadowSupport(dataObject);
            } else {
                editedFile = fileObject;
            }
            if (editedFile != null && editedFile.getPath() != null) {
                format(editedFile);
                return ;
            }
        }
        
        Util.err("Can't find file name");
    }

    protected abstract int useField(final Field f, final String clsName, final String editedFileClassName);
    
    protected boolean hasPart(final boolean hasPart) {
        return hasPart;
    }
    
    protected HashSet<String> makeUsedClasses() {
        return new HashSet();
    }
    
    protected boolean isExcluded(final Field f) {
        if (excludedFields.contains(f.getName())) {
            return true;
        }
        if (f.getName().startsWith("_persistence_")) {
            return true;
        }
        return false;
    }
    
    protected boolean addInUsed(final Field f) {
        return !(f.getType().isArray() || 
                f.getType().isPrimitive() || 
                f.getType().isSynthetic() ||
                f.getType().getName().startsWith("java.lang.")
                );
    }
    
    public static List<Field> getClassFields(final List<Field> fields, final Class cls) {
        if (cls != null) {
            fields.addAll(Arrays.asList(cls.getDeclaredFields()));
            if (!Object.class.equals(cls.getSuperclass())) {
                return getClassFields(fields, cls.getSuperclass());
            }
        }
        return fields;
    }
    
    protected String getExtends (final String editedFileClassName) {
        return "";
    }
    
    protected String getClassNameWithAccess(final String editedFileClassName) {
        if (editedFileClassName.startsWith("Abstract")) {
            return "public abstract class " + editedFileClassName + getExtends(editedFileClassName);
        }
        return "public class " + editedFileClassName + getExtends(editedFileClassName);
    }
    
    protected boolean fillFields(final SourceGroup sg, final String srcClassName, final String editedFileClassName, final HashSet<String> usedClasses, final Map<String, String> fields) {
        boolean hasPart = false;
        Class srcClass = Util.loadClassAny(srcClassName, sg.getRootFolder());
        if (srcClass != null) {
            for (Field f : getClassFields(new ArrayList<Field>(), srcClass)) {
                if (!isExcluded(f)) {
                    final String clsName = f.getType().getName().toString();
                    final int useField = useField(f, clsName, editedFileClassName);
                    if (useField > 0) {
                        fields.put(f.getName(), clsName);
                        if (addInUsed(f)) {
                            usedClasses.add(clsName);
                        }
                    } else if (useField < 0 && !hasPart) {
                        hasPart = true;
                    }
                }
            }
        }
        return hasPart;
    }
    
    protected void formatModelCreate(final String padding, final String modelPrefix, 
        final StringBuilder sb, 
        final SourceGroup sg, 
        final String srcClassName, 
        final String editedFileClassNameFull,
        final String editedFileClassName) {

        final String srcClassNameShort = Util.getClassNameShort(srcClassName);
        final String modelName = modelPrefix + srcClassNameShort + "Model";
        sb.append(modelName).append(".create()").append("\n");

        final HashSet<String> usedClasses = makeUsedClasses();
        final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        boolean hasPart = fillFields(sg, srcClassName, editedFileClassName, usedClasses, fields);
        for (String f : fields.keySet()) {
            sb.append(padding).append(".set").append(Util.toCamelCase(f)).append("(").append("the").append(Util.toCamelCase(f)).append(")").append("\n");
        }
        if (hasPart) {
            sb.append(padding).append(".setPart(");
            final ModifyModelAction mmodel = new ModifyModelAction();
            mmodel.formatModelCreate(padding + "    ", "Modify", sb, sg, srcClassName, editedFileClassNameFull, editedFileClassName);
            sb.append(padding).append(")\n");
        }
    }
    
    protected void formatJavaModel(final StringBuilder sb, 
            final SourceGroup sg, 
            final String srcClassName, 
            final String editedFileClassNameFull,
            final String editedFileClassName) {

        final HashSet<String> usedClasses = makeUsedClasses();
        final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            boolean hasPart = fillFields(sg, srcClassName, editedFileClassName, usedClasses, fields);
            
            usedClasses.remove(editedFileClassNameFull);
            for (Iterator<String> it = usedClasses.iterator(); it.hasNext();) {
                String cl = it.next();
                sb.append("import ").append(cl).append(";").append("\n");
            }
            sb.append("\n");
            // public class ClassName extends AbstractClassName
            sb.append(getClassNameWithAccess(editedFileClassName)).append("{").append("\n");
            sb.append("\n");
            addConstructor(editedFileClassName, sb);
            if (hasPart(hasPart)) {
                sb.append("    private Modify").append(Util.getClassNameShort(srcClassName)).append("Model part = Modify").append(Util.getClassNameShort(srcClassName)).append("Model.create ()").append(";").append("\n");
                sb.append("\n");
                appendGetter(editedFileClassName, sb, "part", "Modify" + Util.getClassNameShort(srcClassName) + "Model");
                sb.append("\n");
                appendSetterPlain(editedFileClassName, sb, "part", "Modify" + Util.getClassNameShort(srcClassName) + "Model");
                sb.append("\n");
            }
            for (String k : fields.keySet()) {
                final String cls = fields.get(k);
                sb.append("    private ").append(getFieldType(Util.getClassNameShort(cls))).append(" ").append(k).append(";").append("\n");
            }
            for (String k : fields.keySet()) {
                final String cls = fields.get(k);
                sb.append("\n");
                appendGetter(editedFileClassName, sb, k, cls);
                sb.append("\n");
                appendSetter(editedFileClassName, sb, k, cls);
            }
            sb.append("\n");
            sb.append("}\n");
    }
    
    protected boolean hasConstructor(final String editedFileClassName) {
        return true;
    }
    
    protected void addConstructor(final String editedFileClassName, final StringBuilder sb) {
        if (hasConstructor(editedFileClassName)) {
            sb.append("    private ").append(editedFileClassName).append("(){}").append("\n");
            sb.append("    public static ").append(editedFileClassName).append(" create(){ ").append("\n");
            sb.append("        return new ").append(editedFileClassName).append("();").append("\n");
            sb.append("    }").append("\n");
            sb.append("\n");
        }
    }
    
    protected void appendGetter(final String editedFileClassName, final StringBuilder sb, final String paramName, final String cls) {
        sb.append("    public ").append(getFieldType(cls)).append(" get").append(Util.toCamelCase(paramName)).append("() {").append("\n");
        sb.append("        return ").append(paramName).append(";").append("\n");
        sb.append("    }").append("\n");
    }
    
    protected void appendSetter(final String editedFileClassName, final StringBuilder sb, final String paramName, final String cls) {
        appendSetterPlain(editedFileClassName, sb, paramName, cls);
    }
    
    private void appendSetterPlain(final String editedFileClassName, final StringBuilder sb, final String paramName, final String cls) {
        appendSetterRaw(editedFileClassName, sb, paramName, Util.getClassNameShort(cls), "<T extends " + editedFileClassName + "> T", getSetterSet("o"));
    }
    
    protected void appendSetterRaw(final String editedFileClassName, final StringBuilder sb, final String paramName, final String cls, final String returnType, final String setterSet) {
        sb.append("    public ").append(returnType).append(" set").append(Util.toCamelCase(paramName)).append("(final ").append(cls).append(" o) {").append("\n");
        sb.append("        this.").append(paramName).append(" = ").append(setterSet).append(";").append("\n");
        if (!"void".equals(returnType)) {
            sb.append("        return (T)this;").append("\n");
        }
        sb.append("    }").append("\n");
    }
    
    protected String getModelPrefix() {
        return "";
    }
    
    protected String formatJava(final FileObject editedFile, final String selection, final String content) {
        final SourceGroup sg = Util.getSourceGroup(editedFile);
        if (sg != null) {
            final String editedFileClassNameFull = Util.getClassName(editedFile);
            final String editedFileClassName = Util.getClassNameShort(editedFileClassNameFull);
            final String editedFilePackage = Util.getPackage(editedFileClassNameFull);
            final StringBuilder sb = new StringBuilder();
            if (selection != null) {
                final String selectionClear = selection.trim();
                final String[] selections = selectionClear.split("\\s+");
                if (selections.length > 1) {
                    appendGetter(editedFileClassName, sb, selections[1], selections[0]);
                    appendSetter(editedFileClassName, sb, selections[1], selections[0]);
                } else if (editedFileClassName.endsWith("Service")) {
                    formatModelCreate("        ", getModelPrefix(), sb, sg, selectionClear, editedFileClassNameFull, editedFileClassName);
                } else {
                
                    sb.append("//=====================GENERATED========================\n");
                    sb.append("package ").append(editedFilePackage).append(";").append("\n");
                    sb.append("\n");

                    formatJavaModel(sb, sg, selectionClear, editedFileClassNameFull, editedFileClassName);
                }
            }
            sb.append("//").append(selection).append("\n");
            sb.append("//=====================end==============================\n");
            caretPos = sb.length();
            //TODO show dialog
            Object askres = Util.ask("Copy result to clipboard?", sb.toString(), new Object[] { "Copy", "Append", "Insert", "Cancel" }, "Append");
            if ( "Copy".equals(askres) ) {
                if (!Util.setClipboardContents(sb.toString())) {
                    return sb.toString() + content;
                }
            } else if ( "Append".equals(askres) ) {
                if (!Util.appendClipboardContents(sb.toString())) {
                    return sb.toString() + content;
                }
            } else if ( "Insert".equals(askres) ) {
                return sb.toString() + content;
            } else {
                // return null;
            }
        }
        return null;
    }
    
    private int caretPos;
    
    protected String getFieldType(final String cls) {
        return Util.getClassNameShort(cls);
    }
    
    protected String getSetterSet(final String o) {
        return o;
    }
    
}
