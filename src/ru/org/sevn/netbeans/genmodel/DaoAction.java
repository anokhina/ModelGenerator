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
import java.util.Map;
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
    @ActionReference(path = "Menu/File", position = 1432)
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
    
    private Map<String, String> fillFields(final Class cls, final Map<String, String> fields) {
        if (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                if (!"part".equals(f.getName())) {
                    final String clsName = f.getType().getName().toString();
                    fields.put(f.getName(), clsName);
                }
            }
        }
        return fields;
    }
    
    private Map<String, Map<String, Object>> fillFieldsCodegen(final Class cls, final Map<String, Map<String, Object>> fields) {
        if (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                Map<String, Object> cg = CodegenUtil.getCodegen(App.instance().getCodeGenClassName(), f);
                if (cg != null && CodegenUtil.isSerchable(cg)) {
                    fields.put(f.getName(), cg);
                }
            }
        }
        return fields;
    }
    
    private String getClassName(final String name, final Class nameClass) {
        if (nameClass != null) {
            return name;
        }
        return "Object";
    }

    @Override
    protected void formatJavaModel(final StringBuilder sb, 
            final SourceGroup sg, 
            final String srcClassName, 
            final String editedFileClassNameFull,
            final String editedFileClassName) {

        final HashSet<String> usedClasses = makeUsedClasses();
        App.instance().fillUsedClassDao(usedClasses);
        usedClasses.add("javax.persistence.EntityManager");
        usedClasses.add("javax.persistence.PersistenceContext");
        usedClasses.add("org.springframework.stereotype.Component");

        usedClasses.add(srcClassName);
        
        final String packageName = Util.getClassNamePackage(editedFileClassNameFull);
        final String createModelFillName = packageName + ".Create" + Util.getClassNameShort(srcClassName) + "Model";
        final String modifyModelFillName = packageName + ".Modify" + Util.getClassNameShort(srcClassName) + "Model";
        final String queryModelFillName = packageName + ".AbstractQuery" + Util.getClassNameShort(srcClassName) + "Model";
        final Class createClass = Util.loadClassAny(createModelFillName, sg.getRootFolder());
        final Class modifyClass = Util.loadClassAny(modifyModelFillName, sg.getRootFolder());
        final Class queryClass = Util.loadClassAny(queryModelFillName, sg.getRootFolder());
        if (createClass == null) {
            sb.append("//GENERATION WARNING: not found " + createModelFillName + "\n");
        }
        if (modifyClass == null) {
            sb.append("//GENERATION WARNING: not found " + modifyModelFillName + "\n");
        }
        if (queryClass == null) {
            sb.append("//GENERATION WARNING: not found " + queryModelFillName + "\n");
        }
        
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
                            if (createClass == null) {
                                fieldsCreate.put(f.getName(), clsName);
                            }
                        } else if (useFieldCreate < 0 && !hasPart) {
                            hasPart = true;
                        }
                        if (useFieldModify > 0) {
                            if (modifyClass == null) {
                                fieldsModify.put(f.getName(), clsName);
                            }
                        }
                    }
                }
            }
            
            fillFields(createClass, fieldsCreate);
            fillFields(modifyClass, fieldsModify);
            
            usedClasses.remove(editedFileClassNameFull);
            if (editedFileClassName.startsWith("Abstract")) {
                usedClasses.remove("org.springframework.stereotype.Component");
            }
            for (Iterator<String> it = usedClasses.iterator(); it.hasNext();) {
                String cl = it.next();
                sb.append("import ").append(cl).append(";").append("\n");
            }
            sb.append("\n");
            
            final String queryClassName = "".concat("Query").concat(Util.getClassNameShort(srcClassName)).concat("Model");
            final Class queryClassNameClass = Util.loadClassAny(packageName + "." + queryClassName, sg.getRootFolder());
            
            if (editedFileClassName.startsWith("Abstract")) {
                sb.append("public abstract class ");
            } else {
                sb.append("@Component\n");
                sb.append("public class ");
            }
            sb.append(editedFileClassName).append(" extends AbstractDao<").append(Util.getClassNameShort(srcClassName)).append(",").append(getClassName(queryClassName, queryClassNameClass)).append("> {").append("\n");
            sb.append("\n");
            sb.append("    private QueryBuilderConfiguration configuration;\n");
            sb.append("\n");
            sb.append("    @Override\n");
            sb.append("    public QueryBuilderConfiguration<").append(Util.getClassNameShort(srcClassName)).append("> getConfiguration() {\n");
            sb.append("        return configuration;\n");
            sb.append("    }\n");
            sb.append("\n");
            sb.append("    @PersistenceContext\n");
            sb.append("    public void setEntityManager (EntityManager entityManager) {\n");
            sb.append("        configuration = new QueryBuilderConfiguration (entityManager, ").append(Util.getClassNameShort(srcClassName)).append(".class);\n");
            sb.append("    }\n");
            sb.append("\n");
            printCreate(sb, srcClassName, hasPart, fieldsCreate);
            printUpdate(sb, srcClassName, fieldsModify);
            printWhere(sb, srcClassName, queryClassName, queryClassNameClass, queryClass, fieldsCreate, fieldsModify);
            printJoins(sb, queryClassName, queryClassNameClass);
            sb.append("\n");
            sb.append("}\n");
    }
    
    private void printCreate(final StringBuilder sb, final String srcClassName, final boolean hasPart, final LinkedHashMap<String, String> fieldsCreate) {
        if (fieldsCreate.size() == 0) {
            sb.append("    public ").append(Util.getClassNameShort(srcClassName)).append(" create (final Modify").append(Util.getClassNameShort(srcClassName)).append("Model model) {\n");
            sb.append("        final ").append(Util.getClassNameShort(srcClassName)).append(" entity = update (new ").append(Util.getClassNameShort(srcClassName)).append(" (), model);\n");
            sb.append("\n");
            sb.append("        return getConfiguration().getEntityManager().merge (entity);\n");
            sb.append("    }\n");
        } else {
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
            sb.append("        return getConfiguration().getEntityManager().merge (entity);\n");
            sb.append("    }\n");
        }
    }
    
    private void printUpdate(final StringBuilder sb, final String srcClassName, final LinkedHashMap<String, String> fieldsModify) {
            sb.append("    public ").append(Util.getClassNameShort(srcClassName)).append(" update (final ").append(Util.getClassNameShort(srcClassName)).append(" entity, final Modify").append(Util.getClassNameShort(srcClassName)).append("Model model) {\n");
            for (String k : fieldsModify.keySet()) {
                sb.append("        use (model.get").append(Util.toCamelCase(k)).append("(), entity::set").append(Util.toCamelCase(k)).append(");\n");
            }
            sb.append("\n");
            sb.append("        return entity;\n");
            sb.append("    }\n");
    }
    /*
    String joinType() default "";
    String joinEntityOrField() default "";
    String joinAlias() default "";
    String joinOnExpression() default "";
    */
    private void printJoins(final StringBuilder sb, final String queryClassName, final Class queryClassNameClass) {
            sb.append("\n");
            sb.append("    @Override\n");
            sb.append("    public void buildJoins(final Joins joins, final ").append(getClassName(queryClassName, queryClassNameClass)).append(" model) {\n");
            sb.append("        // @formatter:off\n");
            if (queryClassNameClass != null) {
                final Map<String, Map<String, Object>> fields = fillFieldsCodegen(queryClassNameClass, new LinkedHashMap());
                if (fields.size() > 0) {
                    for (String k : fields.keySet()) {
                        final Map<String, Object> cg = fields.get(k);
                        final String joinType = ggg(cg.get("joinType"), "");
                        if (joinType.length() > 0) {
                            final String getterPrefix = ggg(cg.get("getterPrefix"), "get");
                            final String joinEntityOrField = ggg(cg.get("joinEntityOrField"), "");
                            final String joinAlias = ggg(cg.get("joinAlias"), "");
                            final String joinOnExpression = ggg(cg.get("joinOnExpression"), "");
                            if (getterPrefix.equals("is")) {
                                sb.append("        joins.addIf(model.").append(getterPrefix).append(Util.toCamelCase(k)).append(" (), //test\n");
                                //sb.append("        if ( model.").append(getterPrefix).append(Util.toCamelCase(k)).append(" () ) {\n");
                            } else {
                                sb.append("        joins.add(model.").append(getterPrefix).append(Util.toCamelCase(k)).append(" (), //test\n");
                                //sb.append("        if ( model.").append(getterPrefix).append(Util.toCamelCase(k)).append(" () != null ) {\n");
                            }
                            if (joinOnExpression.length() == 0) {
                                sb.append("                    () -> new JoinField (").append(joinType).append(", \"").append(joinEntityOrField).append("\", \"").append(joinAlias).append("\"), //create").append("\n");
                                sb.append("                    j -> {} //configure\n");
                                //sb.append("            joins.append (new JoinField (").append(joinType).append(", \"").append(joinEntityOrField).append("\", \"").append(joinAlias).append("\"));").append("\n");
                            } else {
                                sb.append("                    () -> new JoinEntity (").append(joinType).append(", \"").append(joinEntityOrField).append("\", \"").append(joinAlias).append("\"), //create").append("\n");
                                sb.append("                    j -> j.on ().append (\" ").append(joinOnExpression).append(" \") //configure\n");
                                //sb.append("            joins.append (new JoinEntity (").append(joinType).append(", \"").append(joinEntityOrField).append("\", \"").append(joinAlias).append("\"))").append("\n");
                                //sb.append("                .on ().append (\" ").append(joinOnExpression).append(" \");\n");
                            }
                            sb.append("        );\n");
                            //sb.append("        }\n");
                        }
                    }
                }
            }
            sb.append("        // @formatter:on\n");
            sb.append("    }\n");
    }
    private void printWhere(final StringBuilder sb, final String srcClassName, final String queryClassName, final Class queryClassNameClass, final Class queryClass, final LinkedHashMap<String, String> fieldsCreate, final LinkedHashMap<String, String> fieldsModify) {
            if (queryClassNameClass == null) {
                sb.append("//GENERATION WARNING: not found class: ").append(queryClassName).append("\n");
                
            }
            sb.append("\n");
            sb.append("    @Override\n");
            sb.append("    public void buildWhereQuery(final WhereHelper wh, final ").append(getClassName(queryClassName, queryClassNameClass)).append(" model) {\n");
            sb.append("        // @formatter:off\n");
            if (queryClass == null) {
                sb.append("//GENERATION WARNING: not found class: AbstractQuery").append(Util.getClassNameShort(srcClassName)).append("Model\n");
//                sb.append("        wh\n");
//                for (String k : fieldsCreate.keySet()) {
//                    sb.append("                .andEq(model.get").append(Util.toCamelCase(k)).append("(),   \"entity.").append(k).append("\",    \"entity").append(Util.toCamelCase(k)).append("\")\n");
//                }
//                for (String k : fieldsModify.keySet()) {
//                    sb.append("                .andEq(model.get").append(Util.toCamelCase(k)).append("(),   \"entity.").append(k).append("\",    \"entity").append(Util.toCamelCase(k)).append("\")\n");
//                }
//                sb.append("                ;\n");
            } else {
                final Map<String, String> fields = fillFields(queryClass, new LinkedHashMap());
                if (fields.size() > 0) {
                    sb.append("        wh\n");
                    for (String k : fields.keySet()) {
                        sb.append("                .andEq(model.get").append(Util.toCamelCase(k)).append("(),   \"entity.").append(k).append("\",    \"entity").append(Util.toCamelCase(k)).append("\")\n");
                    }
                    sb.append("                ;\n");
                }
            }
    /*
    String getterPrefix () default "get";
    String searchFieldName () default "";
    String operation () default "";
    String queryExpression () default "";    
    */
            if (queryClassNameClass != null) {
                final Map<String, Map<String, Object>> fields = fillFieldsCodegen(queryClassNameClass, new LinkedHashMap());
                if (fields.size() > 0) {
                    sb.append("        wh\n");
                    for (String k : fields.keySet()) {
                        final Map<String, Object> cg = fields.get(k);
                        final String joinType = ggg(cg.get("joinType"), "");
                        if (joinType.length() > 0) continue;
                        
                        final String entityFieldName = ggg(cg.get("searchFieldName"), "entity." + k);
                        final String paramName = "entity" + Util.toCamelCase(k);
                        final String operation = ggg(cg.get("operation"), "andEq");
                        final String expression = ggg(cg.get("queryExpression"), "");
                        final String getterPrefix = ggg(cg.get("getterPrefix"), "get");
                        
                        if (expression == null || expression.length() == 0) {
                            if (paramName == null || paramName.length() == 0) {
                                sb.append("                .").append(operation).append("(model.").append(getterPrefix).append(Util.toCamelCase(k)).append("(),   \"").append(entityFieldName).append("\")\n");
                            } else {
                                sb.append("                .").append(operation).append("(model.").append(getterPrefix).append(Util.toCamelCase(k)).append("(),   \"").append(entityFieldName).append("\",    \"").append(paramName).append("\")\n");
                            }
                        } else {
                            sb.append("                .").append(operation).append("(model.").append(getterPrefix).append(Util.toCamelCase(k)).append("(),   \" ").append(expression).append(" \")\n");
                        }
                    }
                    sb.append("                ;\n");
                }
            }
            sb.append("        // @formatter:on\n");
            sb.append("    }\n");
    }
    
    private static String ggg(final Object v, final String defval) {
        if (v == null) {
            return defval;
        }
        final String ret = v.toString().trim();
        if (ret.length() == 0) {
            return defval;
        }
        return ret;
    }

    @Override
    protected int useField(Field f, String clsName, final String editedFileClassName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
