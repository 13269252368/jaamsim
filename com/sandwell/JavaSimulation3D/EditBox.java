/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2005-2011 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.sandwell.JavaSimulation3D;

import com.sandwell.JavaSimulation.*;
import com.sandwell.JavaSimulation.Package;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

import javax.media.j3d.ColoringAttributes;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

import javax.swing.event.ChangeEvent;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.vecmath.Color3f;
import javax.vecmath.Vector3d;

/**
 * Class to display information about model objects. <br>
 * displays the results of getInfo for an object
 */
public class EditBox extends FrameBox {

	private static EditBox myInstance;  // only one instance allowed to be open
	private static final int ROW_HEIGHT=20;
	private static final int VALUE_COLUMN=2;

	private Entity currentEntity;
	private final JTabbedPane jTabbedPane;
	private final HelpKeyListener helpKeyListener;

	private int presentPage; // Present tabbed page

	private boolean buildingTable;	    // TRUE if the table is being populated

	private final CellRenderer cellRenderer;

	/**
	 * Widths of columns in the table of keywords as modified by the user in an edit
	 * session. When the EditBox is re-opened for the next edit (during the same session),
	 * the last modified column widths are used.
	 * <pre>
	 * userColWidth[ 0 ] = user modified width of column 0 (keywords column)
	 * userColWidth[ 1 ] = user modified width of column 1 (defaults units column)
	 * userColWidth[ 2 ] = user modified width of column 2 (values column)
	 */
	private int[] userColWidth = { 200, 100, 500 };

	private EditBox() {

		super( "Input Editor" );
		cellRenderer = new CellRenderer();
		helpKeyListener = new HelpKeyListener();

		setDefaultCloseOperation(FrameBox.HIDE_ON_CLOSE);

		buildingTable = false;

		// Set the preferred size of the panes
		jTabbedPane = new JTabbedPane();
		jTabbedPane.setBackground( INACTIVE_TAB_COLOR );
		jTabbedPane.setPreferredSize( new Dimension( 700, 400 ) );
		getContentPane().add(jTabbedPane);

		// Register a change listener
		jTabbedPane.addChangeListener(new ChangeListener() {

			// This method is called whenever the selected tab changes
			public void stateChanged(ChangeEvent evt) {
				JTabbedPane pane = (JTabbedPane)evt.getSource();

				// JTabbedPane hasn't built yet
				if(buildingTable || pane.getSelectedIndex() < 0)
					return;

				jTabbedPane.setBackgroundAt( presentPage, INACTIVE_TAB_COLOR );
				presentPage = jTabbedPane.getSelectedIndex();
				jTabbedPane.setBackgroundAt( presentPage, ACTIVE_TAB_COLOR );
				updateValues();
			}
		});

		JLabel helpLabel = new JLabel( "Press F1 for help on any cell", JLabel.CENTER );
		helpLabel.setFont(helpLabel.getFont().deriveFont(9f)); // smaller font size
		getContentPane().add("South", helpLabel);

		pack();
		setLocation(220, 710);
		setSize(1060, 290);
	}


	private static class HelpKeyListener implements KeyListener {

		public void keyReleased(KeyEvent e) {
			if (e.getKeyCode() != KeyEvent.VK_F1)
				return;

			JTable propTable = ((HTextField)e.getSource()).propTable;

			if (propTable.getSelectedRow() < 0)
				return;

			EditBox.getInstance().doHelp(
					propTable.getValueAt(propTable.getSelectedRow(), 0).toString()
					);
		}

		public void keyPressed(KeyEvent e) {}
		public void keyTyped(KeyEvent e) {}
	}

	protected void  doHelp(String keyword) {
		if (currentEntity == null) {
			Simulation.spawnHelp("", "");
			return;
		}

		// Determine the package of the entity
		Package cat = null;
		for( ObjectType type : ObjectType.getAll() ) {
			if( type.getJavaClass() == currentEntity.getClass() ) {
				cat = type.getPackage();
				break;
			}
		}


		if (cat != null &&  cat.getHelpSection() != null) {
			String helpName = "::" + cat.getHelpSection().trim() + "#mo_topic_" + keyword.trim();
			Simulation.spawnHelp(cat.getFilePrefix(), helpName);
		}
	}

	public synchronized static EditBox getInstance() {
		if (myInstance == null)
			myInstance = new EditBox();

		return myInstance;
	}

	// ========================================
	// HTextField is for the keyword/value grid
	// ----------------------------------------
	static class HTextField extends JTextField {
		public JTable propTable;

