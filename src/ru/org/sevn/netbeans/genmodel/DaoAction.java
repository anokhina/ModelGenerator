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
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.netbeans.api.project.SourceGroup;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import static ru.org.sevn.netbeans.genmodel.BaseAction.excludedTypes;

@ActionID(
        category = "File",
        id = "ru.org.sevn.netbeans.genmodel.DaoAction"
)
@ActionRegistration(
        iconBase = "ru/org/sevn/netbeans/genmodel/dao.png",
        displayName = "#CTL_DaoAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 1429)
    ,
  @ActionReference(path = "Toolbars/File", position = 600)
})
@Messages("CTL_DaoAction=Create Dao")
public final class DaoAction extends BaseAction {

    
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
    protected void formatJavaModel(final StringBuilder sb, 
            final SourceGroup sg, 
            final String srcClassName, 
            final String editedFileClassNameFull,
            final String editedFileClassName) {

        final HashSet<String> usedClasses = makeUsedClasses();
        usedClasses.add("javax.persistence.EntityManager");
        usedClasses.add("javax.persistence.PersistenceContext");
        usedClasses.add("org.springframework.stereotype.Component");

        usedClasses.add(srcClassName);
        
        final LinkedHashMap<String, String> fieldsCreate = new LinkedHashMap<>();
        final LinkedHashMap<String, String> fieldsModify = new LinkedHashMap<>();
            boolean hasPart = false;
            Class srcClass = Util.loadClassAny(srcClassName, sg.getRootFolder());
            if (srcClass != null) {
                for (Field f : srcClass.getDeclaredFields()) {
                    if (!isExcluded(f)) {
                        final String clsName = f.getType().getName().toString();
                        final int useFieldCreate = CreateModelAction.useFieldCreate(f, clsName);
                        final int useFieldModify = ModifyModelAction.useFieldModify(f, clsName);
                        if (useFieldCreate > 0) {
                            fieldsCreate.put(f.getName(), clsName);
                        } else if (useFieldCreate < 0 && !hasPart) {
                            hasPart = true;
                        }
                        if (useFieldModify > 0) {
                            fieldsModify.put(f.getName(), clsName);
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
            
            sb.append("@Component\n");
            sb.append("public class ").append(editedFileClassName).append(" extends AbstractDao<").append(Util.getClassNameShort(srcClassName)).append(",").append("Query").append(Util.getClassNameShort(srcClassName)).append("Model").append("> {").append("\n");
            sb.append("\n");
            sb.append("    @PersistenceContext\n");
            sb.append("    private EntityManager entityManager;\n");
            sb.append("\n");
            sb.append("    @Override\n");
            sb.append("    public QueryBuilderConfiguration<").append(Util.getClassNameShort(srcClassName)).append("> getConfiguration() {\n");
            sb.append("        return new QueryBuilderConfiguration (entityManager, ").append(Util.getClassNameShort(srcClassName)).append(".class);\n");
            sb.append("    }\n");
            sb.append("\n");
            sb.append("    public ").append(Util.getClassNameShort(srcClassName)).append(" create (final Create").append(Util.getClassNameShort(srcClassName)).append("Model model) {\n");
            if (hasPart) {
                sb.append("        final ").append(Util.getClassNameShort(srcClassName)).append(" entity = update (new ").append(Util.getClassNameShort(srcClassName)).append(" (), model.getPart ());\n");
            } else {
                sb.append("        final ").append(Util.getClassNameShort(srcClassName)).append(" entity = new ").append(Util.getClassNameShort(srcClassName)).append(" ();\n");
            }
            sb.append("\n");
            for (String k : fieldsCreate.keySet()) {
                sb.append("        entity.set").append(Util.toCamelCase(k)).append(" (model.get").append(Util.toCamelCase(k)).append("());").append("\n");
            }
            sb.append("\n");
            sb.append("        return entityManager.merge (entity);\n");
            sb.append("    }\n");
            sb.append("    public ").append(Util.getClassNameShort(srcClassName)).append(" update (final ").append(Util.getClassNameShort(srcClassName)).append(" entity, final Modify").append(Util.getClassNameShort(srcClassName)).append("Model model) {\n");
            for (String k : fieldsModify.keySet()) {
                sb.append("        use (model.get").append(Util.toCamelCase(k)).append("(), entity::set").append(Util.toCamelCase(k)).append(");\n");
            }
            sb.append("\n");
            sb.append("        return entity;\n");
            sb.append("    }\n");
            sb.append("\n");
            sb.append("    @Override\n");
            sb.append("    public void buildWhereQuery(WhereHelper wh, Query").append(Util.getClassNameShort(srcClassName)).append("Model model) {\n");
            sb.append("        // @formatter:off\n");
            sb.append("        wh\n");
            for (String k : fieldsCreate.keySet()) {
                sb.append("                .andEq(model.get").append(Util.toCamelCase(k)).append("(),   \"entity.").append(k).append("\",    \"entity").append(Util.toCamelCase(k)).append("\")\n");
            }
            for (String k : fieldsModify.keySet()) {
                sb.append("                .andEq(model.get").append(Util.toCamelCase(k)).append("(),   \"entity.").append(k).append("\",    \"entity").append(Util.toCamelCase(k)).append("\")\n");
            }
            sb.append("                ;\n");
            sb.append("        // @formatter:on\n");
            sb.append("    }\n");
            sb.append("\n");
            sb.append("}\n");
    }

    @Override
    protected int useField(Field f, String clsName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
