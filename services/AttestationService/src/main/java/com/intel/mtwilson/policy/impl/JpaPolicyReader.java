/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.policy.impl;

import com.intel.mountwilson.as.common.ASException;
import com.intel.mtwilson.as.controller.TblHostSpecificManifestJpaController;
import com.intel.mtwilson.as.controller.TblLocationPcrJpaController;
import com.intel.mtwilson.as.controller.TblMleJpaController;
import com.intel.mtwilson.as.controller.TblPcrManifestJpaController;
import com.intel.mtwilson.as.controller.TblModuleManifestJpaController;
import com.intel.mtwilson.as.data.TblHostSpecificManifest;
import com.intel.mtwilson.as.data.TblHosts;
import com.intel.mtwilson.as.data.TblMle;
import com.intel.mtwilson.as.data.TblModuleManifest;
import com.intel.mtwilson.as.data.TblPcrManifest;
import com.intel.mtwilson.datatypes.ErrorCode;
import com.intel.mtwilson.model.Bios;
import com.intel.mtwilson.model.Measurement;
import com.intel.mtwilson.model.Pcr;
import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.Sha1Digest;
import com.intel.mtwilson.model.Vmm;
import com.intel.mtwilson.policy.Rule;
import com.intel.mtwilson.policy.rule.PcrEventLogIncludes;
import com.intel.mtwilson.policy.rule.PcrEventLogIntegrity;
import com.intel.mtwilson.policy.rule.PcrMatchesConstant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class should be used to instantiate Policy and Rule objects out of our existing
 * database schema.
 * 
 * This class provides utility methods for the vendor-specific rule loading
 * classes. For example, creating a PcrMatchesConstant rule out of a mw_pcr_manifest record
 * is the same code regardless of which vendor is using it for what purpose.
 * 
 * @author jbuhacoff
 */
public class JpaPolicyReader {
    private Logger log = LoggerFactory.getLogger(getClass());
    
    private EntityManagerFactory entityManagerFactory;
    private TblMleJpaController mleJpaController;
    private TblPcrManifestJpaController pcrManifestJpaController;
    private TblHostSpecificManifestJpaController pcrHostSpecificManifestJpaController;
    private TblModuleManifestJpaController moduleManifestJpaController;
    private TblLocationPcrJpaController locationPcrJpaController;

    public JpaPolicyReader(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        mleJpaController = new TblMleJpaController(entityManagerFactory);
        pcrManifestJpaController = new TblPcrManifestJpaController(entityManagerFactory);
        moduleManifestJpaController = new TblModuleManifestJpaController(entityManagerFactory);
        locationPcrJpaController = new TblLocationPcrJpaController(entityManagerFactory);
        pcrHostSpecificManifestJpaController = new TblHostSpecificManifestJpaController(entityManagerFactory);
    }
    
    
    public List<PcrIndex> loadBiosPcrIndexList(TblHosts tblHosts) {
        ArrayList<PcrIndex> pcrs = new ArrayList<PcrIndex>();
        TblMle biosMle = mleJpaController.findMleById(tblHosts.getBiosMleId().getId()); // XXX don't know why we are doing another database lookup, the tblHosts.getBiosMleId() is not an Id it's the full record and it has the same information we are looking up here
        String biosPcrList = biosMle.getRequiredManifestList();
        if (biosPcrList.isEmpty()) {
            throw new ASException(
                    ErrorCode.AS_MISSING_MLE_REQD_MANIFEST_LIST,
                    tblHosts.getBiosMleId().getName(), tblHosts.getBiosMleId().getVersion());
        }
        String[] biosPcrs = biosPcrList.split(",");
        for(String str : biosPcrs) {
            pcrs.add(new PcrIndex(Integer.valueOf(str)));
        }
        return pcrs;
    }    
    
    public List<PcrIndex> loadVmmPcrIndexList(TblHosts tblHosts) {
        ArrayList<PcrIndex> pcrs = new ArrayList<PcrIndex>();
        // Get the Vmm MLE without accessing cache
        TblMle vmmMle = mleJpaController.findMleById(tblHosts.getVmmMleId().getId()); // XXX don't know why we are doing another database lookup, the tblHosts.getVmmMleId() is not an Id it's the full record and it has the same information we are looking up here
        String vmmPcrList = vmmMle.getRequiredManifestList();
        if (vmmPcrList == null || vmmPcrList.isEmpty()) {
            throw new ASException(
                    ErrorCode.AS_MISSING_MLE_REQD_MANIFEST_LIST,
                    tblHosts.getVmmMleId().getName(), tblHosts.getVmmMleId().getVersion());
        }
        String[] vmmPcrs = vmmPcrList.split(",");
        for(String str : vmmPcrs) {
            pcrs.add(new PcrIndex(Integer.valueOf(str)));
        }
        return pcrs;
    }        

