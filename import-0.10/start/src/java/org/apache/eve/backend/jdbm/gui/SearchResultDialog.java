/*
 * $Id: SearchResultDialog.java,v 1.2 2003/03/13 18:27:24 akarasulu Exp $
 *
 * -- (c) LDAPd Group                                                    --
 * -- Please refer to the LICENSE.txt file in the root directory of      --
 * -- any LDAPd project for copyright and distribution information.      --
 *
 */

package org.apache.eve.backend.jdbm.gui ;

import javax.swing.JDialog ;
import java.awt.Frame ;
import java.awt.event.WindowEvent ;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.tree.TreeModel;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JTable;
import javax.swing.table.TableModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.ListSelectionModel;
import java.math.BigInteger;


public class SearchResultDialog
    extends JDialog implements ListSelectionListener
{
    private JPanel jPanel1 = new JPanel();
    private JTree jTree1 = new JTree();
    private JPanel jPanel2 = new JPanel();
    private JPanel jPanel3 = new JPanel();
    private JTextArea jTextArea1 = new JTextArea();
    private JScrollPane jScrollPane1 = new JScrollPane();
    private JButton jButton1 = new JButton();
    private JPanel jPanel4 = new JPanel();
    private JScrollPane jScrollPane2 = new JScrollPane();
    private JTable m_resultsTbl = new JTable();

    /** Creates new form JDialog */
    public SearchResultDialog(Frame parent, boolean modal) {
        super(parent, modal);
        initGUI();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     */
    private void initGUI() {
        addWindowListener(
            new java.awt.event.WindowAdapter() {
                public void windowClosing(java.awt.event.WindowEvent evt) {
                    closeDialog(evt);
                }
            });
        pack();
        getContentPane().setLayout(new java.awt.GridBagLayout());
        getContentPane().add(jPanel1,
        new java.awt.GridBagConstraints(0, 0, 1, 1, 1.0, 0.1, java.awt.GridBagConstraints.NORTH, java.awt.GridBagConstraints.BOTH,
        new java.awt.Insets(10, 5, 5, 5), 0, 0));
        getContentPane().add(jPanel2,
        new java.awt.GridBagConstraints(0, 1, 1, 1, 1.0, 0.4, java.awt.GridBagConstraints.CENTER, java.awt.GridBagConstraints.BOTH,
        new java.awt.Insets(5, 5, 5, 5), 0, 0));
        getContentPane().add(jPanel3,
        new java.awt.GridBagConstraints(0, 3, 1, 1, 1.0, 0.1, java.awt.GridBagConstraints.SOUTH, java.awt.GridBagConstraints.BOTH,
        new java.awt.Insets(0, 0, 0, 0), 0, 0));
        getContentPane().add(jPanel4,
        new java.awt.GridBagConstraints(0, 2, 1, 1, 1.0, 0.4, java.awt.GridBagConstraints.CENTER, java.awt.GridBagConstraints.BOTH,
        new java.awt.Insets(5, 5, 5, 5), 0, 0));
        jPanel1.setLayout(new java.awt.BorderLayout(10, 10));
        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(
        new java.awt.Color(153, 153, 153), 1), "Specifications", javax.swing.border.TitledBorder.LEADING, javax.swing.border.TitledBorder.TOP,
        new java.awt.Font("SansSerif", 0, 14), new java.awt.Color(60, 60, 60)));
        jPanel1.add(jTextArea1, java.awt.BorderLayout.CENTER);
        jScrollPane1.getViewport().add(jTree1);
        jTree1.setBounds(new java.awt.Rectangle(238,142,82,80));
        jTextArea1.setText("");
        jTextArea1.setEditable(false);
        setBounds(new java.awt.Rectangle(0, 0, 485, 434));
        setTitle("Search Results");
        jPanel2.setLayout(new java.awt.BorderLayout());
        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(
        new java.awt.Color(153, 153, 153), 1),
        "Filter Expression Tree", javax.swing.border.TitledBorder.LEADING, javax.swing.border.TitledBorder.TOP,
        new java.awt.Font("SansSerif", 0, 14), new java.awt.Color(60, 60, 60)));
        jPanel2.add(jScrollPane1, java.awt.BorderLayout.CENTER);
        jButton1.setText("Done");
        jButton1.setActionCommand("Done");
		jButton1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent a_event) {
                SearchResultDialog.this.setVisible(false) ;
				SearchResultDialog.this.dispose() ;
            }
        }) ;
        jButton1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jButton1.setAlignmentX(0.5f);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jPanel3.setPreferredSize(new java.awt.Dimension(79, 41));
        jPanel3.setMinimumSize(new java.awt.Dimension(79, 41));
        jPanel3.setSize(new java.awt.Dimension(471,35));
        jPanel3.setToolTipText("");
        jPanel3.add(jButton1);
        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(
        new java.awt.Color(153, 153, 153), 1), "Search Results", javax.swing.border.TitledBorder.LEADING, javax.swing.border.TitledBorder.TOP,
        new java.awt.Font("SansSerif", 0, 14), new java.awt.Color(60, 60, 60)));
        jPanel4.setLayout(new java.awt.BorderLayout());
        jPanel4.add(jScrollPane2, java.awt.BorderLayout.CENTER);
        jScrollPane2.getViewport().add(m_resultsTbl);
        m_resultsTbl.setSize(new java.awt.Dimension(450,10));
        m_resultsTbl.getSelectionModel().addListSelectionListener(this) ;
    }


    public void valueChanged(ListSelectionEvent an_event)
    {
        ListSelectionModel l_selectionModel = (ListSelectionModel) an_event.getSource() ;
        int l_minIndex = l_selectionModel.getMinSelectionIndex() ;
        int l_maxIndex = l_selectionModel.getMaxSelectionIndex() ;

        for(int ii = l_minIndex ; ii <= l_maxIndex; ii++) {
            if(l_selectionModel.isSelectedIndex(ii) && !an_event.getValueIsAdjusting()) {
                BigInteger l_id = (BigInteger)
                    m_resultsTbl.getModel().getValueAt(ii, 0) ;
                ((BackendFrame) getParent()).selectTreeNode(l_id) ;
            }
        }
    }


    /** Closes the dialog */
    private void closeDialog(WindowEvent evt) {
        setVisible(false);
        dispose();
    }


    public void setTreeModel(TreeModel a_model)
    {
        this.jTree1.setModel(a_model) ;
    }


    public void setFilter(String a_filter)
    {
        this.jTextArea1.setText(a_filter) ;
    }


    public void setTableModel(TableModel a_model)
    {
        m_resultsTbl.setModel(a_model) ;
    }
}
