/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2011 Ausenco Engineering Canada Inc.
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.jaamsim.DisplayModels.ColladaModel;
import com.jaamsim.DisplayModels.DisplayModel;
import com.jaamsim.controllers.RenderManager;
import com.jaamsim.input.InputAgent;
import com.jaamsim.math.Vec3d;
import com.jaamsim.math.Vec4d;
import com.jaamsim.render.Future;
import com.jaamsim.render.RenderUtils;
import com.jaamsim.ui.FrameBox;
import com.sandwell.JavaSimulation.Simulation;
import com.sandwell.JavaSimulation.Vector;

public class GraphicBox extends JDialog {
	private static GraphicBox myInstance;  // only one instance allowed to be open
	private final  JLabel previewLabel; // preview DisplayModel as a picture
	final ImageIcon previewIcon = new ImageIcon();
	private static DisplayEntity currentEntity;
	private final static JList displayModelList; // All defined DisplayModels


	private final JCheckBox useModelSize;
	private final JCheckBox enabledCulling;
	static {
		displayModelList = new JList();
	}

	private GraphicBox() {

		setTitle( "Select DisplayModel" );
		setIconImage(GUIFrame.getWindowIcon());

		this.setModal(true);

		// Upon closing do the close method
		setDefaultCloseOperation( FrameBox.DO_NOTHING_ON_CLOSE );
		WindowListener windowListener = new WindowAdapter() {
			@Override
			public void windowClosing( WindowEvent e ) {
				myInstance.close();
			}
		};
		this.addWindowListener( windowListener );

		previewLabel = new JLabel("", JLabel.CENTER);
		getContentPane().add(previewLabel, "West");

		JScrollPane scrollPane = new JScrollPane(displayModelList);
		scrollPane.setBorder(new EmptyBorder(5, 0, 44, 10));
		getContentPane().add(scrollPane, "East");

		// Update the previewLabel according to the selected DisplayModel
		displayModelList.addListSelectionListener(   new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {

				// do this unconditionally to force a repaint and allow for quick outs
				previewLabel.setIcon(null);

				// Avoid null pointer exception when the list is being re-populated
				if(displayModelList.getSelectedIndex() == -1)
					return;

				// Selected DisplayModel
				DisplayModel dm = (DisplayModel) ((JList)e.getSource()).getSelectedValue();

				if (!RenderManager.isGood()) { return; }

				Future<BufferedImage> fi = RenderManager.inst().getPreviewForDisplayModel(dm, null);
				fi.blockUntilDone();

				if (fi.failed()) {
					return; // Something went wrong...
				}

				BufferedImage image = RenderUtils.scaleToRes(fi.get(), 180, 180);

				if(image == null) {
					return;
				}

				previewIcon.setImage(image);
				previewLabel.setIcon(previewIcon);
			}
		} );

