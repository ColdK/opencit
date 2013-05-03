/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.agent.citrix;


import com.intel.mountwilson.ta.data.hostinfo.HostInfo;
import com.intel.mtwilson.agent.HostAgent;
import com.intel.mtwilson.crypto.CryptographyException;
import com.intel.mtwilson.crypto.X509Util;
import com.intel.mtwilson.datatypes.TxtHostRecord;
import com.intel.mtwilson.model.Aik;
import com.intel.mtwilson.model.Nonce;
import com.intel.mtwilson.model.Pcr;
import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.PcrManifest;
import com.intel.mtwilson.model.Sha1Digest;
import com.intel.mtwilson.model.TpmQuote;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.XenAPIException;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.codec.binary.Base64;
import org.apache.xmlrpc.XmlRpcException;


/**
 *
 * @author stdalex
 */
public class CitrixHostAgent implements HostAgent{
    private CitrixClient client;
    
    
    public CitrixHostAgent(CitrixClient client) {
     this.client = client;
    }
    
    
    @Override
    public boolean isTpmEnabled() {
        return true;
    }

    @Override
    public boolean isEkAvailable() {
        return true;
    }

    @Override
    public boolean isAikAvailable() {
        return true;
    }

    @Override
    public boolean isAikCaAvailable() {
        return false;
    }

    @Override
    public boolean isDaaAvailable() {
        return true;
    }

    @Override
    public X509Certificate getAikCertificate() {
        throw new UnsupportedOperationException("Not supported");
        /*
        X509Certificate cert = null;
        try {
            String crt = client.getAIKCertificate().replaceAll("\n", "").replaceAll("\r","");
            System.out.println("decodeding pem == \n"+crt);
             cert = X509Util.decodePemCertificate(crt);  
             
        }  catch(Exception ex){
            System.out.println("getAikCert caught: " + ex.getMessage());
            
        }
        return cert;
        */
    }

