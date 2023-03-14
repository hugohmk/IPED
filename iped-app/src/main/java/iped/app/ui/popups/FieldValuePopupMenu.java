package iped.app.ui.popups;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.TableHeaderFilterManager;
import iped.data.IItemId;
import iped.engine.task.index.IndexItem;
import iped.utils.DateUtil;

public class FieldValuePopupMenu extends JPopupMenu implements ActionListener{
    JMenuItem filterLessThan;
    JMenuItem filterGreaterThan;
    IItemId itemId;
    String field;
    String value;
    boolean isDate = false;
    private TableHeaderFilterManager fm;
    
    private SimpleDateFormat df = new SimpleDateFormat(Messages.getString("ResultTableModel.DateFormat")); //$NON-NLS-1$
    private JMenuItem filterContains;
    private JMenuItem filterNotContains;
    private JMenuItem filterEmpty;
    private JMenuItem filterNonEmpty;
    private JButton btValue;

    public FieldValuePopupMenu(IItemId itemId, String field, String value) {
        super();

        fm = TableHeaderFilterManager.get();

        this.itemId = itemId;
        this.field=field;
        this.value = value;

        if(value!=null && value!="") {
            JPanel valuePanel = new JPanel();
            valuePanel.setBorder(BorderFactory.createEmptyBorder());
            JLabel lbValue = new JLabel(value);
            btValue = new JButton();
            btValue.setBorder(BorderFactory.createEmptyBorder());
            URL imageUrl = App.class.getResource("copy.png");
            ImageIcon imageIcon = new ImageIcon(imageUrl);
            Image image = imageIcon.getImage(); // transform it 
            Image newimg = image.getScaledInstance(16, 16,  java.awt.Image.SCALE_SMOOTH); // scale it the smooth way  
            imageIcon = new ImageIcon(newimg);  // transform it back
            btValue.setIcon(imageIcon);
            btValue.addActionListener(this);
            btValue.setMaximumSize(new Dimension(16,16));
            valuePanel.add(lbValue);
            valuePanel.add(btValue);
            this.add(valuePanel);
        }
        
        this.add(new JSeparator());
        if(value.length()>0) {
            if(IndexItem.isNumeric(field)) {
                filterLessThan=new JMenuItem("Filter less than...");
                filterLessThan.addActionListener(this);
                this.add(filterLessThan);

                filterGreaterThan=new JMenuItem("Filter greater than...");
                filterGreaterThan.addActionListener(this);
                this.add(filterGreaterThan);
            }else if(isDate=isDate(value)){
                filterLessThan=new JMenuItem("Before ...");
                filterLessThan.addActionListener(this);
                this.add(filterLessThan);

                filterGreaterThan=new JMenuItem("After ...");
                filterGreaterThan.addActionListener(this);
                this.add(filterGreaterThan);
            }else {
                filterContains=new JMenuItem("Contains ...");
                filterContains.addActionListener(this);
                this.add(filterContains);

                filterNotContains=new JMenuItem("Not Contains ...");
                filterNotContains.addActionListener(this);
                this.add(filterNotContains);
            }
        }
        filterEmpty=new JMenuItem("Empty...");
        filterEmpty.addActionListener(this);
        this.add(filterEmpty);

        filterNonEmpty=new JMenuItem("Non empty...");
        filterNonEmpty.addActionListener(this);
        this.add(filterNonEmpty);
    }
    
    private boolean isDate(String value) {
        try {
            df.parse(value);
            return true;
        }catch (Exception e) {
        }
        return false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==filterContains) {
            fm.addFilter(field, field+":\""+value+"\"");
        }
        if(e.getSource()==filterNotContains) {
            fm.addFilter(field, "-"+field+":\""+value+"\"");
        }
        if(e.getSource()==filterEmpty) {
            fm.addEmptyFilter(field);
        }
        if(e.getSource()==filterNonEmpty) {
            fm.addNonEmptyFilter(field);
        }
        if(isDate) {
            try {
                Date d = df.parse(value);
                value = DateUtil.dateToString(d);
            } catch (ParseException e1) {
                e1.printStackTrace();
            }
        }
        if(e.getSource()==filterLessThan) {
            fm.addFilter(field, field+":[* TO "+value+"]");
        }
        if(e.getSource()==filterGreaterThan) {
            fm.addFilter(field, field+":["+value+" TO *]");
        }
        
        if(e.getSource()==btValue) {
            StringSelection stringSelection = new StringSelection(value);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            this.setVisible(false);
        }
        
        App.get().getAppListener().updateFileListing();
        App.get().setDockablesColors();
    }

}