package org.zaproxy.zap.extension.saml.ui;

import org.parosproxy.paros.view.View;
import org.zaproxy.zap.extension.saml.Attribute;
import org.zaproxy.zap.extension.saml.PassiveAttributeChangeListener;
import org.zaproxy.zap.extension.saml.SAMLConfiguration;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AddAutoChangeAttributeUI extends JDialog {

    private JComboBox<Attribute> comboBoxAttribSelect;
    private JTextField txtAttribValues;

    /**
     * Create the dialog.
     */
    public AddAutoChangeAttributeUI(final PassiveAttributeChangeListener listener) {
        setTitle("Add New Attribute");
        setBounds(100, 100, 450, 250);
        getContentPane().setLayout(new BorderLayout());
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        contentPanel.setLayout(new BorderLayout(0, 0));
        {
            JPanel attribNamePanel = new JPanel();
            FlowLayout flowLayout = (FlowLayout) attribNamePanel.getLayout();
            flowLayout.setHgap(0);
            flowLayout.setAlignment(FlowLayout.LEFT);
            contentPanel.add(attribNamePanel, BorderLayout.NORTH);
            {
                attribNamePanel.setBorder(BorderFactory.createTitledBorder("Attribute Name"));
                comboBoxAttribSelect = new JComboBox<>();
                for (Attribute attribute : SAMLConfiguration.getInstance().getAvailableAttributes()) {
                    if (!listener.getDesiredAttributes().contains(attribute)) {
                        comboBoxAttribSelect.addItem(attribute.createCopy());
                    }
                }
                comboBoxAttribSelect.setMaximumRowCount(5);
                attribNamePanel.add(comboBoxAttribSelect);
            }
        }
        {
            JPanel attribValuesPanel = new JPanel();
            contentPanel.add(attribValuesPanel, BorderLayout.CENTER);
            attribValuesPanel.setLayout(new BorderLayout(5, 5));
            {
                JLabel lblAttributeValues = new JLabel("Attribute Values");
                lblAttributeValues.setHorizontalAlignment(SwingConstants.LEFT);
                attribValuesPanel.add(lblAttributeValues, BorderLayout.NORTH);
            }
            {
                {
                    txtAttribValues = new JTextField();
                    attribValuesPanel.add(txtAttribValues, BorderLayout.CENTER);
                }
            }
        }
        {
            final JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
            getContentPane().add(buttonPane, BorderLayout.SOUTH);
            {
                final JButton okButton = new JButton("OK");
                okButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (comboBoxAttribSelect.getSelectedItem() == null) {
                            View.getSingleton().showWarningDialog("No Attribute selected, " +
                                    "please select one from combo box");
                            return;
                        }
                        if (txtAttribValues.getText().equals("")) {
                            JOptionPane.showMessageDialog(AddAutoChangeAttributeUI.this, "No values given, " +
                                    "Please provide a non-empty value", "Error in value", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        Attribute attribute = ((Attribute) comboBoxAttribSelect.getSelectedItem());
                        attribute.setValue(txtAttribValues.getText());
                        listener.onDesiredAttributeValueChange(attribute);
                        AddAutoChangeAttributeUI.this.setVisible(false);
                    }
                });
                buttonPane.add(okButton);
                getRootPane().setDefaultButton(okButton);
            }
            {
                JButton cancelButton = new JButton("Cancel");
                cancelButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AddAutoChangeAttributeUI.this.setVisible(false);
                    }
                });
                buttonPane.add(cancelButton);
            }
        }
    }

    /**
     * Get the combo box object to update its contents
     *
     * @return
     */
    public JComboBox<Attribute> getComboBoxAttribSelect() {
        return comboBoxAttribSelect;
    }

    /**
     * Get the value textfield to update its content
     *
     * @return
     */
    public JTextField getTxtAttribValues() {
        return txtAttribValues;
    }


}
