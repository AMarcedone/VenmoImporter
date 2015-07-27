/*
VenmoImporter for MoneyDance
Copyright (C) 2015  Antonio Marcedone

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package com.moneydance.modules.features.mdvenmoimporter;

import com.moneydance.apps.md.view.gui.MoneydanceGUI;
import com.moneydance.apps.md.view.gui.OnlineManager;
import com.infinitekind.moneydance.model.*;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

import java.io.*;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Vector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.border.*;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;


public class VenmoImporterWindow 
  extends JFrame
  implements ActionListener
{
  
  private static final int venmoDownloadTrabsactionLimit = 5000;
  private Main extension;
  
  private Boolean initialized;
  private String targetAcctId;
  private String venmoToken;
  private String descriptionFormat;
  
  
  private JPanel panel;
  private JPanel contentPane;
  
  private JTextField venmoTokenField;
  private JComboBox<Account> targetAccountCombo;  
  private JTextField descriptionFormatField;
  
  private JButton btnCancel;
  private JButton btnDelete;
  private JButton btnDownloadTransactions;
  private JButton btnSave;

  

  private static final String settingsKeyInitialized = ".initialized";
  private static final String settingsKeyVenmoToken = ".venmoToken";
  private static final String settingsKeyTargetAcctId = ".targetAcctId";
  private static final String settingsKeyDescriptionFormat = ".descriptionFormat";

  private static final String[] descriptionFields = {"@amount", "@action", "@actor", "@actor_username", "@target", "@target_username", "@note", "@date_created", "@date_completed", "@id"};
  private static final String descriptionFormatTooltip = "<html>The following placeholders can be used to describe a transaction: <br />"
  		+ "@amount - the amount of the transaction <br />"
  		+ "@action - can be 'pay' or 'charge' <br />"
  		+ "@actor - the account initiating the transaction (requesting or making a payment) <br />"
  		+ "@actor_username - the venmo username for @actor <br />"
  		+ "@target - the target account of the transaction (to which money are requested or paid) <br />"
  		+ "@target_username - the venmo username for @target <br />"
  		+ "@note - the description for the transaction <br />"
  		+ "@date_created - the date when the transaction was started <br />"
  		+ "@date_completed - the date when the transaction was settled <br />"
  		+ "@id - the venmo transaction id </html>" ;
  private static final String descriptionFormatDefault = "@note - @action from @actor to @target"; 
  
  private static final String venmoTokenTooltip = "To find your access token, log in to venmo and navigate to 'Account' and then 'developers'";
  

  public VenmoImporterWindow(Main extension) {
    super("VenmoImporter Console");
    this.extension = extension;
    
    loadSettings();
    
	setResizable(false);
	setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	setBounds(100, 100, 516, 176);
	contentPane = new JPanel();
	contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
	setContentPane(contentPane);
	contentPane.setLayout(new FormLayout(new ColumnSpec[] {
			FormSpecs.RELATED_GAP_COLSPEC,
			FormSpecs.DEFAULT_COLSPEC,
			FormSpecs.RELATED_GAP_COLSPEC,
			ColumnSpec.decode("default:grow"),
			FormSpecs.RELATED_GAP_COLSPEC,},
		new RowSpec[] {
			FormSpecs.RELATED_GAP_ROWSPEC,
			FormSpecs.DEFAULT_ROWSPEC,
			FormSpecs.RELATED_GAP_ROWSPEC,
			FormSpecs.DEFAULT_ROWSPEC,
			RowSpec.decode("8dlu"),
			FormSpecs.DEFAULT_ROWSPEC,
			FormSpecs.RELATED_GAP_ROWSPEC,
			FormSpecs.DEFAULT_ROWSPEC,
			FormSpecs.RELATED_GAP_ROWSPEC,}));
	
	JLabel lblVenmoToken = new JLabel("Venmo Token");
	lblVenmoToken.setHorizontalAlignment(SwingConstants.RIGHT);
	contentPane.add(lblVenmoToken, "2, 2");
    
	venmoTokenField = new JTextField(venmoToken);
	venmoTokenField.setToolTipText(venmoTokenTooltip);
	contentPane.add(venmoTokenField, "4, 2, fill, default");
	venmoTokenField.setColumns(50);
	    
	JLabel lblAccount = new JLabel("Account");
	contentPane.add(lblAccount, "2, 4, right, default");
	
    targetAccountCombo = new JComboBox<>(new Vector<>(extension.getUnprotectedContext().getRootAccount().getSubAccounts()));    
    contentPane.add(targetAccountCombo, "4, 4, fill, default");
    if(targetAcctId!= null) {
    	targetAccountCombo.setSelectedItem(extension.getUnprotectedContext().getCurrentAccountBook().getAccountByUUID(targetAcctId));
    }
	    
	JLabel lblDescriptionFormat = new JLabel("Memo template");
	contentPane.add(lblDescriptionFormat, "2, 6, right, default");
	
	descriptionFormatField = new JTextField(descriptionFormat == null ? descriptionFormatDefault : descriptionFormat);
	descriptionFormatField.setToolTipText(descriptionFormatTooltip);
	contentPane.add(descriptionFormatField, "4, 6, fill, default");
	descriptionFormatField.setColumns(10);
	
	panel = new JPanel();
	panel.setBorder(null);
	contentPane.add(panel, "2, 8, 3, 1, fill, fill");
	
	btnDelete = new JButton("Delete Settings");
	panel.add(btnDelete);
	
	btnCancel = new JButton("Cancel");
	panel.add(btnCancel);
	
	btnSave = new JButton("Save");
	panel.add(btnSave);
	
	btnDownloadTransactions = new JButton("Download Transactions");
	panel.add(btnDownloadTransactions);

	
	btnCancel.addActionListener(this);
	btnSave.addActionListener(this);
	btnDelete.addActionListener(this);
	btnDownloadTransactions.addActionListener(this);
    
    enableEvents(WindowEvent.WINDOW_CLOSING);
        
  }


//downloads the Venmo Transactions into the target account.
  public void downloadVenmoTransactions(){
  

   String venmoPositivecharge = "pay";
	
	
	String venmoUserId;

    AccountBook book = extension.getUnprotectedContext().getCurrentAccountBook();

    Account targetAcc = book.getAccountByUUID(targetAcctId); 
    if (targetAcc == null){
    	JOptionPane.showMessageDialog(this, "\n There was a problem with your account selection.");
    	return;
    	//Should never happen, as the combobox forces you to call the method after selecting a valid account id.
    }
    
    OnlineTxnList txnlist =  targetAcc.getDownloadedTxns(); 
    
	URL VenmoURL;
	try {
		
		VenmoURL = new URL("https://api.venmo.com/v1/me?access_token=" + venmoToken);

		BufferedReader in = new BufferedReader(new InputStreamReader(VenmoURL.openStream()));
        JSONObject tId;
    	
    	tId = new JSONObject(in.readLine());
    	if(tId.has("error")){
    		throw new Exception("The request to Venmo had the following error:\n\n" + tId.getJSONObject("error").getInt("code") + " " +  tId.getJSONObject("error").getString("message"));
    	}
    	venmoUserId =  tId.getJSONObject("data").getJSONObject("user").getString("id");
    	
		in.close();
		
		VenmoURL = new URL("https://api.venmo.com/v1/payments?limit="+venmoDownloadTrabsactionLimit+"&access_token=" + venmoToken);
		in = new BufferedReader(new InputStreamReader(VenmoURL.openStream()));
		
        JSONObject tr;
        	
    	tr = new JSONObject(in.readLine());
    	if(tr.has("error")){
    		throw new Exception("The request to Venmo had the following error:\n\n" + tr.getJSONObject("error").getInt("code") + " " +  tr.getJSONObject("error").getString("message"));
    	}
    	
    	double amount = 0;
    	String comment;

    	String remotetxnId;


    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		SimpleDateFormat output = new SimpleDateFormat("yyyyMMdd");    	
    	int date;
    	
    	OnlineTxn otrans;
    	
    	for(int i = 0; i< tr.getJSONArray("data").length(); i++ ){
    		JSONObject t = tr.getJSONArray("data").getJSONObject(i);
    		
    		if(!t.getString("status").equals("settled")) continue;
    		
    		if(t.getJSONObject("actor").getString("id").equals(venmoUserId)){
    			if(t.getString("action").equals(venmoPositivecharge)){
    				amount = - t.getDouble("amount");
    			}else{
    				amount = + t.getDouble("amount");
    			}
    		}else if(t.getJSONObject("target").getString("type").equals("user") && t.getJSONObject("target").getJSONObject("user").getString("id").equals(venmoUserId)){
    			if(t.getString("action").equals(venmoPositivecharge)){
    				amount = + t.getDouble("amount");
    			}else{
    				amount = - t.getDouble("amount");
    			}
    		}

    		//must be in the same order as descriptionFields
    		String[] descriptionValues = { Double.toString(t.getDouble("amount")),  t.getString("action"), t.getJSONObject("actor").getString("display_name"), t.getJSONObject("actor").getString("username"),  t.getJSONObject("target").getJSONObject("user").getString("display_name"),  t.getJSONObject("target").getJSONObject("user").getString("username"), t.getString("note"), t.getString("date_created") , t.getString("date_completed") , t.getString("note")};
    		//private static final String[] descriptionFields = {"@amount", "@action", "@actor", "@actor_username", "@target", "@target_username", "@note", "@date_created", "@date_completed", "@id"};
    		
    		comment = StringUtils.replaceEach(descriptionFormat, descriptionFields, descriptionValues);
    		    		
    		
    		date = Integer.parseInt(output.format(sdf.parse(t.getString("date_completed"))) );
    		
    		remotetxnId = t.getString("id");
    		
    		otrans = txnlist.newTxn();
    		
    		otrans.setAmount((long) (amount*100));
    		otrans.setMemo(comment);
    		otrans.setDatePostedInt(date);
    		otrans.setFITxnId(remotetxnId);
    		
    		txnlist.addNewTxn(otrans);
    	    		
        }

        in.close();

        
        } catch (NumberFormatException | JSONException | ParseException e) {
        	//Date format not recognized. Should never happen given the venmo api
      	  	JOptionPane.showMessageDialog(this, e.toString());
			e.printStackTrace();
		} catch (Exception e) {
      	  	JOptionPane.showMessageDialog(this, e.toString() + "\n\n See the moneydance console for additional details on the error.");
        	e.printStackTrace();
		}
		
		targetAcc.downloadedTxnsUpdated();
		
	    com.moneydance.apps.md.controller.Main mainApp =
                  (com.moneydance.apps.md.controller.Main) extension.getUnprotectedContext();
	    OnlineManager onlineMgr = new OnlineManager( (MoneydanceGUI) mainApp.getUI() );

		onlineMgr.processDownloadedTxns(targetAcc);
		
    
  
  }
  

  private void loadSettings() {
	  LocalStorage storage = extension.getUnprotectedContext().getCurrentAccountBook().getLocalStorage();
	  
	  if(storage.containsKey(extension.getName() + settingsKeyInitialized )){
		  initialized = true;
		  venmoToken = storage.getStr(extension.getName() + settingsKeyVenmoToken, "");
		  targetAcctId = storage.getStr(extension.getName() + settingsKeyTargetAcctId, "");		 
		  descriptionFormat = storage.getStr(extension.getName() + settingsKeyDescriptionFormat, "");		  
	  }else{
		  initialized = false;
		  venmoToken = null;
		  targetAcctId = null;
		  descriptionFormat = null;
	  }
  }

  private void saveSettings() {
	  LocalStorage storage = extension.getUnprotectedContext().getCurrentAccountBook().getLocalStorage();
	  
	  if(initialized == false) storage.put(extension.getName() + settingsKeyInitialized, true);

	  if( venmoToken == null ||  !venmoToken.equals(venmoTokenField.getText())){
		  venmoToken = venmoTokenField.getText();
		  storage.put(extension.getName() + settingsKeyVenmoToken, venmoToken);		  
	  }
	  if( targetAcctId == null || !targetAcctId.equals( ((Account) (targetAccountCombo.getSelectedItem()) ).getUUID()) ){
		  targetAcctId = ((Account) (targetAccountCombo.getSelectedItem()) ).getUUID();
		  storage.put(extension.getName() + settingsKeyTargetAcctId, targetAcctId);
	  }
	  if( descriptionFormat == null ||  !descriptionFormat.equals(descriptionFormatField.getText())){
		  descriptionFormat = descriptionFormatField.getText();
		  storage.put(extension.getName() + settingsKeyDescriptionFormat, descriptionFormat);		  
	  }

	
  }


  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==btnCancel) {
      extension.closeConsole();
    }else if(src==btnSave) {
  	  	saveSettings();
  	  	extension.closeConsole();
    }else if(src==btnDownloadTransactions) {
  	  	saveSettings();
  	  	downloadVenmoTransactions();
  	  	extension.closeConsole();
    }else if(src==btnDelete) {
  	  	deleteAllSettings();
  	  	extension.closeConsole();
    }
    
  }


  private void deleteAllSettings() {
	  LocalStorage storage = extension.getUnprotectedContext().getCurrentAccountBook().getLocalStorage();
	  
	  storage.entrySet().removeIf(e -> e.getKey().contains(extension.getName()));
  }


public final void processEvent(AWTEvent evt) {
    if(evt.getID()==WindowEvent.WINDOW_CLOSING) {
      extension.closeConsole();
      return;
    }
    if(evt.getID()==WindowEvent.WINDOW_OPENED) {
    }
    super.processEvent(evt);
  }



  void goAway() {
    setVisible(false);
    dispose();
  }
}