		// Import and Accept buttons
		JButton importButton = new JButton("Import");
		importButton.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {

				if (!RenderManager.isGood()) {
					return;
				}

				StringBuilder validString = new StringBuilder(45);
				validString.append("*.dae;");
				validString.append("*.jsm;");
				validString.append("*.zip;");
				validString.append("*.kmz;");
				validString.append("*.bmp;");
				validString.append("*.jpg;");
				validString.append("*.png;");
				validString.append("*.pcx;");
				validString.append("*.gif");

				FileDialog chooser = new FileDialog(myInstance, "New DisplayModel", FileDialog.LOAD );
				chooser.setFile(validString.toString());
				chooser.setVisible(true);

				// A file has not been selected
				if( chooser.getFile() == null )
					return;

				String fileName = chooser.getFile();
				String chosenFileName = chooser.getDirectory() + fileName;
				chosenFileName = chosenFileName.trim();

				int to = fileName.contains(".") ? fileName.indexOf(".") : fileName.length()-1;
				String entityName = fileName.substring(0, to); // File name without the extension
				entityName = entityName.replaceAll(" ", ""); // Space is not allowed for Entity Name

				DisplayModel newModel = InputAgent.defineEntityWithUniqueName(ColladaModel.class, entityName, true);

				Vector data = new Vector(1);
				data.addElement(entityName);
				data.addElement("ColladaFile");
				data.addElement(chosenFileName);

				InputAgent.processData(newModel, data);
				myInstance.refresh(); // Add the new DisplayModel to the List
				FrameBox.valueUpdate();

				// Scroll to selection and ensure it is visible
				int index = displayModelList.getModel().getSize() - 1;
				displayModelList.setSelectedIndex(index);
				displayModelList.ensureIndexIsVisible(index);
			}
		} );

		JButton acceptButton = new JButton("Accept");
		acceptButton.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				setEnabled(false); // Don't accept any interaction
				DisplayModel dm = (DisplayModel) displayModelList.getSelectedValue();

				Vector data = new Vector(3);
				data.addElement(currentEntity.getInputName());
				data.addElement("DisplayModel");
				data.addElement(dm.getName());
				InputAgent.processData(currentEntity, data);

				if (!RenderManager.isGood()) {
					myInstance.close();
				}

				Vec4d modelSize = Vec4d.ONES;
				if (dm instanceof ColladaModel) {
					ColladaModel dmc = (ColladaModel)dm;
					modelSize = RenderManager.inst().getMeshSize(dmc.getColladaFile());
				}

				Vec3d entitySize = currentEntity.getSize();
				double longestSide = modelSize.x;
				//double ratio = dm.getConversionFactorToMeters();
				double ratio = 1;
				if(! useModelSize.isSelected()) {
					ratio = entitySize.x/modelSize.x;
					if(modelSize.y > longestSide) {
						ratio = entitySize.y/modelSize.y;
						longestSide = modelSize.y;
					}
					if(modelSize.z > longestSide) {
						ratio = entitySize.z/modelSize.z;
					}
				}
				entitySize = new Vec3d(modelSize.x*ratio, modelSize.y*ratio, modelSize.z*ratio);
				InputAgent.processEntity_Keyword_Value(currentEntity, "Size", String.format("%.6f %.6f %.6f m", entitySize.x, entitySize.y, entitySize.z));
				FrameBox.valueUpdate();
				myInstance.close();
			}
		} );
		useModelSize = new JCheckBox("Use Display Model Size");
		useModelSize.setSelected(true);
		enabledCulling = new JCheckBox("Enable Culling");
		enabledCulling.setSelected(true);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout( new FlowLayout(FlowLayout.RIGHT) );
		buttonPanel.add(useModelSize);
		buttonPanel.add(enabledCulling);
		buttonPanel.add(importButton);
		buttonPanel.add(acceptButton);
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		this.pack();
	}

	public static GraphicBox getInstance( DisplayEntity ent, int x, int y ) {
		currentEntity = ent;

		Point pos = new Point(x, y);

		// Has the Graphic Box been created?
		if (myInstance == null) {
			myInstance = new GraphicBox();
			myInstance.setMinimumSize(new Dimension(400, 300));
		}

		// Position of the GraphicBox
		pos.setLocation(pos.x + myInstance.getSize().width /2 , pos.y + myInstance.getSize().height /2);
		myInstance.setLocation(pos.x, pos.y);
		myInstance.refresh();
		myInstance.setEnabled(true);
		return myInstance;
	}

	private void close() {
		currentEntity = null;
		setVisible(false);
	}

	@Override
	public void dispose() {
		super.dispose();
		myInstance = null;
	}

	private void refresh() {
		DisplayModel entDisplayModel = currentEntity.getDisplayModelList().get(0);

		ArrayList<DisplayModel> models = Simulation.getClonesOf(DisplayModel.class);
		// Populate JList with all the DisplayModels
		DisplayModel[] displayModels = new DisplayModel[models.size()];
		int index = 0;
		int i = 0;
		for (DisplayModel each : models) {
			if(entDisplayModel == each) {
				index = i;
			}
			displayModels[i++] = each;
		}
		displayModelList.setListData(displayModels);
		displayModelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		displayModelList.setSelectedIndex(index);
		displayModelList.ensureIndexIsVisible(index);
	}
}
