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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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
import static ru.org.sevn.netbeans.genmodel.BaseAction.excludedFields;
import static ru.org.sevn.netbeans.genmodel.BaseAction.getClassFields;

@ActionID(
        category = "File",
        id = "ru.org.sevn.netbeans.genmodel.InterfaceAction"
)
@ActionRegistration(
        iconBase = "ru/org/sevn/netbeans/genmodel/i.png",
        displayName = "#CTL_InterfaceAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 1433, separatorAfter = 1434)
    ,
  @ActionReference(path = "Toolbars/File", position = 600)
})
@Messages("CTL_InterfaceAction=UML class diagram")
public final class UMLAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        final List<FileObject> editedFiles = new ArrayList<>();
        
        for (final Lookup.Provider provider : Util.getCurrentEditors()) {
            FileObject fileObject = getJavaFileObject(provider);
            if (fileObject != null) {
                editedFiles.add(fileObject);
            }
        }
        if (editedFiles.size() > 0) {
            format(getJavaFileObject(Util.getCurrentEditor()), editedFiles, "ru.sifox.mpbx.entity"); //TODO
            return ;
        }
        
        Util.err("Can't find file name");
    }
    
    private FileObject getJavaFileObject(final Lookup.Provider provider) {
        if (provider != null) {
            DataObject dataObject = provider.getLookup().lookup(DataObject.class);
            FileObject fileObject = provider.getLookup().lookup(FileObject.class);
            if (null != dataObject || null != fileObject) {
                final FileObject editedFile;
                if (null != dataObject) {
                    editedFile = Util.getFileObjectWithShadowSupport(dataObject);
                } else {
                    editedFile = fileObject;
                }
                if (editedFile != null && editedFile.getPath() != null && editedFile.getExt() != null && isJava(editedFile.getExt().toLowerCase())) {
                    return editedFile;
                }
            }
        }
        return null;
    }
    
    private boolean isJava(final String ext) {
        switch(ext) {
            case ".java":
            case "java":
                return true;
        }
        return false;
    }

    private void format(final FileObject current, final List<FileObject> editedFiles, final String... inPkg) {
        final Map<String, Clazz> usedClasses = new LinkedHashMap<>();
        final List<Class> openedClasses = new ArrayList<>();
        
        for (final FileObject editedFile : editedFiles) {
            final Class srcClass = getClass(editedFile);
            if (srcClass != null) {
                System.out.println("-------------o-"+srcClass.getName());
                openedClasses.add(srcClass);
            }
        }
        for (final Class srcClass : openedClasses) {
            new Clazz(srcClass, usedClasses, openedClasses, 0, -1, inPkg);
        }
        if (openedClasses.size() > 0) {
            formatJava(getClass(current), openedClasses, usedClasses, "ru.sifox.mpbx.entity"); //TODO
            return;
        }
        
        Util.err("Can't generate uml");
    }
    
    private Class getClass(final FileObject editedFile) {
        final SourceGroup sg = Util.getSourceGroup(editedFile);
        if (sg != null) {
            final String editedFileClassNameFull = Util.getClassName(editedFile);

            return Util.loadClassAny(editedFileClassNameFull, sg.getRootFolder());
        }
        return null;
    }
    
    protected String getExtends (final String editedFileClassName) {
        return "";
    }
    
    protected String getClassNameWithAccess(final String editedFileClassName) {
        return "public interface " + editedFileClassName + getExtends(editedFileClassName);
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
    
    static class Clazz {
        private final Class clazz;
        private final boolean isabstract;
        private final String fullname;
        private final String name;
        private final String pkg;
        private final List<Clazz> extend = new ArrayList<>();
        private final Map<String, Clazz> fields = new LinkedHashMap<>();
        private String classType;
        private String displayName;
        
        private boolean keepIt(final Class srcClass, final List<Class> openedClasses, final String... inPkg) {
            if (openedClasses != null && openedClasses.size() > 1) {
                return openedClasses.contains(srcClass);
            }
            for (final String p : inPkg) {
                if (srcClass.getPackage() != null && srcClass.getPackage().getName().contains(p)) {
                    return true;
                }
            }
            return false;
        }
        public Clazz(final Class srcClass) {
            this.clazz = srcClass;
            final int mod = srcClass.getModifiers();
            if (srcClass.isInterface()) {
                classType = "interface";
            } else if (srcClass.isEnum()) {
                classType = "enum";
            } else {
                classType = "class";
            }
            if (srcClass.getPackage() == null) {
                this.pkg = null;
            } else {
                this.pkg = srcClass.getPackage().getName();
            }
            this.name = srcClass.getSimpleName();
            this.fullname = srcClass.getName();
            this.displayName = name;
            this.isabstract = Modifier.isAbstract(mod);
        }
        public Clazz(final Class srcClass, final Map<String, Clazz> classes, final List<Class> openedClasses, final int level, final int maxLevel, final String... inPkg ) {
            this(srcClass);

            //TODO order for extends
            if (keepIt(srcClass, openedClasses, inPkg) && classes != null) {
                classes.put(fullname, this);
            }

            final Class sclass = srcClass.getSuperclass();
            if (sclass == null || sclass.isAssignableFrom(Object.class)) {
                
            } else {
                exts(sclass, classes, openedClasses, level, maxLevel, inPkg);
            }
            for (final Class i : srcClass.getInterfaces()) {
                exts(i, classes, openedClasses, level, maxLevel, inPkg);
            }
            
            for (Field f : srcClass.getDeclaredFields()) {
                if (isExcluded(f)) continue;
                
                final Clazz c = classes.get(f.getType().getName());
                if (c == null) {
                    if (keepIt(f.getType(), openedClasses, inPkg)) {
                        if (level + 1 < maxLevel || maxLevel < 0) {
                            new Clazz(f.getType(), classes, openedClasses, level + 1, maxLevel, inPkg);
                        }
                    }
                }
                final int mod = f.getModifiers();
                String fname = f.getName();
                if (Modifier.isPrivate(mod)) {
                    fname = " - " + fname;
                } else if (Modifier.isProtected(mod)) {
                    fname = " # " + fname;
                    
                } else if (Modifier.isPublic(mod)) {
                    fname = " + " + fname;
                    
                }
                if (Modifier.isStatic(mod)) {
                    fname = " {static} " + fname;
                }
                if (clazz.isEnum()) {
                    fields.put(fname, null);
                } else {
                    fields.put(fname, new Clazz(f.getType()));
                }
            }
        }
        public void useFullName() {
            this.displayName = name;
        }
        
        public void collectLinks(final Map<String, Set<String>> links, final Map<String, Clazz> classes) {
            Set<String> others = links.get(this.displayName);
            if (others == null) {
                others = new HashSet<>();
                links.put(this.displayName, others);
            }
            for (final String f : fields.keySet()) {
                final Clazz fieldType = fields.get(f);
                if (fieldType != null) {
                    Clazz keep = classes.get(fieldType.name);
                    if (keep == null) {
                        keep = classes.get(fieldType.fullname);
                    }
                    if (keep != null) {
                        if (!others.contains(keep.displayName)) {
                            others.add(keep.displayName);
                        }
                    }
                }
            }
        }
        
        public String toString(final Map<String, Set<String>> links, final Map<String, Clazz> classes) {
            collectLinks(links, classes);
            final StringBuilder sb = new StringBuilder();
            sb.append("    ");
            if (!clazz.isInterface()) {
                sb.append(isabstract ? "abstract " : "");
            }
            sb.append(classType).append(" ");
            sb.append(displayName).append(" ");
            if (extend.size() > 0) {
                sb.append("extends ");
                for (int i = 0; i < extend.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(extend.get(i).displayName);
                }
            }
            sb.append("{\n");
            for (final String f : fields.keySet()) {
                sb.append("        ").append(f);
                if (fields.get(f) != null) {
                    sb.append(" : ").append(fields.get(f).name).append("\n");
                } else {
                    sb.append("\n");
                }
            }
            sb.append("    }\n");
            return sb.toString();
        }
        
        
        private void exts(final Class sclass, final Map<String, Clazz> classes, final List<Class> openedClasses, final int level, final int maxLevel, final String... inPkg) {
            if (level + 1 < maxLevel || maxLevel < 0) {
                Clazz sclazz = classes.get(sclass.getName());
                if (sclazz == null) {
                    if (keepIt(sclass, openedClasses, inPkg)) {
                        sclazz = new Clazz(sclass, classes, openedClasses, level + 1, maxLevel, inPkg);
                    }
                }
                if (sclazz != null) {
                    extend.add(sclazz);
                }
            }
        }

        private boolean isExcluded(Field f) {
            if (excludedFields.contains(f.getName())) {
                return true;
            }
            if (f.getName().startsWith("_persistence_")) {
                return true;
            }
            if (f.getName().startsWith("$VALUES")) {
                return true;
            }
            return false;
        }
    }
    private void formatJava(final Class current, final List<Class> openedClasses, final Map<String, Clazz> usedClasses, final String... inPkg) {
        final File outDir = new File("/tmp/uml-new");
        outDir.mkdirs();
        
        if (openedClasses.size() > 1 && current != null) {
            final Map<String, Clazz> classes = new LinkedHashMap<>();
            
            for(final Class c : openedClasses) {
                System.out.println("-------------1-"+c.getName()+":"+c.isEnum());
                new Clazz(c, classes, openedClasses, 0, 2, inPkg); 
            }
            final StringBuilder sb = printClasses(classes);
            
            File out = new File(outDir, current.getName() + ".puml");
            out.getParentFile().mkdirs();
            try {
                Files.write(out.toPath(), sb.toString().getBytes("UTF-8"));
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        } else {
            for (final Clazz c : usedClasses.values()) {
                if (!c.clazz.isEnum() && !c.clazz.isInterface()) {
                    System.out.println("---------------"+c.clazz.getName()+":"+c.clazz.isEnum());
                    fillClass(outDir, c.clazz, inPkg);
                }
            }
        }
        //ask(editedFile, sb);
    }
    
    private StringBuilder printClasses(final Map<String, Clazz> classes) {
        final Map<String, Clazz> classesDisplay = new LinkedHashMap<>();
        final Map<String, Set<String>> links = new LinkedHashMap();
        final StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("'=====================GENERATED========================\n");
        for(final String k : classes.keySet()) {
            final Clazz cl = classes.get(k);
            final Clazz clTheSameName = classesDisplay.get(cl.displayName);
            if (clTheSameName != null) {
                cl.useFullName();
            }
            classesDisplay.put(cl.displayName, cl);
        }
        final ArrayList<Clazz> classes2print = new ArrayList<>(classes.values());
        Collections.reverse(classes2print);
        for(final Clazz cl : classes2print) {
            final String p = cl.pkg;
            sb.append("package ").append(p).append(" {\n");
            sb.append(cl.toString(links, classes)).append("\n");
            sb.append("}\n");
        }
        
        for(final String l : links.keySet()) {
            for (String t : links.get(l)) {
                sb.append(l).append(" --> ").append(t).append("\n");
            }
        }
        sb.append("'=====================end==============================\n");
        sb.append("@enduml\n");
        return sb;
    }
    
    private void fillClass(final File outDir, final Class srcClass, final String... inPkg) {
        final Map<String, Clazz> classes = new LinkedHashMap<>();
        new Clazz(srcClass, classes, null, 0, 2, inPkg); 

        final StringBuilder sb = printClasses(classes);

        //File out = new File(new File(outDir, srcClass.getPackage().getName().replace(".", "/")), srcClass.getName() + ".puml");
        File out = new File(outDir, srcClass.getName() + ".puml");
        out.getParentFile().mkdirs();
        try {
            Files.write(out.toPath(), sb.toString().getBytes("UTF-8"));
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        
    }
    
    private void ask(final FileObject editedFile, final StringBuilder sb) {
        final String genName = getGenName(editedFile.getName());
        final FileObject dir = editedFile.getParent();
        final FileObject genFile = dir.getFileObject(genName, "puml");
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
                    final FileObject createdFile = dir.createData(genName, "puml");
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
