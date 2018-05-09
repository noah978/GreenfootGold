/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2012,2014,2016  Michael Kolling and John Rosenberg 
 
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
package bluej.groupwork.actions;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import threadchecker.OnThread;
import threadchecker.Tag;
import bluej.Config;
import bluej.collect.DataCollector;
import bluej.groupwork.Repository;
import bluej.groupwork.TeamSettingsController;
import bluej.groupwork.TeamUtils;
import bluej.groupwork.TeamworkCommand;
import bluej.groupwork.TeamworkCommandResult;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.Utility;

/**
 * An action to perform an import into a repository, i.e. to share a project.
 * 
 * @author Kasper
 */
public class ImportAction extends TeamAction 
{
    public ImportAction(PkgMgrFrame pmf)
    {
        super(pmf, "team.import");
    }
    
    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(PkgMgrFrame pmf)
    {
        Project project = pmf.getProject();
        
        if (project == null) {
            return;
        }
        
        doImport(pmf, project);
    }

    @OnThread(Tag.Swing)
    private void doImport(final PkgMgrFrame pmf, final Project project)
    {
        // The team settings controller is not initially associated with the
        // project, so you can still modify the repository location
        final TeamSettingsController tsc = new TeamSettingsController(project.getProjectDir());
        final Repository repository = tsc.getRepository(true);
        
        if (repository == null) {
            // user cancelled
            return;
        }

        try {
            project.saveAll();
            project.saveAllEditors();
        }
        catch(IOException ioe) {
            String msg = DialogManager.getMessage("team-error-saving-project");
            if (msg != null) {
                String finalMsg = Utility.mergeStrings(msg, ioe.getLocalizedMessage());
                Platform.runLater(() -> DialogManager.showErrorTextFX(pmf.getFXWindow(), finalMsg));
                return;
            }
        }
        setStatus(Config.getString("team.sharing"));
        startProgressBar(); 
        
        Thread thread = new Thread() {
            
            TeamworkCommandResult result = null;
            
            public void run()
            {
                // boolean resetStatus = true;
                TeamworkCommand command = repository.shareProject();
                result = command.getResult();

                if (! result.isError()) {
                    final AtomicReference<Set<File>> files = new AtomicReference<>();
                    final AtomicBoolean isDVCS = new AtomicBoolean();
                    try
                    {
                        EventQueue.invokeAndWait(() -> {
                            project.setTeamSettingsController(tsc);
                            Set<File> projFiles = tsc.getProjectFiles(true);
                            // Make copy, to ensure thread safety:
                            files.set(new HashSet<File>(projFiles));
                            isDVCS.set(tsc.isDVCS());
                        });
                    }
                    catch (InvocationTargetException | InterruptedException e)
                    {
                        Debug.reportError(e);
                    }
                    Set<File> newFiles = new LinkedHashSet<File>(files.get());
                    Set<File> binFiles = TeamUtils.extractBinaryFilesFromSet(newFiles);
                    command = repository.commitAll(newFiles, binFiles, Collections.<File>emptySet(),
                            files.get(), Config.getString("team.import.initialMessage"));
                    result = command.getResult();
                    //In DVCS, we need an aditional command: pushChanges.
                    if (isDVCS.get()){
                        command = repository.pushChanges();
                        result = command.getResult();
                    }
                }

                Platform.runLater(() -> {
                    handleServerResponse(result);
                    EventQueue.invokeLater(() -> {
                        stopProgressBar();
                        if (!result.isError())
                        {
                            setStatus(Config.getString("team.shared"));
                            DataCollector.teamShareProject(project, repository);
                        } else
                        {
                            clearStatus();
                        }
                    });
                });
            }
        };
        thread.start();
    }
}