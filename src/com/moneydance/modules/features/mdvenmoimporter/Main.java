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

import com.moneydance.apps.md.controller.FeatureModule;
import com.moneydance.apps.md.controller.FeatureModuleContext;

public class Main
  extends FeatureModule
{
  private VenmoImporterWindow venmoImporterWindow = null;

  public void init() {
    FeatureModuleContext context = getContext();
    try {
      context.registerFeature(this, "showconsole",
        null,
        getName());
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }

  public void cleanup() {
    closeConsole();
  }
  
  
  /** Process an invokation of this module with the given URI */
  public void invoke(String uri) {
    String command = uri;
    String parameters = "";
    int theIdx = uri.indexOf('?');
    if(theIdx>=0) {
      command = uri.substring(0, theIdx);
      parameters = uri.substring(theIdx+1);
    }
    else {
      theIdx = uri.indexOf(':');
      if(theIdx>=0) {
        command = uri.substring(0, theIdx);
      }
    }

    if(command.equals("showconsole")) {
      showConsole();
    }    
  }

  public String getName() {
    return "VenmoImporter";
  }

  private synchronized void showConsole() {
    if(venmoImporterWindow==null) {
      venmoImporterWindow = new VenmoImporterWindow(this);
      venmoImporterWindow.setVisible(true);
    }
    else {
      venmoImporterWindow.setVisible(true);
      venmoImporterWindow.toFront();
      venmoImporterWindow.requestFocus();
    }
  }
  
  FeatureModuleContext getUnprotectedContext() {
    return getContext();
  }

  synchronized void closeConsole() {
    if(venmoImporterWindow!=null) {
      venmoImporterWindow.goAway();
      venmoImporterWindow = null;
      System.gc();
    }
  }
}


