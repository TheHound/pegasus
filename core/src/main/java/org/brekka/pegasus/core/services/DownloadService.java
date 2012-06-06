/**
 * 
 */
package org.brekka.pegasus.core.services;

import java.io.InputStream;

import org.brekka.pegasus.core.model.AllocationFile;

/**
 * @author Andrew Taylor
 *
 */
public interface DownloadService {

    InputStream download(AllocationFile file, ProgressCallback progressCallback);
    
    interface ProgressCallback {
        void update(long current, long total);
    }
}
