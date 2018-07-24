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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.datatransfer.ExClipboard;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

public class Util {

    private static final String JAVA_MIME_TYPE = "text/x-java";

    public static String toCamelCase(String s) {
        if (s.length() > 1) {
            return s.substring(0, 1).toUpperCase().concat(s.substring(1));
        }
        return s.toUpperCase();
    }

    public static Class loadResourceClassAny(String name, FileObject fileInProject) throws ClassNotFoundException {
        try {
            return loadResourceClass(name, fileInProject);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    public static String getClassNamePackage(final String className) {
        if (className != null) {
            final int lastPoint = className.lastIndexOf(".");
            if (lastPoint > 0) {
                return className.substring(0, lastPoint);
            }
        }
        return null;
    }
    
    public static String getClassNameShort(final String className) {
        if (className != null) {
            final int lastPoint = className.lastIndexOf(".");
            if (lastPoint > 0) {
                return className.substring(lastPoint + 1);
            }
        }
        return className;
    }

    public static Class loadClass(String name, FileObject fileInProject) throws ClassNotFoundException {
        try {
            return loadCompileClass(name, fileInProject);
        } catch (ClassNotFoundException ex) {
            return loadResourceClass(name, fileInProject);
        }
    }

    public static boolean isJavaFile(FileObject f) {
        return JAVA_MIME_TYPE.equals(f.getMIMEType());
    }

    public static String getClassName(final FileObject editedFile) {
        final SourceGroup sg = getSourceGroup(editedFile);
        if (sg != null) {
            File dir = new File(sg.getRootFolder().getPath());
            File fl = new File(editedFile.getPath());
            Path dirPath = dir.toPath();
            Path flPath = fl.toPath();
            final String relativeName = dirPath.relativize(flPath).toString();
            final int lastPoint = relativeName.lastIndexOf(".");
            if (lastPoint > 0) {
                return relativeName.substring(0, lastPoint).replace(File.separator, ".");
            } else {
                return relativeName.replace(File.separator, ".");
            }
        }
        return null;
    }

    public static Class loadClass(String name, FileObject fileInProject, String pathType) throws ClassNotFoundException {
        ClassPath classPath = ClassPath.getClassPath(fileInProject, pathType);
        if (classPath == null) {
            throw new ClassNotFoundException(name);
        } else {
            return classPath.getClassLoader(true).loadClass(name);
        }
    }

    public static Class loadCompileClass(String name, FileObject fileInProject) throws ClassNotFoundException {
        return loadClass(name, fileInProject, ClassPath.COMPILE);
    }
    
    public static Class loadClassAny(String name, FileObject fileInProject) {
        for (final String pathType : new String[] {ClassPath.COMPILE, ClassPath.SOURCE, ClassPath.EXECUTE}) {
            try {
                Class cls = loadClass(name, fileInProject);
                if (cls != null) {
                    return cls;
                }
            } catch (Throwable ex) {
            }
        }
        return null;
    }

    public static File getFile(final String className, final SourceGroup sg) {
        final String relativeName = className.replace(".", File.separator) + ".java";
        return new File(FileUtil.toFile(sg.getRootFolder()), relativeName);
    }

    public static boolean isOnSourceClasspath(FileObject fo) {
        Project p = FileOwnerQuery.getOwner(fo);
        if (p == null) {
            return false;
        }
        if (OpenProjects.getDefault().isProjectOpen(p)) {
            SourceGroup[] gr = ProjectUtils.getSources(p).getSourceGroups("java");
            for (int j = 0; j < gr.length; j++) {
                if (fo.equals(gr[j].getRootFolder())) {
                    return true;
                }
                if (FileUtil.isParentOf(gr[j].getRootFolder(), fo)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public static Class loadResourceClass(String name, FileObject fileInProject) throws ClassNotFoundException {
        int i = name.indexOf('$');
        String resourceName = (i > -1 ? name.substring(0, i) : name).replace('.', '/') + ".java";
        if (ClassPath.getClassPath(fileInProject, ClassPath.SOURCE).findResource(resourceName) != null) {
            final ClassLoader loader = ClassPath.getClassPath(fileInProject, ClassPath.EXECUTE).getClassLoader(true);
            return loader.loadClass(name);
        }
        throw new ClassNotFoundException(name);
    }

    public static Class loadCompileClassAny(String name, FileObject fileInProject) {
        try {
            return loadCompileClass(name, fileInProject);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    public static SourceGroup getSourceGroup(final FileObject fo) {
        Project project = FileOwnerQuery.getOwner(fo);
        if (project != null) {
            SourceGroup[] sourceGroups = ProjectUtils.getSources(project).getSourceGroups("java");
            for (int i = 0; i < sourceGroups.length; i++) {
                if (fo == sourceGroups[i].getRootFolder() || FileUtil.isParentOf(sourceGroups[i].getRootFolder(), fo)) {
                    return sourceGroups[i];
                }
            }
        }
        return null;
    }

    public static String getPackage(final String className) {
        if (className != null) {
            final int lastPoint = className.lastIndexOf(".");
            if (lastPoint > 0) {
                return className.substring(0, lastPoint);
            }
        }
        return null;
    }

    public static void setSelectedString(final String str) {
        if (str != null) {
            EditorRegistry.lastFocusedComponent().replaceSelection(str);
        }
    }

    public static TopComponent getCurrentEditor() {
        WindowManager wm = WindowManager.getDefault();
        Mode editor = wm.findMode("editor");
        return editor.getSelectedTopComponent();
    }

    public static FileObject getFileObjectWithShadowSupport(DataObject dataObject) {
        if (dataObject instanceof DataShadow) {
            DataShadow dataShadow = (DataShadow) dataObject;
            return dataShadow.getOriginal().getPrimaryFile();
        }
        return dataObject.getPrimaryFile();
    }

    public static void err(final String msg) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(msg, NotifyDescriptor.ERROR_MESSAGE));
    }

    public static String selectAll() {
        EditorRegistry.lastFocusedComponent().selectAll();
        return EditorRegistry.lastFocusedComponent().getSelectedText();
    }

    public static FileObject getCurrentProjectDir() {
        Lookup lookup = Utilities.actionsGlobalContext();
        Project project = lookup.lookup(Project.class);
        if (project != null) {
            return project.getProjectDirectory();
        }
        return null;
    }

    public static boolean inOpenedProject(final FileObject editedFile) {
        final Project project = FileOwnerQuery.getOwner(editedFile);
        if (project != null) {
            if (OpenProjects.getDefault().isProjectOpen(project)) {
                return true;
            }
        }
        return false;
    }

    public static boolean askYesNo(final String title, final String msg) {
        NotifyDescriptor d = new NotifyDescriptor.Confirmation(msg, title, NotifyDescriptor.YES_NO_OPTION);
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION) {
            return true;
        }
        return false;
    }
    
    public static Object ask(final String title, final String msg, final Object[] options, final Object defaultOption) {
        JTextArea textField = new JTextArea();
        textField.setEditable(false);
        textField.setText(msg);
//        textField.setLineWrap(true);
//        textField.setWrapStyleWord(true);        
        
        DialogDescriptor dialogDsc = new DialogDescriptor(textField, title, true, options, defaultOption, DialogDescriptor.DEFAULT_ALIGN, null, null);
        dialogDsc.setOptions(options);
        return DialogDisplayer.getDefault().notify(dialogDsc);
    }
    
    public static void notify(final String msg) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(msg, NotifyDescriptor.INFORMATION_MESSAGE));
    }

    public static Project getMainProject() {
        return OpenProjects.getDefault().getMainProject();        
    }
    
    public static Project[] getOpenedProjects() {
        return OpenProjects.getDefault().getOpenProjects();
    }

    public static String getClipboardString() {
        final Clipboard clipboard = getClipboard();
        if (clipboard != null) {
            try {
                return (String)clipboard.getData(DataFlavor.stringFlavor);
            } catch (Exception ex) {
                ex.printStackTrace();
                //Exceptions.printStackTrace(ex);
            }
        }
        return null;
    }
    
    public static Clipboard getClipboard() {
        final Clipboard clipboard = Lookup.getDefault().lookup(ExClipboard.class);
        if (clipboard == null) {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        }
        return clipboard;
    }
    
    public static boolean setClipboardContents(final String content) {
        final Clipboard clipboard = getClipboard();
        if (clipboard != null) {
            if (content == null) {
                StatusDisplayer.getDefault().setStatusText("");
                clipboard.setContents(null, null);
                return true;
            } else {
                StatusDisplayer.getDefault().setStatusText("Clipboard: " + content);
                clipboard.setContents(new StringSelection(content), null);
                return true;
            }
        }
        return false;
    }
    
    public static boolean appendClipboardContents(final String content) {
        final String oldVal = getClipboardString();
        if (oldVal == null) {
            return setClipboardContents(content);
        } else {
            return setClipboardContents(oldVal + content);
        }
    }
}
//https://platform.netbeans.org/tutorials/nbm-copyfqn.html