		public HTextField(JTable propTable) {
			super();
			this.propTable = propTable;
			propTable.clearSelection();
			this.addKeyListener(EditBox.getInstance().getHelpKeyListener());
			this.setBorder(null);
		}

		protected void processFocusEvent( FocusEvent fe ) {
			if ( fe.getID() == FocusEvent.FOCUS_GAINED ) {

				// select entire text string in the cell currently clicked on
				selectAll();
			}
			else if (fe.getID() == FocusEvent.FOCUS_LOST) {
				TableCellEditor tce = ((MyJTable)propTable).getCellEditor();

				// nothing to do
				if(tce == null)
					return;

				Component otherComp = fe.getOppositeComponent();

				// inside the JTable
				if(otherComp == propTable)
					return;

				// from a ColorCell to JTabbedPane
				if(otherComp == EditBox.getInstance().getJTabbedPane() ) {
					return;
				}

				// colorButton is pressed
				if(otherComp != null && (tce instanceof ColorEditor &&
					 ( this.getParent() == otherComp.getParent() )  ) ){
					return;
				}

				// apply the input modification after loosing the focus
				tce.stopCellEditing();

				// no bold keyword when input editor looses the focus
				propTable.clearSelection();
			}
			super.processFocusEvent( fe );
		}
	}

	/**
	 * Build a JTable with number of rows
	 *
	 * @param numberOfRows
	 * @return
	 */
	private JTable buildProbTable( int numberOfRows ) {

		MyJTable propTable;
		propTable = new MyJTable(numberOfRows, 3);

		propTable.setRowHeight( ROW_HEIGHT );
		propTable.setRowMargin( 1 );
		propTable.getColumnModel().setColumnMargin( 20 );
		propTable.setSelectionBackground( Color.WHITE );
		propTable.setSelectionForeground( Color.BLACK );
		propTable.setAutoResizeMode( JTable.AUTO_RESIZE_NEXT_COLUMN );
		propTable.setRowSelectionAllowed( false );

		// Set DefaultCellEditor that can process FOCUS events.
		propTable.setDefaultEditor( Object.class, new DefaultCellEditor(
				new HTextField(propTable ) ) );
		DefaultCellEditor dce = (DefaultCellEditor)propTable.getDefaultEditor(Object.class);

		// Set click behavior
		dce.setClickCountToStart(1);

		// Set keyword table column headers
		propTable.getColumnModel().getColumn( 0 ).setHeaderValue( "<html> <b> Keyword </b>" );
		propTable.getColumnModel().getColumn( 0 ).setPreferredWidth( userColWidth[ 0 ] );
		propTable.getColumnModel().getColumn( 1 ).setHeaderValue( "<html> <b> Default </b>" );
		propTable.getColumnModel().getColumn( 1 ).setPreferredWidth( userColWidth[ 1 ] );
		propTable.getColumnModel().getColumn( 2 ).setHeaderValue( "<html> <b> Value </b>" );
		propTable.getColumnModel().getColumn( 2 ).setPreferredWidth( userColWidth[ 2 ] );
		propTable.addKeyListener(helpKeyListener);

		// Listen for table changes
		propTable.getModel().addTableModelListener( new MyTableModelListener() );

		propTable.getColumnModel().getColumn( 0 ).setCellRenderer( cellRenderer ) ;

		return propTable;
	}

	Entity getCurrentEntity() {
		return currentEntity;
	}

	public JTabbedPane getJTabbedPane() {
		return jTabbedPane;
	}

	public void setEntity(Entity entity) {
		if(currentEntity == entity)
			return;

		if(entity != null && entity.testFlag(Entity.FLAG_GENERATED))
			entity = null;

		// tabbed pane has to be rebuilt
		if( currentEntity == null || entity == null ||
				currentEntity.getClass() != entity.getClass()) {

			buildingTable = true;
			presentPage = 0;
			jTabbedPane.removeAll();
			currentEntity = entity;

			// build all tabs and their prop tables
			if(currentEntity != null) {
				ArrayList<String> category = new ArrayList<String>();
				for( Input<?> in : currentEntity.getEditableInputs() ) {
					if ( category.contains(in.getCategory()) )
						continue;

					category.add( in.getCategory() );
					jTabbedPane.addTab(in.getCategory(), getScrollPaneFor(in.getCategory()));
				}
			}
			buildingTable = false;
		}
		currentEntity = entity;
		updateValues();
	}

