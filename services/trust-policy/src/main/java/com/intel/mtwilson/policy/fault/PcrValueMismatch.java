/*
 * Copyright (C) 2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.policy.fault;

import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.Sha1Digest;
import com.intel.mtwilson.policy.Fault;

/**
 *
 * @author jbuhacoff
 */
public class PcrValueMismatch extends Fault {
    private PcrIndex pcrIndex;
    private Sha1Digest expectedValue;
    private Sha1Digest actualValue;
    public PcrValueMismatch(PcrIndex pcrIndex, Sha1Digest expectedValue, Sha1Digest actualValue) {
        super("Host PCR %d with value %s does not match expected value %s", pcrIndex.toInteger(), actualValue.toString(), expectedValue.toString());
        this.pcrIndex = pcrIndex;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }
    
    public PcrIndex getPcrIndex() { return pcrIndex; }
    public Sha1Digest getExpectedValue() { return expectedValue; }
    public Sha1Digest getActualValue() { return actualValue; }
}