    public Rule createPcrMatchesConstantRuleFromTblPcrManifest(TblPcrManifest pcrInfo, String... markers) {
        try {
            PcrIndex pcrIndex = new PcrIndex(Integer.valueOf(pcrInfo.getName()));
            Sha1Digest pcrValue = new Sha1Digest(pcrInfo.getValue());
            log.debug("Creating PcrMatchesConstantRule from PCR {} value {}", pcrIndex.toString(), pcrValue.toString());
            PcrMatchesConstant rule = new PcrMatchesConstant(new Pcr(pcrIndex, pcrValue));
            rule.setMarkers(markers);
            return rule;
        }
        catch(IllegalArgumentException e) {
            log.warn("Invalid PCR {} value {}, skipped", pcrInfo.getName(), pcrInfo.getValue());
            return null;
        }
    }
    
    public Set<Rule> createPcrMatchesConstantRulesFromTblPcrManifest(Collection<TblPcrManifest> pcrInfoList, String... markers) {
        HashSet<Rule> list = new HashSet<Rule>();
        for(TblPcrManifest pcrInfo : pcrInfoList) {
            Rule rule = createPcrMatchesConstantRuleFromTblPcrManifest(pcrInfo, markers); // returns null if the rule cannot be created, such as the digest value is blank
            if( rule != null ) {
                list.add(rule);
            }
        }
        return list;
    }

    /*
    public Set<Rule> loadPcrMatchesConstantRules(TblHosts tblHosts, TblMle mle) {
        Collection<TblPcrManifest> pcrInfoList = mle.getTblPcrManifestCollection();
        return createPcrMatchesConstantRulesFromTblPcrManifest(pcrInfoList, TrustMarker.BIOS.name());
    }*/
    
    public Set<Rule> loadPcrMatchesConstantRulesForBios(Bios bios, TblHosts tblHosts) {
        TblMle biosMle = mleJpaController.findBiosMle(bios.getName(), bios.getVersion(), bios.getOem());
        log.debug("WhitelistUtil found BIOS MLE: {}", biosMle.getName());
        Collection<TblPcrManifest> pcrInfoList = biosMle.getTblPcrManifestCollection();
        return createPcrMatchesConstantRulesFromTblPcrManifest(pcrInfoList, TrustMarker.BIOS.name());
    }

    public Set<Rule> loadPcrMatchesConstantRulesForVmm(Vmm vmm, TblHosts tblHosts) {
        TblMle vmmMle = mleJpaController.findVmmMle(vmm.getName(), vmm.getVersion(), vmm.getOsName(), vmm.getOsVersion());
        log.debug("WhitelistUtil found VMM MLE: {}", vmmMle.getName());
        Collection<TblPcrManifest> pcrInfoList = vmmMle.getTblPcrManifestCollection();
        return createPcrMatchesConstantRulesFromTblPcrManifest(pcrInfoList, TrustMarker.VMM.name());
    }
    
    public Set<Rule> loadPcrMatchesConstantRulesForLocation(String location, TblHosts tblHosts) {
//        HashSet<Rule> rules = new HashSet<Rule>();
//        TblLocationPcr locationPcr = locationPcrJpaController.findTblLocationPcrByLocationName(location) // XXX need to create this method
//        log.debug("WhitelistUtil found Location PCR: {}", locationPcr.getName());
//        Sha1Digest pcrValue = new Sha1Digest(locationPcr.getValue());
//        log.debug("Creating PcrMatchesConstantRule from PCR 22 value {}", pcrValue.toString());
//        PcrMatchesConstant rule = new PcrMatchesConstant(new Pcr(PcrIndex.PCR22, pcrValue));
//        rule.setMarkers(markers);
//        rules.add(rule);
//        return rules;
        throw new UnsupportedOperationException("TODO: add support for checking pcr 22");
    }

    public Measurement createMeasurementFromTblModuleManifest(TblModuleManifest moduleInfo, TblHosts host) {
        HashMap<String,String> info = new HashMap<String,String>();
        // info.put("EventType", manifest.getEventType()); // XXX  we don't have an "EventType" field defined in the "mw_module_manifest" table ... should add it 
        info.put("EventName", moduleInfo.getEventID().getName());
        info.put("ComponentName", moduleInfo.getComponentName());

        if( moduleInfo.getUseHostSpecificDigestValue() != null && moduleInfo.getUseHostSpecificDigestValue().booleanValue() ) {
            TblHostSpecificManifest hostSpecificModule = pcrHostSpecificManifestJpaController.findByModuleAndHostID(host.getId(), moduleInfo.getId()); // returns null if not found;  XXX TODO this API only allows ONE host specific module per host... that only happens to be true today for vmware but soon we will need a more normalized database schema for this
            if( hostSpecificModule == null ) {
                log.error(String.format("Missing host-specific module %s for host %s", moduleInfo.getComponentName(), host.getName()));
                Measurement m = new Measurement(Sha1Digest.ZERO, "Missing host-specific module: "+moduleInfo.getComponentName(), info);
                return m;
            }
            else {
                Measurement m = new Measurement(new Sha1Digest(hostSpecificModule.getDigestValue()), moduleInfo.getComponentName(), info);
                return m;
            }
        }
        else {
            // XXX making assumptions about the nature of the module... due to what we store in the database when adding whitelist and host.  
            // XXX the only way to fix this is to change the schema so it can accomodate all the custom info we need w/o needing to know WHAT it is from here.
            info.put("PackageName", moduleInfo.getPackageName());
            info.put("PackageVersion", moduleInfo.getPackageVersion());
            info.put("PackageVendor", moduleInfo.getPackageVendor());
            Measurement m = new Measurement(new Sha1Digest(moduleInfo.getDigestValue()), moduleInfo.getComponentName(), info); 
            return m;
        }
    }
    