    @Override
    public X509Certificate getAikCaCertificate() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public String getHostInformation() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getVendorHostReport() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public TpmQuote getTpmQuote(Aik aik, Nonce nonce, Set<PcrIndex> pcr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public TxtHostRecord getHostDetails() throws IOException {
        //throw new UnsupportedOperationException("Not supported yet.");
        TxtHostRecord record = new TxtHostRecord();
        HostInfo info = null;
        try {
            info = this.client.getHostInfo();
        } catch(Exception ex){
            System.out.println("getHostDetails getHostInfo caught: " + ex.getMessage());
       }
        
        record.HostName = client.hostIpAddress;
        record.IPAddress = client.hostIpAddress;
        record.Port = client.port;
        record.BIOS_Name = info.getBiosOem();
        record.BIOS_Version = info.getBiosVersion();
        record.BIOS_Oem = info.getBiosOem();
        record.VMM_Name = info.getVmmName();
        record.VMM_Version = info.getVmmVersion();
        record.VMM_OSName = info.getOsName();
        record.VMM_OSVersion = info.getOsVersion();
        record.AddOn_Connection_String = client.connectionString;
        
        try {
            record.AIK_Certificate = client.getAIKCertificate();
        }  catch(Exception ex){
            System.out.println("getHostDetails getAikCert caught: " + ex.getMessage());
       }
        
        return record;
    }
    
    /*
     * Format should look something like this
     * <?xml version='1.0' encoding='UTF-8'?>
     * <Host_Attestation_Report Host_Name="10.1.70.126" vCenterVersion="5.0" HostVersion="5.0">
     *      <PCRInfo ComponentName="0" DigestValue="1d670f2ae1dde52109b33a1f14c03e079ade7fea"/>
     *      <PCRInfo ComponentName="17" DigestValue="ca21b877fa54dff86ed5170bf4dd6536cfe47e4d"/>
     *      <PCRInfo ComponentName="18" DigestValue="8cbd66606433c8b860de392efb30d76990a3b1ed"/>
     * </Host_Attestation_Report>
     */
    @Override
    public String getHostAttestationReport(String pcrList) throws IOException {
       String attestationReport = "";
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        XMLStreamWriter xtw;
        StringWriter sw = new StringWriter();
        try {
            xtw = xof.createXMLStreamWriter(sw);
        
            xtw.writeStartDocument();
            xtw.writeStartElement("Host_Attestation_Report");
            xtw.writeAttribute("Host_Name",this.client.hostIpAddress);
            xtw.writeAttribute("vCenterVersion", "5.0");
            xtw.writeAttribute("HostVersion", "5.0");
            //xtw.writeAttribute("TXT_Support", tpmSupport.toString());
        
            HashMap<String, Pcr> pcrMap = client.getQuoteInformationForHost(pcrList);
            
            Iterator it = pcrMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pairs = (Map.Entry)it.next();
                xtw.writeStartElement("PCRInfo");
                Pcr pcr = (Pcr)pairs.getValue();
                xtw.writeAttribute("ComponentName",pcr.getIndex().toString());
                xtw.writeAttribute("DigestValue", pcr.getValue().toString());
                xtw.writeEndElement();
               
                it.remove(); // avoids a ConcurrentModificationException
            }
            xtw.writeEndElement();
            xtw.writeEndDocument();
            xtw.flush();
            xtw.close(); 
            attestationReport = sw.toString();
        
        } catch (XMLStreamException ex) {
            Logger.getLogger(CitrixHostAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        System.err.println("stdalex-error getHostAttestationReport report:" + attestationReport);
        return attestationReport;
    }

    @Override
    public boolean isIntelTxtSupported() {
        return true;
    }

    @Override
    public boolean isIntelTxtEnabled() {
        return true;
    }

    @Override
    public boolean isTpmPresent() {
        return true;
    }

    @Override
    public X509Certificate getEkCertificate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /*  BEFORE
     * -----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvNEz3+TStAAndHTc1qwT
NGvZYyB7DD1FshQf+mbQUGJ9HccOXNn5oHB7fWQjODjlDrYyCs7FclSMTLxA3lHX
98QWeWHL2O8t8qrJQQEUWZITmr/ddiNJOOvMeYF0K5if4m84vjgx/pTwwAVyU0Yo
MMXPnRozO8o7zSyRsH4jixALDugrsveEjLQI/cIEFvNjqlhyfumHyJKywNkMH1oJ
4e/f89FkpeDV694lsLs1jguuLLnvroXYJ5Uzeos+F0Pj1zFDUvhWrjVwxsUfAxS8
5uFGTUm6EEl9XiKwi+mgg8ODrY5dh3uE2yKB2T1Qj8BfK55zB8cYbORSsm6/f6Bi
BwIDAQAB
-----END PUBLIC KEY-----
*   AFTER
* -----BEGIN PUBLIC KEY-----MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvNEz3+TStAAndHTc1qwTNGvZYyB7DD1FshQf+mbQUGJ9HccOXNn5oHB7fWQjODjlDrYyCs7FclSMTLxA3lHX98QWeWHL2O8t8qrJQQEUWZITmr/ddiNJOOvMeYF0K5if4m84vjgx/pTwwAVyU0YoMMXPnRozO8o7zSyRsH4jixALDugrsveEjLQI/cIEFvNjqlhyfumHyJKywNkMH1oJ4e/f89FkpeDV694lsLs1jguuLLnvroXYJ5Uzeos+F0Pj1zFDUvhWrjVwxsUfAxS85uFGTUm6EEl9XiKwi+mgg8ODrY5dh3uE2yKB2T1Qj8BfK55zB8cYbORSsm6/f6BiBwIDAQAB-----END PUBLIC KEY-----
     */
    @Override
    public PublicKey getAik() {
        PublicKey pk = null;
         try {
            String crt  = client.getAIKCertificate();
            System.out.println(" crt == " + crt);
            pk = X509Util.decodePemPublicKey(crt);
            //client.getAIKCertificate().replace(X509Util.BEGIN_PUBLIC_KEY, "").replace(X509Util.END_PUBLIC_KEY, "").replaceAll("\n","").replaceAll("\r","");  
        }  catch(Exception ex){
            System.out.println("getAik caught: " + ex.getMessage()); 
            
        }  
        return pk;
    }

    @Override
    public PcrManifest getPcrManifest() throws IOException {
        PcrManifest pcrManifest = new PcrManifest();
        String pcrList = "0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24";
         HashMap<String, Pcr> pcrMap = client.getQuoteInformationForHost(pcrList);
            
         Iterator it = pcrMap.entrySet().iterator();
         while (it.hasNext()) {
                Map.Entry pairs = (Map.Entry)it.next();
                Pcr pcr = (Pcr)pairs.getValue();
                pcrManifest.setPcr(new Pcr(PcrIndex.valueOf(Integer.parseInt(pcr.getIndex().toString())), new Sha1Digest(pcr.getValue().toString())));
                it.remove(); // avoids a ConcurrentModificationException
        }
       return pcrManifest;
    }

    @Override
    public Map<String, String> getHostAttributes() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}