/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.tag.model;

import com.intel.dcsg.cpg.io.UUID;
import com.intel.mtwilson.repository.Locator;
import javax.ws.rs.PathParam;

/**
 *
 * @author ssbangal
 */
public class TpmPasswordLocator implements Locator<TpmPassword> {

    @PathParam("id")
    public UUID id;

    @Override
    public void copyTo(TpmPassword item) {
        if (id != null) {
            item.setId(id);
        }
    }
    
}
