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
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.SourceGroup;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import static ru.org.sevn.netbeans.genmodel.BaseAction.getClassFields;

//@ActionID(
//        category = "File",
//        id = "ru.org.sevn.netbeans.genmodel.InterfaceAction"
//)
//@ActionRegistration(
//        iconBase = "ru/org/sevn/netbeans/genmodel/i.png",
//        displayName = "#CTL_InterfaceAction"
//)
//@ActionReferences({
//    @ActionReference(path = "Menu/File", position = 1433, separatorAfter = 1434)
//    ,
//  @ActionReference(path = "Toolbars/File", position = 600)
//})
//@Messages("CTL_InterfaceAction=Create Entity Interface")
public final class InterfaceAction implements ActionListener {

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

    private void format(final FileObject editedFile) {
        if (editedFile != null) {
            if (editedFile.getExt() != null) {
                switch(editedFile.getExt().toLowerCase()) {
                    case ".java":
                    case "java":
                        final SourceGroup sg = Util.getSourceGroup(editedFile);
                        if (sg != null) {
                            formatJava(sg, editedFile);
                            return;
                        }
                }
            }
        }
        Util.err("Can't generate interface");
    }
    
    protected String getExtends (final String editedFileClassName) {
        return "";
    }
    
    protected String getClassNameWithAccess(final String editedFileClassName) {
        return "public interface " + editedFileClassName + getExtends(editedFileClassName);
    }
    
    protected HashSet<String> makeUsedClasses() {
        return new HashSet();
    }
    
    private static boolean addInUsed(final Class cls, final String editedFilePackage) {
        final String pkg = Util.getPackage(cls.getName());

        return !(cls.isArray() || 
                cls.isPrimitive() || 
                cls.isSynthetic() ||
                cls.getName().startsWith("java.lang.") ||
                editedFilePackage.equals(pkg)
                );
    }
    
    public static StringBuilder getMethods(final HashSet<String> usedClasses, final Class cls) {
        final String editedFilePackage = Util.getPackage(cls.getName());
        final HashMap<String, StringBuilder> ret = new HashMap();
        if (cls != null) {
            for (final Method m : cls.getDeclaredMethods()) {
                if (m.getName().startsWith("get") && m.getParameterCount() == 0 ||
                        m.getName().startsWith("is") && m.getParameterCount() == 0
                        ) {
                    
                    final Class returnType = m.getReturnType();
                    if (addInUsed(returnType, editedFilePackage)) {
                        usedClasses.add(returnType.getName());
                    }
                    ret.put(m.getName(), 
                        new StringBuilder().append("    ").append(Util.getClassNameShort(returnType.toString())).append(" ").append(m.getName()).append("();\n")
                            );
                } else if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
                    final Class setType = m.getParameterTypes()[0];
                    if (addInUsed(setType, editedFilePackage)) {
                        usedClasses.add(setType.getName());
                    }
                    ret.put(m.getName(), 
                        new StringBuilder().append("    ").append("void").append(" ").append(m.getName()).append("(").append(Util.getClassNameShort(setType.toString())).append(" the").append(Util.getClassNameShort(setType.toString(), "")).append(");\n")
                    );
                }
            }
        }
        ArrayList<String> lst = new ArrayList(ret.keySet());
        Collections.sort(lst);
        final StringBuilder sb = new StringBuilder();
        for (final String k : lst) {
            sb.append(ret.get(k));
        }
        return sb;
    }
    
    private String getGenName(final String n) {
        if (n.endsWith("Entity")) {
            return n.substring(0, n.length() - "Entity".length());
        } else {
            return n + "Stub";
        }
    }
    private void formatJava(final SourceGroup sg, final FileObject editedFile) {
        final String editedFileClassNameFull = Util.getClassName(editedFile);
        final String editedFileClassName = Util.getClassNameShort(editedFileClassNameFull);
        final String editedFilePackage = Util.getPackage(editedFileClassNameFull);
        final String genName = getGenName(editedFile.getName());
        
        final FileObject dir = editedFile.getParent();
        final FileObject genFile = dir.getFileObject(genName, "java");
        final StringBuilder sb = new StringBuilder();
        final HashSet<String> usedClasses = makeUsedClasses();
        final Class srcClass = Util.loadClassAny(editedFileClassNameFull, sg.getRootFolder());
        
        sb.append("//=====================GENERATED========================\n");
        sb.append("package ").append(editedFilePackage).append(";").append("\n");
        sb.append("\n");
        
        String extendsStr = "";
        
        final StringBuilder methods;
        if (srcClass == null) {
            methods = null;
            sb.append("//Can't load source class:" + editedFileClassNameFull);
        } else {
            final Class superCls = srcClass.getSuperclass();
            if (superCls != null) {
                if (!superCls.getName().startsWith("java.lang.")) {
                    extendsStr = " extends " + getGenName(Util.getClassNameShort(superCls.getName())) + " ";
                    if (addInUsed(superCls, editedFilePackage)) {
                        usedClasses.add(superCls.getName());
                    }
                }
            }
            
            methods = getMethods(usedClasses, srcClass);
            usedClasses.remove(editedFileClassNameFull);
            for (Iterator<String> it = usedClasses.iterator(); it.hasNext();) {
                String cl = it.next();
                sb.append("import ").append(cl).append(";").append("\n");
            }
            sb.append("\n");
        }
        
        sb.append(getClassNameWithAccess(genName)).append(extendsStr).append("{").append("\n");
        sb.append("\n");
        if (methods != null) {
            sb.append(methods);
        }
        
        sb.append("}\n");
        sb.append("//").append(editedFileClassName).append("\n");
        sb.append("//=====================end==============================\n");

        Object askres = Util.ask("Copy result to clipboard?", sb.toString(), new Object[] { "Copy", "Append", "Insert", "Cancel" }, "Append");
        if ( "Copy".equals(askres) ) {
            if (!Util.setClipboardContents(sb.toString())) {
                Util.err("Can't copy to clipboard");
            }
        } else if ( "Append".equals(askres) ) {
            if (!Util.appendClipboardContents(sb.toString())) {
                Util.err("Can't append to clipboard");
            }
        } else if ( "Insert".equals(askres) ) {
            if (genFile == null) {
                try {
                    final FileObject createdFile = dir.createData(genName, "java");
                    try (final OutputStream os = createdFile.getOutputStream()) {
                        os.write(sb.toString().getBytes("UTF-8"));
                    }
                } catch (IOException ex) {
                    Util.err("Can't create file:" + genName + ".java");
                }
            } else {
                Util.err("Can't generate interface: file exists :" + genName + ".java");
            }
        } else {
            // nothing
        }
    }
}
