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

import java.lang.reflect.Field;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "File",
        id = "ru.org.sevn.netbeans.genmodel.QueryModelAction"
)
@ActionRegistration(
        iconBase = "ru/org/sevn/netbeans/genmodel/query.png",
        displayName = "#CTL_QueryModelAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 1431)
    ,
  @ActionReference(path = "Toolbars/File", position = 500)
})
@Messages("CTL_QueryModelAction=Create Query Model")
public final class QueryModelAction extends BaseAction {

    @Override
    protected int useField(Field f, final String clsName, final String editedFileClassName) {
        if (!excludedTypes.contains(clsName)) {
            if (isAbstract(editedFileClassName)) {
                if (CodegenUtil.isSerchable(f)) {
                    return 1;
                }
            }
        }
        return 0;
    }
    
    @Override
    protected boolean hasConstructor(final String editedFileClassName) {
        if (editedFileClassName.startsWith("Basic")) {
            return true;
        }
        if (isAbstract(editedFileClassName)) {
            return false;
        } 
        return true;
    }
    
    protected String getExtends (final String editedFileClassName) {
        if (!isAbstract(editedFileClassName)) {
            return " extends Abstract" + editedFileClassName;
        }
        return "";
    }
    
    protected boolean isAbstract(final String editedFileClassName) {
        return editedFileClassName.startsWith("Abstract") || editedFileClassName.endsWith("Service") || editedFileClassName.startsWith("Basic") ;
    }
    
    @Override
    protected boolean hasPart(final boolean hasPart) {
        return false;
    }
    
    @Override
    protected String getModelPrefix() {
        return "Query";
    }
}
