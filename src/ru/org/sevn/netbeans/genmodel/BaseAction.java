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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    }
    
    protected String getFormatted(final FileObject editedFile, final String selectedString) {
        if (editedFile != null) {
            if (editedFile.getExt() != null) {
                switch(editedFile.getExt().toLowerCase()) {
                    case ".java":
                    case "java":
                        return formatJava(editedFile, selectedString);
                }
            }
        }
        return selectedString;
    }

    protected void format(final FileObject editedFile) {
        String selectedStr = Util.selectAll();
        
        Util.setSelectedString(getFormatted(editedFile, selectedStr));
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

    protected abstract int useField(final Field f, final String clsName);
    
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
        return false;
    }
    
    protected boolean addInUsed(final Field f) {
        return !(f.getType().isArray() || 
                f.getType().isPrimitive() || 
                f.getType().isSynthetic() ||
                f.getType().getName().startsWith("java.lang.")
                );
    }
    
    protected String formatJava(final FileObject editedFile, final String selectedString) {
        final SourceGroup sg = Util.getSourceGroup(editedFile);
        if (sg != null) {
            final String editedFileClassNameFull = Util.getClassName(editedFile);
            final String editedFileClassName = Util.getClassNameShort(editedFileClassNameFull);
            final String editedFilePackage = Util.getPackage(editedFileClassNameFull);
            final String beginStr = "//<gen>";
            final String endStr = "</gen>";
            final StringBuilder sb = new StringBuilder();
            final HashSet<String> usedClasses = makeUsedClasses();
            final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            if (selectedString.startsWith(beginStr)) {
                final int endName = selectedString.indexOf(endStr);
                final String srcClassName = selectedString.substring(beginStr.length(), endName);
                sb.append(beginStr).append(srcClassName).append(endStr).append("\n");
                sb.append("package ").append(editedFilePackage).append(";").append("\n");
                sb.append("\n");

                boolean hasPart = false;
                Class srcClass = Util.loadClassAny(srcClassName, sg.getRootFolder());
                if (srcClass != null) {
                    for (Field f : srcClass.getDeclaredFields()) {
                        if (!isExcluded(f)) {
                            final String clsName = f.getType().getName().toString();
                            final int useField = useField(f, clsName);
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
                usedClasses.remove(editedFileClassNameFull);
                for (Iterator<String> it = usedClasses.iterator(); it.hasNext();) {
                    String cl = it.next();
                    sb.append("import ").append(cl).append(";").append("\n");
                }
                sb.append("\n");
                sb.append("public class ").append(editedFileClassName).append("{").append("\n");
                sb.append("\n");
                if (hasPart(hasPart)) {
                    sb.append("    private final Modify").append(Util.getClassNameShort(srcClassName)).append("Model part = new Modify").append(Util.getClassNameShort(srcClassName)).append("Model ()").append(";").append("\n");
                    sb.append("\n");
                    sb.append("    public Modify").append(Util.getClassNameShort(srcClassName)).append("Model getPart").append("() {").append("\n");
                    sb.append("        return this.part").append(";").append("\n");
                    sb.append("    }").append("\n");
                    sb.append("\n");
                }
                for (String k : fields.keySet()) {
                    final String cls = fields.get(k);
                    sb.append("    private ").append(getFieldType(Util.getClassNameShort(cls))).append(" ").append(k).append(";").append("\n");
                }
                for (String k : fields.keySet()) {
                    final String cls = fields.get(k);
                    sb.append("\n");
                    sb.append("    public ").append(getFieldType(cls)).append(" get").append(Util.toCamelCase(k)).append("() {").append("\n");
                    sb.append("        return ").append(k).append(";").append("\n");
                    sb.append("    }").append("\n");
                    sb.append("\n");
                    sb.append("    public ").append("void").append(" set").append(Util.toCamelCase(k)).append("(final ").append(Util.getClassNameShort(cls)).append(" o) {").append("\n");
                    sb.append("        this.").append(k).append(" = ").append(getSetterSet("o")).append(";").append("\n");
                    sb.append("    }").append("\n");
                }
                sb.append("\n");
                sb.append("}\n");

            }
            sb.append("//=====================end==============================\n");
            return sb.toString() + selectedString;
        }
        return selectedString;
    }
    
    protected String getFieldType(final String cls) {
        return Util.getClassNameShort(cls);
    }
    
    protected String getSetterSet(final String o) {
        return o;
    }
    
}
