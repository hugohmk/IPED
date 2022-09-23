/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.utils.LocalizedFormat;
import javax.swing.Icon;
import javax.swing.UIManager;
import iped.engine.localization.CategoryLocalization;

public class CategoryTreeCellRenderer extends DefaultTreeCellRenderer {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {

        DefaultTreeCellRenderer result = (DefaultTreeCellRenderer) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        
		String blankIconString = "blank";
		
        String category = value != null ? value.toString() : "";
        category = category.replaceAll("\\(\\d*\\)", "").trim();
        category = getNonLocalizedCategory(category);
        category = category != null ? category : "";
        
        if (!category.isEmpty() && IconLoader.extesionIconMap.containsKey(category.toLowerCase())){
            result.setIcon(IconLoader.extesionIconMap.get(category.toLowerCase()));
        }else {
			result.setIcon(IconLoader.extesionIconMap.get(blankIconString.toLowerCase()));
		}

        return result;
    }

    private String getNonLocalizedCategory(String category) {
        return CategoryLocalization.getInstance().getNonLocalizedCategory(category);
    }

}