	private JScrollPane getScrollPaneFor(String category) {

		// Count number of inputs for the present category
		int categoryCount = 0;
		for( Input<?> in : currentEntity.getEditableInputs() ) {
			if( in.getCategory().equals( category ) &&  ! in.isLocked() ) {
				categoryCount++;
			}
		}

		JTable propTable = this.buildProbTable( categoryCount  );
		propTable.getTableHeader().setBackground(HEADER_COLOR);

		int row = 0;

		// fill in keyword and default columns
		for( Input<?> in : currentEntity.getEditableInputs() ) {

			if( ! in.getCategory().equals(category) || in.isLocked() )
				continue;

			propTable.setValueAt( String.format("%s", in.getKeyword()), row, 0 );

			String defValString = EditBox.getDefaultValueStringOf(in);

			propTable.setValueAt( String.format("%s %s", defValString, in.getUnits()), row, 1 );
			row++;
		}

		JPanel jp = new JPanel();
		jp.setLayout(new BorderLayout(5,5));
		jp.add(propTable, BorderLayout.CENTER);
		JScrollPane jScrollPane = new JScrollPane(jp);
		jScrollPane.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
		jScrollPane.setColumnHeaderView( propTable.getTableHeader());
		return jScrollPane;
	}

	public void updateValues() {

		// table has not built yet
		if(buildingTable)
			return;

		// no entity is selected
		if(currentEntity == null) {
			setTitle("Input Editor");
			return;
		}

		buildingTable = true;

		setTitle( String.format("Input Editor - %s", currentEntity) );

		String currentCategory = jTabbedPane.getTitleAt(jTabbedPane.getSelectedIndex());
		JTable propTable = (JTable)((JPanel)((JScrollPane)jTabbedPane.getComponentAt(presentPage)).getViewport().getComponent(0)).getComponent(0);

		int row = 0;
		for( Input<?> in : currentEntity.getEditableInputs() ) {

			if( ! in.getCategory().equals(currentCategory) || in.isLocked() )
				continue;

			propTable.setValueAt( String.format("%s", in.getValueString()), row, 2 );
			row++;
		}

		buildingTable = false;
	}

	public void dispose() {
		myInstance = null;
		super.dispose();
	}

	public class CellRenderer extends DefaultTableCellRenderer {
		private final Font bold;
		private final Font plain;

		public CellRenderer() {
			bold  = this.getFont().deriveFont(Font.BOLD);
			plain = this.getFont().deriveFont(Font.PLAIN);
		}

		public Component getTableCellRendererComponent
		(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{

			Component cell = super.getTableCellRendererComponent
			(table, value, isSelected, hasFocus, row, column);

			if ( row == table.getSelectedRow() ) {
				cell.setFont( bold );
			}
			else {
				cell.setFont( plain ) ;
			}

			return cell;
		}
	}


	private static String getDefaultValueStringOf(Input<?> in) {
		String defValString = "{ }";
		Object defValue = in.getDefaultValue();
		if (defValue != null) {
			defValString = defValue.toString();
			if(defValue instanceof ArrayList<?>) {
				defValString = defValString.substring(1, defValString.length()-1);
			}
			else if(defValue instanceof ColoringAttributes) {
				Color3f color3f = new Color3f();
				((ColoringAttributes)defValue).getColor(color3f);
				defValString = color3f.toString();
			}
			else if(defValue instanceof Vector3d) {
				defValString = String.format( "%.3f %.3f %.3f", ((Vector3d)defValue).x, ((Vector3d)defValue).y, ((Vector3d)defValue).z );
			}
		}
		return defValString;
	}

	HelpKeyListener getHelpKeyListener() {
		return helpKeyListener;
	}

	class MyTableModelListener implements TableModelListener {

		/**
		 * Method for handling table edit events
		 */
		public void tableChanged( TableModelEvent e ) {

			// Do not worry about table changes brought on by building a new table
			if(buildingTable) {
				return;
			}

			// Find the data that has changed
			int row = e.getFirstRow();
			int col = e.getColumn();

			TableModel model = (TableModel)e.getSource();
			Object data = model.getValueAt( row, col );
			if ( model.getValueAt( row, 0 ) == null ) {
				return;
			}

			String currentKeyword = ((String)model.getValueAt( row, 0 )).trim();
			Input<?> in = currentEntity.getInput( currentKeyword );

			// The value has not changed
			if(in.getValueString().equals(model.getValueAt( row, 2 ))) {
				return;
			}

			try {

				String str = data.toString();

				if( str.isEmpty() ) {

					// Back to default value
					str = getDefaultValueStringOf(in);
				}
				InputAgent.processEntity_Keyword_Value(currentEntity, in, str);
			} catch (InputErrorException exep) {

				JOptionPane pane = new JOptionPane( String.format("%s; value will be cleared", exep.getMessage()),
						JOptionPane.ERROR_MESSAGE );
				JDialog errorBox = pane.createDialog( EditBox.this, "Input Error" );
				errorBox.setModal(true);
				errorBox.setVisible(true);
				FrameBox.valueUpdate();
				return;
			}
			GraphicsUpdateBehavior.forceUpdate = true;
		}
	}

