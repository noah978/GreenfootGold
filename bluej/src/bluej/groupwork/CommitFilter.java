/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2016  Michael Kolling and John Rosenberg 
 
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
package bluej.groupwork;

import bluej.pkgmgr.BlueJPackageFile;

/**
 * Class to filter CVS StatusInformation to calculate those classes that will 
 * be changed when we next commit. It should include files that are locally 
 * modified, remotely modified, locally deleted and remotely removed.
 *
 * @author bquig
 */
public class CommitFilter
{
    /**
     * Filter to identify which files in a repository will be altered at 
     * the next commit.
     * @param statusInfo the statusInfo to be filtered
     * @param local if the commit is between working copy and local tree or 
     *              between local tree and remote tree.
     * @return 
     */
    public boolean accept(TeamStatusInfo statusInfo, boolean local)
    {
        int stat;
        if (local){
            stat = statusInfo.getStatus();
        } else {
            stat = statusInfo.getRemoteStatus();
        }
        
        if (stat == TeamStatusInfo.STATUS_DELETED) {
            return true;
        }
        if (stat == TeamStatusInfo.STATUS_NEEDSADD) {
            return true;
        }
        if (stat == TeamStatusInfo.STATUS_NEEDSCOMMIT) {
            return true;
        }
        if (!local && stat == TeamStatusInfo.STATUS_NEEDSCHECKOUT){
            return true;
        }
        if (!local && stat == TeamStatusInfo.STATUS_NEEDS_PUSH) {
            return true;
        }
        
        
        if (BlueJPackageFile.isPackageFileName(statusInfo.getFile().getName())) {
            boolean conflict = (stat == TeamStatusInfo.STATUS_CONFLICT_ADD);
            conflict |= (stat == TeamStatusInfo.STATUS_NEEDSMERGE);
            conflict |= (stat == TeamStatusInfo.STATUS_CONFLICT_LDRM);
            conflict |= (stat == TeamStatusInfo.STATUS_UNRESOLVED);
            if (conflict) {
                return true;
            } 
        }

        return false;
    }
}
