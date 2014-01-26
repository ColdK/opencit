package com.intel.mtwilson.as.rest;

import com.intel.mountwilson.as.common.ASConfig;
import com.intel.mountwilson.as.common.ASException;
import com.intel.mtwilson.as.business.BulkHostMgmtBO;
import com.intel.mtwilson.as.business.trust.BulkHostTrustBO;
import com.intel.mtwilson.as.ASComponentFactory;
import com.intel.mtwilson.datatypes.BulkHostTrustResponse;
import com.intel.mtwilson.datatypes.ErrorCode;
import com.intel.mtwilson.datatypes.HostConfigResponse;
import com.intel.mtwilson.datatypes.HostConfigResponseList;
import com.intel.mtwilson.datatypes.HostResponse;
import com.intel.mtwilson.datatypes.TxtHost;
import com.intel.mtwilson.datatypes.TxtHostRecord;
import com.intel.mtwilson.datatypes.TxtHostRecordList;
import com.intel.mtwilson.security.annotations.RolesAllowed;
import com.intel.dcsg.cpg.validation.ValidationUtil;
import com.intel.mtwilson.launcher.ws.ext.V1;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Web Service
 *
 * Example query: http://localhost:8080/AttestationService/resources/hosts
 * /trust?hosts=10.1.71.104&force_verify=false
 *
 * @author dmagadix
 */
@V1
@Stateless
@Path("/AttestationService/resources/hosts/bulk")
public class BulkHostTrust {

        private Logger log = LoggerFactory.getLogger(getClass());

        /**
         * REST Web Service Example: GET /hosts/trust?hosts=host_name_1
         * ,host_name_2,host_name_3&force_verify=true
         *
         * @param hosts
         * @param forceVerify
         * @return
         */
        @GET
        @Produces({MediaType.APPLICATION_XML})
        @Path("/trust/saml")
        @RolesAllowed({"Attestation", "Report"})
        public String getTrustSaml(
                @QueryParam("hosts") String hosts,
                @QueryParam("force_verify") @DefaultValue("false") Boolean forceVerify,
                //                        @QueryParam("threads") @DefaultValue("5") Integer threads, // bug #503 max threads now global and configured in properties file
                @QueryParam("timeout") @DefaultValue("600") Integer timeout) {
            
                ValidationUtil.validate(hosts);
                Integer myTimeOut = timeout;
                // if no timeout value is passed to function, check config for default, 
                // if not in config, go with default value
                // Modified the default time out back to 600 seconds as we are seeing time out issues. 30 seconds short for VMware hosts.
                if (timeout == 600) {
                        log.info("getTrustSaml called with default timeout, checking config");
                        myTimeOut = ASConfig.getConfiguration().getInt("com.intel.mountwilson.as.attestation.hostTimeout", 600);
                        log.debug("getTrustSaml config returned back" + myTimeOut);
                }
                if (hosts == null || hosts.length() == 0) {

                        throw new ASException(com.intel.mtwilson.datatypes.ErrorCode.AS_MISSING_INPUT,
                                "hosts");
                }

                Set<String> hostSet = new HashSet<String>();
                // bug #783  make sure that we only pass to the next layer hostnames that are likely to be valid 
                for(String host : Arrays.asList(hosts.split(","))) {
                    log.debug("Host: '{}'", host);
                    if( !(host.trim().isEmpty() || host.trim() == null) ) {
                        hostSet.add(host.trim());
                    }
                }
                BulkHostTrustBO bulkHostTrustBO = new BulkHostTrustBO(/*threads, */myTimeOut);
                return bulkHostTrustBO.getBulkTrustSaml(hostSet, forceVerify);


        }

        /**
         * REST Web Service Example: GET /hosts/trust?hosts=host_name_1
         * ,host_name_2,host_name_3&force_verify=true
         *
         * @param hosts
         * @param forceVerify
         * @return
         */
        @Path("/trust")
        @GET
        @Produces({MediaType.APPLICATION_JSON})
        @RolesAllowed({"Attestation", "Report"})
        public BulkHostTrustResponse getTrustJson(
                @QueryParam("hosts") String hosts,
                @QueryParam("force_verify") @DefaultValue("false") Boolean forceVerify,
                //                        @QueryParam("threads") @DefaultValue("5") Integer threads, // bug #503 max threads now global and configured in properties file
                @QueryParam("timeout") @DefaultValue("600") Integer timeout) {

                ValidationUtil.validate(hosts);
                if (hosts == null || hosts.length() == 0) {
                        throw new ASException(com.intel.mtwilson.datatypes.ErrorCode.AS_MISSING_INPUT,
                                "hosts");
                }

                Set<String> hostSet = new HashSet<String>();
                hostSet.addAll(Arrays.asList(hosts.split(",")));
                BulkHostTrustBO bulkHostTrustBO = new BulkHostTrustBO(/*threads,*/timeout);
                return bulkHostTrustBO.getBulkTrustJson(hostSet, forceVerify);
        }

        /**
         * This function support bulk host registration.
         *
         * @param hostRecords
         * @return
         */
        @RolesAllowed({"Attestation"})
        @POST
        @Consumes({MediaType.APPLICATION_JSON})
        @Produces({MediaType.APPLICATION_JSON})
        public HostConfigResponseList addHosts(TxtHostRecordList hostRecords) {
            
            ValidationUtil.validate(hostRecords);
            
            TxtHostRecordList newHostRecords = new TxtHostRecordList();
            for(TxtHostRecord host : hostRecords.getHostRecords().toArray(new TxtHostRecord[0]) ){
            if(host.HostName.isEmpty() || host.HostName == null)
                throw new ASException(com.intel.mtwilson.datatypes.ErrorCode.AS_MISSING_INPUT,
                                "host");
            else
                newHostRecords.getHostRecords().add(host);
            }
            BulkHostMgmtBO bulkHostMgmtBO = new BulkHostMgmtBO();
            HostConfigResponseList results =  bulkHostMgmtBO.addHosts(newHostRecords);
            for (HostConfigResponse hr : results.getHostRecords()) {
                log.debug("Bulk Add Hosts: " + hr.getHostName() + ":" + hr.getStatus() + ":" + hr.getErrorMessage());
            }
            return results;            
        }

        /**
         * This function supports bulk update of the hosts specified.
         *
         * @param hostRecords
         * @return
         */
        @RolesAllowed({"Attestation"})
        @PUT
        @Consumes({MediaType.APPLICATION_JSON})
        @Produces({MediaType.APPLICATION_JSON})
        public HostConfigResponseList updateHosts(TxtHostRecordList hostRecords) {
            
            ValidationUtil.validate(hostRecords);

              TxtHostRecordList newHostRecords = new TxtHostRecordList();
            for(TxtHostRecord host : hostRecords.getHostRecords().toArray(new TxtHostRecord[0]) ){
            if(host.HostName.isEmpty() || host.HostName == null)
                throw new ASException(com.intel.mtwilson.datatypes.ErrorCode.AS_MISSING_INPUT,"host");
            else
                newHostRecords.getHostRecords().add(host);
            }
            BulkHostMgmtBO bulkHostMgmtBO = new BulkHostMgmtBO();
            HostConfigResponseList results = bulkHostMgmtBO.updateHosts(newHostRecords);
            for (HostConfigResponse hr : results.getHostRecords()) {
                log.debug("Bulk Update Hosts: " + hr.getHostName() + ":" + hr.getStatus() + ":" + hr.getErrorMessage());
            }
            return results;            
        }
}