	public static class MyJTable extends JTable {
		private DefaultCellEditor dropDownEditor;
		private ColorEditor colorEditor;

		public boolean isCellEditable( int row, int column ) {
			return ( column == VALUE_COLUMN ); // Only Value column is editable
		}

		public MyJTable(int column, int row) {
			super(column, row);
		}

		public TableCellEditor getCellEditor(int row, int col) {

			// Obtain the input for the keyword
			String currentKeyword = ((String)this.getValueAt( row, 0 )).trim();
			Input<?> in =
			   EditBox.getInstance().getCurrentEntity().getInput(currentKeyword);

			// 1) Multiple choice input
			if(in.getValidOptions() != null) {
				if(dropDownEditor == null) {
					JComboBox dropDown = new JComboBox();
					dropDown.setEditable(true);
					dropDown.getEditor().getEditorComponent().addKeyListener(
							EditBox.getInstance().getHelpKeyListener() );
					dropDownEditor = new DefaultCellEditor(dropDown);
				}

				// Refresh the content of the combo box
				JComboBox dropDown = (JComboBox) dropDownEditor.getComponent();
				DefaultComboBoxModel model = (DefaultComboBoxModel) dropDown.getModel();
				model.removeAllElements();
				ArrayList<String> array = in.getValidOptions();
				for(String each: array) {
					model.addElement(each);
				}
				return new DefaultCellEditor(dropDown);
			}

			// 2) Colour input
			if(in instanceof ColourInput) {
				if(colorEditor == null) {
					colorEditor = new ColorEditor(this);
				}
				return colorEditor;
			}

			// 3) Normal text
			return this.getDefaultEditor(Object.class);
		}
	}

	public static class ColorEditor extends AbstractCellEditor
	implements TableCellEditor, ActionListener {

		private final JPanel jPanel;
		private final HTextField text;
		private final JButton colorButton;
		private JColorChooser colorChooser;
		private JDialog dialog;
		private ColourInput in;
		public JTable propTable;

		public ColorEditor(JTable table) {
			propTable = table;
			jPanel = new JPanel(new BorderLayout());

			text = new HTextField(propTable);
			jPanel.add(text, BorderLayout.WEST);

			colorButton = new JButton(new ImageIcon(
				GUIFrame.class.getResource("/resources/images/dropdown.png")));
			colorButton.addActionListener(this);
			colorButton.setActionCommand("button");
			colorButton.setContentAreaFilled(false);
			jPanel.add(colorButton, BorderLayout.EAST);
		}

		public Object getCellEditorValue() {
			return text.getText();
		}

		public void actionPerformed(ActionEvent e) {
			if("button".equals(e.getActionCommand())) {
				if(colorChooser == null || dialog == null) {
					colorChooser = new JColorChooser();
					dialog = JColorChooser.createDialog(jPanel,
							"Pick a Color",
							true,  //modal
							colorChooser,
							this,  //OK button listener
							null); //no CANCEL button listener
					dialog.setIconImage(GUIFrame.getWindowIcon());
				}

				Color3f col = new Color3f();
				in.getValue().getColor(col);
				colorChooser.setColor(new Color(col.x, col.y, col.z));
				dialog.setVisible(true);

				// Apply editing
				stopCellEditing();

				// Focus the cell
				propTable.requestFocusInWindow();
			}
			else {
				Color color = colorChooser.getColor();
				text.setText( String.format("%d %d %d", color.getRed(),
						color.getGreen(), color.getBlue() ) );
			}
		}

		public Component getTableCellEditorComponent(JTable table,
				Object value, boolean isSelected, int row, int column) {

			// set the value
			String keyword = ((String)table.getValueAt( row, 0 )).trim();
			in = (ColourInput)
					EditBox.getInstance().getCurrentEntity().getInput(keyword);
			text.setText(
				((String)table.getValueAt( row, VALUE_COLUMN )).trim() );

			// right size for jPanel and its components
			Dimension dim = new Dimension(
				  table.getColumnModel().getColumn( VALUE_COLUMN ).getWidth() -
				  table.getColumnModel().getColumnMargin(),
				  table.getRowHeight());
			jPanel.setPreferredSize(dim);
			dim = new Dimension(dim.width - (dim.height), dim.height);
			text.setPreferredSize(dim);
			dim = new Dimension(dim.height, dim.height);
			colorButton.setPreferredSize(dim);

			return jPanel;
		}
	}
}
