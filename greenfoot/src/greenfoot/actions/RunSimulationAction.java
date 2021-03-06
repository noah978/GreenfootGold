/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2010,2011,2016  Poul Henriksen and Michael Kolling
 
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
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package greenfoot.actions;

import bluej.Config;
import greenfoot.core.Simulation;
import greenfoot.event.SimulationEvent;
import greenfoot.event.SimulationListener;
import greenfoot.event.SimulationUIListener;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;

import bluej.utility.Debug;

/**
 * An action to run the simulation.
 * 
 * @author Poul Henriksen
 */
public class RunSimulationAction extends AbstractAction
    implements SimulationListener
{
    private static final String iconFile = "run.png";
    private static RunSimulationAction instance = new RunSimulationAction();

    private Simulation simulation;
    protected boolean stateOnDebugResume;
    private SimulationUIListener listener;
    private Runnable actionListener;

    /**
     * Singleton factory method for action.
     */
    public static RunSimulationAction getInstance()
    {
        return instance;
    }

    private RunSimulationAction()
    {
        super(Config.getString("run.simulation"), new ImageIcon(RunSimulationAction.class.getClassLoader().getResource(iconFile)));
    }

    /**
     * Attach this action to a simulation object that it controls.
     */
    public void attachSimulation(Simulation simulation)
    {
        this.simulation = simulation;
        simulation.addSimulationListener(this);
    }
    
    /**
     * Attach a listener to be notified when the action fires.
     */
    public void attachListener(SimulationUIListener listener)
    {
        this.listener = listener;
    }
    
    public void actionPerformed(ActionEvent e)
    {
        if(simulation == null) {
            Debug.reportError("attempt to run a simulation while none exists.");
            return;
        }
        
        if (listener != null) {
            listener.simulationActive();
        }
        if (actionListener != null)
        {
            actionListener.run();
        }
        simulation.setPaused(false);
    }

    /**
     * Observing for the simulation state so we can dis/en-able us appropiately
     */
    public void simulationChanged(final SimulationEvent e)
    {
        int eventType = e.getType();
        if (eventType == SimulationEvent.STOPPED) {
            EventQueue.invokeLater(() -> {
                setEnabled(stateOnDebugResume = true);
            });
        }
        else if (eventType == SimulationEvent.STARTED) {
            EventQueue.invokeLater(() -> {
                setEnabled(stateOnDebugResume = false);
            });
        }
        else if (eventType == SimulationEvent.DISABLED) {
            EventQueue.invokeLater(() -> {
                setEnabled(stateOnDebugResume = false);
            });
        }
        else if (eventType == SimulationEvent.DEBUGGER_PAUSED) {
            EventQueue.invokeLater(() -> {
                stateOnDebugResume = isEnabled();
                setEnabled(false);                        
            });
        }
        else if (eventType == SimulationEvent.DEBUGGER_RESUMED) {
            EventQueue.invokeLater(() -> {
                setEnabled(stateOnDebugResume);
            });
        }
    }

    public void setActionListener(Runnable actionListener)
    {
        this.actionListener = actionListener;
    }
}