    // creates a rule for checking that ONE module is included in a pcr event log
    public Rule createPcrEventLogIncludesRuleFromTblModuleManifest(TblModuleManifest moduleInfo, TblHosts host, String... markers) {
        PcrIndex pcrIndex = new PcrIndex(Integer.valueOf(moduleInfo.getExtendedToPCR()));
        log.debug("... MODULE for PCR {}", pcrIndex.toString());
        Measurement m = createMeasurementFromTblModuleManifest(moduleInfo, host);
        PcrEventLogIncludes rule = new PcrEventLogIncludes(pcrIndex, m);
        rule.setMarkers(markers);
        return rule;
    }
    
    
    // creates a rule for checking that ONE OR MORE modules are included in a pcr event log
    public Set<Rule> createPcrEventLogIncludesRuleFromTblModuleManifest(Collection<TblModuleManifest> pcrModuleInfoList, TblHosts host, String... markers) {
        HashSet<Rule> list = new HashSet<Rule>();
    // XXX unfortunately because of our database design, we don't know in advance which  pcr's these modules belong to.
        // so we need to collect the set of measurements for each pcr, and then for every pcr that has modules/events, we need to add the rules (second section below)
        HashMap<PcrIndex,Set<Measurement>> measurements = new HashMap<PcrIndex,Set<Measurement>>();
        for(TblModuleManifest moduleInfo : pcrModuleInfoList) {
            PcrIndex pcrIndex = PcrIndex.valueOf(Integer.valueOf(moduleInfo.getExtendedToPCR()));
            
            if( !measurements.containsKey(pcrIndex) ) {
                measurements.put(pcrIndex, new HashSet<Measurement>());
            }
            
            Measurement m = createMeasurementFromTblModuleManifest(moduleInfo, host);
            measurements.get(pcrIndex).add(m);
        }
        // for every pcr that has events, we add a "pcr event log includes..." rule for those events, and also an integrity rule.
        for(PcrIndex pcrIndex : measurements.keySet()) {
            // XXX TODO for first phase of this implementation in mt wilson 1.2,  we only support pcr 19 ...  in next phase, need to rewrite the way data is stored and also the UI so we can remove this "pcr 19" check and just do it generally for any pcr with modules
            if( pcrIndex.toInteger() == 19 ) {
                // event log rule
                log.debug("Adding PcrEventLogIncludes rule for PCR {} with {} events", pcrIndex.toString(), measurements.get(pcrIndex).size());
                PcrEventLogIncludes eventLogIncludesRule = new PcrEventLogIncludes(pcrIndex, measurements.get(pcrIndex));
                eventLogIncludesRule.setMarkers(markers);
                list.add(eventLogIncludesRule);
                // integrity rule
                log.debug("Adding PcrEventLogIntegrity rule for PCR {}", pcrIndex.toString());
                PcrEventLogIntegrity integrityRule = new PcrEventLogIntegrity(pcrIndex);
                integrityRule.setMarkers(markers);
                list.add(integrityRule); // if we're going to look for things in the host's event log, it needs to have integrity            
            }
        }
        
        return list;
    }
    
    public Set<Rule> loadPcrEventLogIncludesRuleForBios(Bios bios, TblHosts tblHosts) {
        TblMle biosMle = mleJpaController.findBiosMle(bios.getName(), bios.getVersion(), bios.getOem());
        Collection<TblModuleManifest> pcrModuleInfoList = biosMle.getTblModuleManifestCollection();  // XXX event logs shouldn't be associated directly to a bios, they should be associated to a specific pcr digest ...
        return createPcrEventLogIncludesRuleFromTblModuleManifest(pcrModuleInfoList, tblHosts, TrustMarker.BIOS.name());
    }
    
    public Set<Rule> loadPcrEventLogIncludesRuleForVmm(Vmm vmm, TblHosts tblHosts) {
        TblMle vmmMle = mleJpaController.findVmmMle(vmm.getName(), vmm.getVersion(), vmm.getOsName(), vmm.getOsVersion());
        Collection<TblModuleManifest> pcrModuleInfoList = vmmMle.getTblModuleManifestCollection();       // XXX event logs shouldn't be associated directly to a vmm, they should be associated to a specific pcr digest ...  
        return createPcrEventLogIncludesRuleFromTblModuleManifest(pcrModuleInfoList, tblHosts, TrustMarker.VMM.name());
    }
    
    
}
